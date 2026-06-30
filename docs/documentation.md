# GuideFlow SDK - Documentation

## Title

GuideFlow SDK: interactive in-app tutorials for Jetpack Compose Android apps.

## Description

GuideFlow is an Android library that overlays guided tutorials (tooltips, spotlights, and modal dialogs) on top of an existing Compose app. Tutorial content is not hard-coded in the app. Developers author it in a companion portal, publish it through a Ktor backend, and the backend compiles it into a single configuration document stored in Cloud Firestore. Any app that embeds the SDK downloads that config at runtime and renders the tour, so changing a tutorial does not require a new app release; the developer just re-publishes in the portal.

The system has five parts: the SDK (`guideflow-sdk`), a demo host app (`app`), the authoring portal (`portal`), the backend (`backend`), and shared DTOs (`shared`). The backend is deployed on Google Cloud Run, so the portal and SDK work from any network.

## Use cases

- User onboarding: walk a first-time user through the core screens, such as creating a first budget.
- Feature discovery: point out a new button or screen with a tooltip or spotlight.
- Contextual help: trigger a relevant flow from a help button on any screen with `startFlow("...")`.
- Tutorial iteration without releases: edit and re-publish a flow in the portal, and live apps pick it up on the next launch.
- Multiple apps, one console: each app is a project with its own key, and one portal account manages them all.

## Features

- Tooltip, spotlight, and modal step types.
- Multi-step flows with Next, Back, Skip, and Done. A flow keeps running across screen navigation, so a step can continue on the next page.
- `Modifier.guideFlowAnchor("key")` to mark any composable as a target.
- Advance-on-tap steps: the highlighted element stays interactive, so one tap runs the app's own action and advances the tour (no Next button on that step). The rest of the screen is blocked while a step is active.
- Automatic modal fallback when an anchor is missing on screen, plus an anchor-missing callback. The SDK does not crash the host app.
- Per-flow theming with separate light and dark designs (chosen by the device theme): accent and button-text colour, card background, corner radius, dim opacity, right-to-left layout, custom button labels, a customizable step-counter format, and text size. The font follows the host app's own theme.
- One-request remote config (`GET /api/client/config`) with `304 Not Modified` based on config version.
- Offline cache in DataStore. A failed refresh keeps the last good config.
- Analytics: flow and step events are queued locally (Room) and uploaded in the background (WorkManager); the backend aggregates per-flow summaries shown in the portal as a completion rate, metric tiles, and a per-step view chart.
- Authoring portal with Google Sign-In, project management (create and delete) and flow management (create, rename, duplicate, delete), a step editor with a live themed preview, an appearance editor for the per-flow theme, publish-time validation, and a per-flow analytics view.
- Security: Firebase ID-token verification, per-request project-ownership checks, SHA-256 hashed project keys, and hashed SDK user IDs.

## Implementation

### Architecture

The portal (Android) signs in with Google through Firebase Auth and sends a Firebase ID token to the backend as a Bearer token when creating or publishing tutorials. The backend (Ktor on Cloud Run) verifies the token with the Firebase Admin SDK, checks the caller owns the project, and reads or writes Cloud Firestore. The host app and its embedded SDK call the backend with a project key (not a Firebase token) to fetch the published config.

Authoring path: portal authenticates with Google, gets a Firebase ID token, and calls the backend with `Authorization: Bearer <token>`. The backend verifies the token, checks ownership, and writes to Firestore.

Publish: validates the flow (at least one step, unique step order, tooltip and spotlight steps have an anchor), compiles all published flows of the project into one TutorialConfig, bumps the project's `configVersion`, and stores it under `publishedConfigs/{projectId}`.

Delivery path: the SDK calls `GET /api/client/config` with the project key. The backend looks up the project by the key's SHA-256 hash and returns the compiled config, or `304` when `currentVersion` matches.

### SDK internals

