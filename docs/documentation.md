# GuideFlow SDK — Documentation

## Title

**GuideFlow SDK** — interactive in‑app tutorials for Jetpack Compose Android apps.

---

## Description

GuideFlow is an Android library that overlays guided tutorials — tooltips, spotlights, and modal dialogs — on top of an existing Compose app. Tutorial content is **not** hard‑coded in the app: developers author it in a companion **portal**, publish it through a **Ktor backend**, and the backend compiles it into a single configuration document stored in **Cloud Firestore**. Any app embedding the SDK downloads that config at runtime and renders the tour. Changing a tutorial therefore requires *no app release* — just a re‑publish in the portal.

The system has five parts: the **SDK** (`guideflow-sdk`), a **demo host app** (`app`), the **authoring portal** (`portal`), the **backend** (`backend`), and **shared DTOs** (`shared`). The backend is deployed on Google Cloud Run, so the portal and SDK work from any network.

---

## Use cases

- **User onboarding** — walk a first‑time user through the core screens (e.g. "create your first budget").
- **Feature discovery** — point out a newly shipped button or screen with a tooltip or spotlight.
- **Contextual help** — trigger a relevant flow from a "?" button on any screen via `startFlow("...")`.
- **Tutorial iteration without releases** — product/marketing edits and re‑publishes a flow in the portal; live apps pick it up on next launch.
- **Multiple apps, one console** — each app is a "project" with its own key; one portal account manages them all.

---

## Features

- Tooltip, Spotlight, and Modal step types.
- Multi‑step flows with Next / Back / Skip / Done.
- `Modifier.guideFlowAnchor("key")` to mark any composable as a target.
- Automatic **modal fallback** when an anchor is missing on screen (plus an `ANCHOR_MISSING` callback). The SDK never crashes the host app.
- One‑request remote config (`GET /api/client/config`) with `304 Not Modified` support via config version.
- Offline cache in DataStore; a failed refresh keeps the last good config.
- Authoring portal with Google Sign‑In, project/flow/step CRUD, publish‑time validation, and a live step preview.
- Security: Firebase ID‑token verification, per‑request project‑ownership checks, SHA‑256‑hashed project keys, hashed SDK user IDs.

---

## Implementation

### Architecture

```text
Portal (Android)  ──Google Sign-In──▶ Firebase Auth
      │  Bearer ID token (create/publish)
      ▼
Ktor Backend (Cloud Run) ──Admin SDK──▶ Cloud Firestore
      ▲  X-GuideFlow-Project-Key (GET /api/client/config)
      │
Host app + GuideFlow SDK
```

- **Authoring path:** the portal authenticates with Google, gets a Firebase ID token, and calls the backend with `Authorization: Bearer <token>`. The backend verifies the token (Firebase Admin), checks the caller owns the project, and writes to Firestore.
- **Publish:** validates the flow (≥1 step, unique step order, tooltip/spotlight steps have an anchor), compiles all published flows of the project into one `TutorialConfig`, bumps the project's `configVersion`, and stores it under `publishedConfigs/{projectId}`.
- **Delivery path:** the SDK calls `GET /api/client/config` with the project key (not a Firebase token). The backend looks up the project by the key's SHA‑256 hash and returns the compiled config (or `304` if `currentVersion` matches).

### SDK internals

- `AnchorManager` — Compose snapshot‑state map of `anchorKey → bounds`, fed by `guideFlowAnchor`.
- `FlowCoordinator` — pure‑Kotlin `StateFlow` engine holding the active flow + step index; handles Next/Back/Skip/Complete and blocks concurrent flows.
- `GuideFlowHost` + overlays — render the host content and, above it, the overlay for the current step; a missing anchor routes to `ModalFallback`.
- `ConfigClient` / `ConfigRepository` / `ConfigStorage` — fetch (Ktor), keep‑last‑good in memory, and persist to DataStore.

### Backend internals

- `GuideFlowStore` interface with two implementations: `FirestoreStore` (production) and `InMemoryStore` (local dev & tests). Selected at startup by whether Firebase credentials are present.
- `AuthProvider`: `FirebaseAuthProvider` (verifies ID tokens; on Cloud Run uses Application Default Credentials with an explicit project id) or `DevAuthProvider` (local, returns a fixed dev owner).
- `ProjectKeys` (key generation + hashing), `FlowValidator`, `ConfigCompiler`.

### Data model (Firestore)

Flat collections `projects`, `flows`, `steps`, `publishedConfigs` — see the [README ERD and DB section](../README.md#database-cloud-firestore).

### Tech stack

Kotlin, Jetpack Compose, Ktor (client + server), kotlinx.serialization, DataStore, Firebase Authentication, Firebase Admin SDK, Cloud Firestore, Google Cloud Run, Gradle Kotlin DSL.

---

## User initialization

### 1. Add the SDK

In this repo the host app depends on the library module directly:

```kotlin
// app/build.gradle.kts
dependencies { implementation(project(":guideflow-sdk")) }
```

The SDK declares the `INTERNET` permission itself (merged into the host app). Talking to an HTTPS backend needs no extra manifest config.

### 2. Initialize once at startup

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        GuideFlow.initialize(
            context = applicationContext,
            projectKey = "gf_your_key",   // created in the portal, shown once
            config = GuideFlowConfig(
                baseUrl = "https://guideflow-backend-794711970205.me-west1.run.app",
            ),
        )
        GuideFlow.setUser("user-123")      // optional; hashed before upload
        GuideFlow.setListener(object : GuideFlowListener {
            override fun onFlowCompleted(flowKey: String) { /* analytics */ }
            override fun onError(error: GuideFlowError) { /* log */ }
        })

        lifecycleScope.launch { GuideFlow.refreshConfig() }  // pull latest

        setContent { GuideFlowHost { AppContent() } }        // host once near root
    }
}
```

### 3. Mark the elements a tutorial can point at

```kotlin
Button(modifier = Modifier.guideFlowAnchor("budget_button"), onClick = { … }) {
    Text("Budget")
}
```

The string must match the step's `anchorKey` set in the portal.

### 4. Start a flow

```kotlin
GuideFlow.startFlow("budget_tutorial")   // flowKey from the portal
```

The overlay renders over `GuideFlowHost`; the user advances with Next/Back/Skip/Done. If `refreshConfig()` hasn't loaded a flow with that key, `startFlow` falls back to any flows supplied via `loadLocalFlows(...)` and otherwise reports `FlowNotFound` through the listener.

### Authoring a tutorial (portal)

1. Open the **portal** app and sign in with Google.
2. **Create a project** → copy the `gf_…` key into your app's `initialize(...)` (shown once).
3. **Add a flow** (give it a `flowKey` like `budget_tutorial`).
4. **Add steps** — pick type, set the anchor key (for tooltip/spotlight), title, and body; use the live preview.
5. **Publish** — the flow goes live; the SDK downloads it on the next `refreshConfig()` / app launch.
