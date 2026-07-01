# GuideFlow SDK Documentation

Interactive in-app tutorials for Jetpack Compose apps, authored in a portal and delivered over the air.

> Change a tutorial by editing it in the portal and pressing Publish. Live apps pick it up on their next launch, with no new app release.

---

## Contents

1. [What GuideFlow is](#1-what-guideflow-is)
2. [Core concepts](#2-core-concepts)
3. [How a tutorial reaches a user](#3-how-a-tutorial-reaches-a-user)
4. [Integrate the SDK, step by step](#4-integrate-the-sdk-step-by-step)
5. [Author a tutorial in the portal](#5-author-a-tutorial-in-the-portal)
6. [Step types](#6-step-types)
7. [Theming reference](#7-theming-reference)
8. [Behaviour you can rely on](#8-behaviour-you-can-rely-on)
9. [Public API reference](#9-public-api-reference)
10. [Troubleshooting and FAQ](#10-troubleshooting-and-faq)
11. [Privacy and security](#11-privacy-and-security)

---

## 1. What GuideFlow is

GuideFlow overlays guided tutorials (tooltips, spotlights, and modal dialogs) on top of an existing Jetpack Compose app. The tutorial content is not baked into the app. You author it in a companion portal, publish it through a hosted backend, and the SDK downloads it at runtime.

The system has five parts:

| Part | Role |
|---|---|
| `guideflow-sdk` | The Android library your app embeds to render tutorials. |
| Portal app | Where you sign in, author flows and steps, theme them, publish, and read analytics. |
| Backend | Ktor server on Cloud Run that stores content and serves one compiled config per project. |
| Firestore | Stores projects, flows, steps, published configs, and analytics. |
| `shared` | Serializable models shared by the SDK, backend, and portal. |

The backend is already hosted, so integrating the SDK needs no server work on your side.

---

## 2. Core concepts

Read these once; the rest of the docs assume them.

| Term | Meaning |
|---|---|
| **Project** | One app you are onboarding. It owns all of that app's tutorials and has a **project key** you paste into the SDK. |
| **Flow** | One tutorial: an ordered list of steps, identified by a **flow key** (for example `budget_tutorial`). |
| **Step** | One screen of a tutorial: a tooltip, spotlight, or modal, with a title and body. |
| **Anchor** | An on-screen element a step points at. You tag it in code with `Modifier.guideFlowAnchor("key")`; a step references it by the same key. |
| **Published config** | The single JSON document the backend compiles from a project's published flows. The SDK downloads exactly this, in one request. |
| **Project key** | Identifies the app to the backend (`gf_...`). It is not a secret (it ships in the app); the backend stores only its hash. |

Two different "users":

- **Portal user**: you, the developer. Signs in with Google, owns projects.
- **SDK end user**: a user of your app. Set with `setUser(...)`, only used to tag tutorial state and analytics, and hashed before it ever leaves the device. These are never mixed.

---

## 3. How a tutorial reaches a user

```text
You author in the portal
        -> Publish (backend validates + compiles one config, bumps a version)
        -> Cloud Firestore stores the published config
Your app calls refreshConfig()
        -> SDK downloads the one config (or gets 304 Not Modified) and caches it
        -> startFlow("...") renders the tour over your UI
        -> analytics events are queued and uploaded in the background
```

The key idea: the app always fetches **one** config document for its project key. Editing a tutorial never requires an app update, only a Publish.

---

## 4. Integrate the SDK, step by step

### The whole integration (4 lines)

A complete setup is four calls. `baseUrl` defaults to the hosted backend, and `initialize` refreshes config in the background, so nothing else is required.

```kotlin
GuideFlow.initialize(this, "gf_your_key")                     // 1. once at startup

setContent {
    GuideFlowHost {                                            // 2. once at the root
        Button(Modifier.guideFlowAnchor("budget_button")) {   // 3. tag anything a step points at
            Text("Budget")
        }
    }
}

GuideFlow.startFlow("budget_tutorial")                         // 4. run a published tutorial
```

Everything below explains these four, plus the optional extras (`setUser`, `setListener`, manual `refreshConfig`, `flush`). You can skip the optional ones entirely.

### Step 1: Add the library

In this repository the app depends on the module directly:

```kotlin
// app/build.gradle.kts
dependencies { implementation(project(":guideflow-sdk")) }
```

The SDK declares the `INTERNET` permission itself; it merges into your app. No other manifest change is needed for an HTTPS backend.

### Step 2: Initialize once at startup

Call this before you show any tutorial, typically in your `Activity.onCreate` or `Application`. The project key is all you need:

```kotlin
GuideFlow.initialize(context = this, projectKey = "gf_your_key")
```

Pass a `GuideFlowConfig` only if you want to change a default:

```kotlin
GuideFlow.initialize(
    context = this,
    projectKey = "gf_your_key",
    config = GuideFlowConfig(debugLogging = true),
)
```

`GuideFlowConfig` options:

| Field | Default | What it does |
|---|---|---|
| `baseUrl` | hosted backend | Backend URL. Already set to the hosted backend; override only to run your own. On a physical device do not use `localhost`; use your machine's LAN IP if you self-host. |
| `enableAnalytics` | `true` | Collect and upload flow/step events. Set `false` to turn analytics off entirely. |
| `enableOfflineCache` | `true` | Persist the last good config to DataStore so tutorials work offline and start instantly. |
| `debugLogging` | `false` | Log actionable messages to Logcat under the tag `GuideFlow` (wrong flow key prints the known keys; a missing anchor prints which anchor to add). Keep off in release. |

`initialize` loads any cached config immediately, then refreshes from the backend in the background (keeping the cache if the refresh fails), so you do not need to call `refreshConfig()` yourself for the normal case.

### Step 3: Identify the user (optional)

```kotlin
GuideFlow.setUser("user-123")   // or null for anonymous
```

The id is hashed with SHA-256 before it is stored or uploaded. Pass `null` for anonymous users. This is only for tutorial state and analytics; it has nothing to do with the portal's Google sign-in.

### Step 4: Host the overlay

Wrap your app content once, near the root, so overlays draw above everything and anchors register as screens compose.

```kotlin
setContent {
    GuideFlowHost {
        AppContent()
    }
}
```

Place it a single time. It works across your own navigation, so a flow can continue on a different screen.

### Step 5: Mark the elements a tutorial can point at

```kotlin
Button(
    modifier = Modifier.guideFlowAnchor("budget_button"),
    onClick = { },
) { Text("Budget") }
```

Rules for anchor keys:

- The string must match the step's **anchor key** set in the portal. This is the one contract between your code and the authored content.
- Use stable, descriptive keys (`budget_button`, `profile_tab`). They are not user-visible.
- Only the element that is actually on screen needs the anchor; a tooltip or spotlight for an element that is not currently shown falls back to a modal (see step 6 of the portal section and the behaviour section).
- If the target sits below the fold in a scrollable screen, GuideFlow scrolls it into view automatically when its step runs.

### Step 6: Start and stop flows

```kotlin
GuideFlow.startFlow("budget_tutorial")   // returns Result; also reported to the listener
GuideFlow.stopFlow()                     // end the current flow early
```

Trigger `startFlow` wherever it makes sense: after first login, from a help button, or when a feature is first opened. Only one flow runs at a time.

### Step 7: Listen to lifecycle and errors (optional)

```kotlin
GuideFlow.setListener(object : GuideFlowListener {
    override fun onFlowStarted(flowKey: String) {}
    override fun onStepChanged(flowKey: String, stepIndex: Int) {}
    override fun onFlowCompleted(flowKey: String) {}
    override fun onFlowSkipped(flowKey: String) {}
    override fun onAnchorMissing(flowKey: String, anchorKey: String) {}
    override fun onError(error: GuideFlowError) {}
})
```

Every callback has a default empty body, so override only what you need. Errors are also returned from `startFlow` / `refreshConfig` as a `Result`, and, with `debugLogging = true`, printed to Logcat. The SDK never throws at your app.

### Step 8: Analytics (automatic, with an optional manual flush)

When `enableAnalytics` is on, the SDK records flow and step events into a local Room queue and uploads them in the background with WorkManager, deleting only the events the server acknowledges. You usually do nothing. To push immediately:

```kotlin
val accepted: Result<Int> = GuideFlow.flush()
```

### Full minimal example

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Required. baseUrl defaults to the hosted backend; config is optional.
        GuideFlow.initialize(this, "gf_your_key", GuideFlowConfig(debugLogging = true))

        // Optional: associate a user, and listen to lifecycle/errors.
        GuideFlow.setUser("user-123")

        setContent {
            GuideFlowHost {
                Column {
                    Button(
                        modifier = Modifier.guideFlowAnchor("budget_button"),
                        onClick = {},
                    ) { Text("Budget") }

                    Button(onClick = { GuideFlow.startFlow("budget_tutorial") }) {
                        Text("Start tutorial")
                    }
                }
            }
        }
    }
}
```

---

## 5. Author a tutorial in the portal

1. **Sign in** with Google.
2. **Create a project**, then copy the `gf_...` key into your app's `initialize(...)`. It is shown once. Projects can also be deleted from the projects list.
3. **Create a flow** and give it a flow key such as `budget_tutorial`. From the flow list you can also **rename**, **duplicate**, or **delete** a flow. Duplicating opens a dialog to choose a new name and key, then copies the steps and both themes into a new draft (ideal for a translated or right-to-left variant).
4. **Add steps.** For each step choose the type, set the anchor key (for tooltip and spotlight), and write a title and body. A live preview shows the step exactly as it will render in the flow's theme, and you can toggle light or dark. For a tooltip or spotlight you can turn on **advance when the user taps the element**.
5. **Theme the flow** in the appearance editor: accent and button-text colour, card background, corner radius, dim, right-to-left, custom button labels, the step-counter format, and text size, each with a separate light and dark variant. There is also a **Show back button** toggle. The preview updates live.
6. **Publish.** Publishing validates the flow (at least one step, unique order, an anchor for every tooltip and spotlight), compiles all published flows into one config, and bumps the project's version. The SDK downloads it on the next `refreshConfig()` or launch.

7. **Read analytics** per flow: completion rate, started / completed / skipped / anchor-missing counts, and a per-step view chart labelled by step name.

---

## 6. Step types

```kotlin
enum class StepType { TOOLTIP, SPOTLIGHT, MODAL }
```

| Type | Looks like | Anchor |
|---|---|---|
| `TOOLTIP` | A small card next to the element, with an accent ring around it. No screen dim. | Required |
| `SPOTLIGHT` | Dims the screen and cuts a transparent hole over the element; the controls card floats next to it. | Required |
| `MODAL` | A centered dialog, not attached to anything. | Not needed |

**Advance on tap** (per step, tooltip or spotlight): the highlighted element stays interactive. One tap runs the element's own action (for example navigation) and advances the tour. That step shows no Next button. Everything else on screen is blocked so the user cannot wander off the tour. This is how a tour spans multiple screens: tapping the real button both navigates and moves the tour forward. If the target screen changes, hide the Back button for that flow, since Back moves the tour back but cannot navigate the app back.

**Missing anchor**: if a tooltip or spotlight step points at an anchor that is not on screen, GuideFlow shows a modal fallback and fires `onAnchorMissing`. It never crashes.

---

## 7. Theming reference

Each flow has two `FlowTheme` objects, `theme` (light) and `themeDark` (dark). The SDK applies the dark one when the device is in dark mode. Every field has a default, so older configs deserialize unchanged.

| Field | Default | Effect |
|---|---|---|
| `accentColor` | SDK blue | Colour of the Next/Done button (`#RRGGBB`). |
| `buttonTextColor` | white | Text/foreground on the accent button. |
| `backgroundColor` | follows device | Card surface; leave unset to follow light/dark. |
| `rtl` | `false` | Right-to-left layout for the overlay text. |
| `dimOpacity` | `0.6` | Darkness of the spotlight/modal scrim (0 to 1). |
| `cornerRadius` | `14` | Corner radius in dp for cards and the spotlight cutout. |
| `titleSize` | `16` | Title text size in sp. The font follows the host app. |
| `bodySize` | `14` | Body text size in sp. |
| `nextLabel` / `backLabel` / `skipLabel` / `doneLabel` | Next / Back / Skip / Done | Button labels (translate these for other languages). |
| `progressFormat` | `Step {current} of {total}` | Step counter text; `{current}` and `{total}` are substituted. |
| `showProgress` | `true` | Show the step counter. |
| `showSkip` | `true` | Show the Skip button (hidden on the last step). |
| `showBack` | `true` | Show the Back button (turn off for flows that change screens). |

The font is intentionally not themeable; overlay text uses the host app's own typography so tutorials feel native.

---

## 8. Behaviour you can rely on

- **One request for config.** The SDK fetches a single document per project key and sends the current version so the backend can answer `304 Not Modified` when nothing changed.
- **Offline.** With the cache on, the last good config is stored on device. A failed refresh keeps the previous config; a fresh install with no network simply shows nothing until it can fetch.
- **Analytics never lose events on a bad network.** Events sit in a Room queue (capped at 1000, oldest dropped) and upload in the background; only server-acknowledged events are deleted.
- **The SDK never crashes the host.** Invalid config is ignored, a missing anchor becomes a modal, an unknown step type is skipped, and every error is reported through `Result`, the listener, and (if enabled) Logcat.
- **One flow at a time.** Starting a flow while another is active fails cleanly and is reported.
- **Cross-screen flows.** A running flow survives your app's navigation, so a step can point at an element on a different screen (reached via advance-on-tap).

---

## 9. Public API reference

```kotlin
object GuideFlow {
    const val SDK_VERSION: String

    fun initialize(context: Context, projectKey: String, config: GuideFlowConfig = GuideFlowConfig())
    fun setUser(userId: String?)                   // hashed (SHA-256) before use
    fun setListener(listener: GuideFlowListener?)
    fun loadLocalFlows(flows: List<TutorialFlow>)  // local fallback, used per missing key
    fun availableFlows(): List<TutorialFlow>       // published flows plus local fallbacks
    suspend fun refreshConfig(): Result<Unit>      // fetch the latest published config
    fun startFlow(flowKey: String): Result<Unit>
    fun stopFlow(reason: StopReason = StopReason.MANUAL)
    suspend fun flush(): Result<Int>               // upload queued analytics now
}

@Composable fun GuideFlowHost(modifier: Modifier = Modifier, content: @Composable () -> Unit)
fun Modifier.guideFlowAnchor(key: String): Modifier

interface GuideFlowListener {
    fun onFlowStarted(flowKey: String) {}
    fun onStepChanged(flowKey: String, stepIndex: Int) {}
    fun onFlowCompleted(flowKey: String) {}
    fun onFlowSkipped(flowKey: String) {}
    fun onAnchorMissing(flowKey: String, anchorKey: String) {}
    fun onError(error: GuideFlowError) {}
}

sealed class GuideFlowError { NotInitialized; FlowNotFound(flowKey); AnchorMissing(anchorKey); NetworkError(message); InvalidConfig(message) }
enum class StopReason { MANUAL, COMPLETED, SKIPPED }
```

`loadLocalFlows` is handy for tests and offline demos: remote flows win per key, and any key remote does not define falls back to a local one.

---

## 10. Troubleshooting and FAQ

**Do I need to run a server?**
No. The backend is hosted on Cloud Run. Point `baseUrl` at it and you are done. You only run your own backend if you want to.

**How do I change a tutorial after release?**
Edit it in the portal and press Publish. Apps pick it up on the next `refreshConfig()` or launch. No app store update.

**My flow does not appear. What do I check?**
1. Is the flow **Published** (not Draft)? 2. Does the `flowKey` in `startFlow` match exactly? 3. Has `refreshConfig()` finished at least once? 4. Is the `projectKey` correct? Turn on `debugLogging`; a wrong key prints the list of known flow keys.

**A step shows a centered modal instead of pointing at my element.**
That is the missing-anchor fallback: the step's `anchorKey` did not match any `Modifier.guideFlowAnchor("...")` currently on screen. Check the spelling matches and that the element is composed on that screen. With `debugLogging` on, the log names the anchor to add.

**What happens with no network?**
With the cache enabled, the last downloaded config is used and tutorials still run. Analytics queue locally and upload when connectivity returns.

**Can I test without the backend?**
Yes. Call `loadLocalFlows(listOf(...))` with hardcoded `TutorialFlow` objects and `startFlow` them. Any key not present remotely uses the local one.

**How do I support dark mode / other languages / right-to-left?**
Dark mode is automatic via the flow's `themeDark`. For other languages, translate the step text and the button labels, and set the counter format; for right-to-left, turn on `rtl`. Duplicate a flow to make a translated variant quickly.

**How does a tour move between screens?**
Use advance-on-tap on the real navigation control. Tapping it performs your navigation and advances the tour; the next step points at an element on the new screen.

**How big is the footprint / what permissions?**
The SDK adds the `INTERNET` permission only. It uses Compose, Ktor client, DataStore, Room, and WorkManager, which most modern apps already pull in.

**Is analytics personal data?**
No location, contacts, camera, screenshots, or input text is collected. Only a hashed user id and technical metadata (event type, timestamps, app/SDK/OS version, device model).

---

## 11. Privacy and security

- SDK user ids are hashed (SHA-256) on device before storage or upload.
- Project keys are stored only as a hash on the backend; the raw key is shown once at creation.
- Portal requests are authenticated with a Firebase ID token, and every request checks that the caller owns the project.
- The SDK and portal never talk to Firestore directly; everything goes through the backend.
- No sensitive device data is collected (see the FAQ).