- `AnchorManager`: Compose snapshot-state map of anchor key to bounds, populated by `guideFlowAnchor`.
- `FlowCoordinator`: pure-Kotlin StateFlow engine holding the active flow and step index; handles Next/Back/Skip/Complete and blocks concurrent flows.
- `GuideFlowHost` and overlays: render host content and, above it, the overlay for the current step; a missing anchor routes to `ModalFallback`.
- `ConfigClient`, `ConfigRepository`, `ConfigStorage`: fetch (Ktor), keep last good config in memory, and persist to DataStore.
- `AnalyticsManager`, `EventDatabase` (Room), `AnalyticsUploadWorker` (WorkManager): the coordinator emits events to the manager, which queues them in Room (capped at 1000) and schedules a worker that uploads batches and deletes only acknowledged events.

### Backend internals

- `GuideFlowStore` interface with two implementations: `FirestoreStore` (production) and `InMemoryStore` (local dev and tests). Selected at startup by whether Firebase credentials are present.
- `AuthProvider`: `FirebaseAuthProvider` (verifies ID tokens; on Cloud Run uses Application Default Credentials with an explicit project id) or `DevAuthProvider` (local, returns a fixed dev owner).
- `ProjectKeys` (key generation and hashing), `FlowValidator`, `ConfigCompiler`.

### Data model (Firestore)

Flat collections `projects`, `flows`, `steps`, `publishedConfigs`. See the README ERD and database section.

### Tech stack

Kotlin, Jetpack Compose, Ktor (client and server), kotlinx.serialization, DataStore, Firebase Authentication, Firebase Admin SDK, Cloud Firestore, Google Cloud Run, Gradle Kotlin DSL.

## User initialization

### 1. Add the SDK

In this repo the host app depends on the library module directly:

```kotlin
// app/build.gradle.kts
dependencies { implementation(project(":guideflow-sdk")) }
```

The SDK declares the `INTERNET` permission itself, which merges into the host app. Talking to an HTTPS backend needs no extra manifest config.

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
Button(modifier = Modifier.guideFlowAnchor("budget_button"), onClick = { }) {
    Text("Budget")
}
```

The string must match the step's `anchorKey` set in the portal.

### 4. Start a flow

```kotlin
GuideFlow.startFlow("budget_tutorial")   // flowKey from the portal
```

The overlay renders over `GuideFlowHost`, and the user advances with Next, Back, Skip, and Done. If `refreshConfig()` has not loaded a flow with that key, `startFlow` falls back to any flows supplied via `loadLocalFlows(...)`, and otherwise reports `FlowNotFound` through the listener.

### Authoring a tutorial (portal)

1. Open the portal app and sign in with Google.
2. Create a project, then copy the `gf_...` key into your app's `initialize(...)` call. It is shown once.
3. Add a flow and give it a flowKey such as `budget_tutorial`. From the flow list you can also rename, duplicate, or delete a flow; duplicating copies its steps and both themes into a new draft, which is handy for making a translated or right-to-left variant.
4. Add steps: pick the type, set the anchor key (for tooltip and spotlight), title, and body. A live preview shows the step exactly as it will render, including the flow's theme, and you can toggle light or dark. For a tooltip or spotlight you can turn on "advance when the user taps the element".
5. Open the appearance editor to set the per-flow theme: colours, corner radius, dim, right-to-left layout, button labels, step-counter format, font, and text size, each with a light and a dark variant.
6. Publish. Publishing validates the flow (at least one step, unique order, anchors present for tooltip and spotlight), then the flow goes live and the SDK downloads it on the next `refreshConfig()` or app launch.

### Theming and advance-on-tap (how it works end to end)

The per-flow theme lives on the flow as two `FlowTheme` objects (`theme` and `themeDark`), serialized to JSON in Firestore and returned inside the published config. The SDK picks the dark variant when the device is in dark mode and applies it to every overlay; right-to-left affects only the text layout, while overlay placement stays in absolute coordinates.

`advanceOnTap` is a per-step flag. When it is set, the overlay leaves the anchored element uncovered (blocking the rest of the screen with strips around it) so the element receives the real tap: its own `onClick` runs and the SDK advances the flow on the same gesture. If that step's anchor is missing, it falls back to a modal that still shows a Next button, so the user is never stuck.
