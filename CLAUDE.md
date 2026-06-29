# CLAUDE.md

## Project Name

**GuideFlow SDK**

GuideFlow is a Kotlin-based Android SDK for displaying interactive in-app tutorials inside Jetpack Compose applications.

The SDK allows host applications to display:

- Tooltips
- Spotlight highlights
- Modal fallback messages
- Multi-step tutorial flows
- Next, Back, Skip, and Done actions

Tutorial content is created in a developer portal, stored through a Kotlin Ktor backend in Firestore, and downloaded by the Android SDK as a single published configuration object.

The project is a course assignment. The goal is to build a complete but realistic MVP, not an enterprise platform.

---

## Core Goal

Build a complete local development ecosystem containing:

1. A reusable Android SDK written in Kotlin and Jetpack Compose
2. A demo Android app that imports and demonstrates the SDK
3. A Kotlin Ktor backend
4. A Firestore database
5. A Kotlin Compose developer portal
6. Firebase Authentication with Google Sign-In for portal users
7. Basic tutorial analytics
8. Local caching and offline analytics synchronization

Deployment is not part of the assignment.

The complete system must run and be demonstrated locally from Android Studio and on a physical Android device.

---

## Final Technology Stack

### Language

- Kotlin everywhere

### Android SDK

- Kotlin
- Jetpack Compose
- Ktor Client
- Kotlinx Serialization
- DataStore
- Room
- WorkManager

### Demo Application

- Kotlin
- Jetpack Compose
- Physical Android device for testing

### Backend

- Kotlin
- Ktor Server
- Kotlinx Serialization
- Firebase Admin SDK / Firestore Java client

### Database

- Cloud Firestore
- Firebase Emulator Suite may be used during development

### Portal

- Android app (Jetpack Compose) — decision 2026-06-27 (was "Compose for Web")
- Ktor Client
- Firebase Authentication (Google Sign-In via Credential Manager)

### Authentication

- Firebase Authentication
- Google Sign-In
- Firebase ID token verification in Ktor

### Build

- Gradle Kotlin DSL

### SDK Distribution

- JitPack may be added at the end
- Publishing is not required for the MVP to function locally

---

## Important Scope Rules

Keep the MVP focused.

### Included

- Tooltip
- Spotlight
- Modal fallback
- Multi-step tutorials
- Tutorial creation in portal
- Tutorial publishing
- Remote config retrieval
- Local config cache
- Analytics event collection
- Offline analytics queue
- Background upload with WorkManager
- Firebase Authentication for portal users
- Project ownership
- Basic analytics summary

### Not Included

Do not implement these unless the MVP is fully complete:

- A/B testing
- Advanced targeting
- Complex scheduling
- Team roles
- Team invitations
- Asset uploads
- Screenshots
- AI-generated tutorials
- Drag-and-drop visual editor
- Crash reporting
- iOS support
- Production deployment
- CI/CD
- Custom domain
- Advanced charts
- Large-scale analytics infrastructure
- Automatic discovery of UI elements
- Advanced animation editor

---

## System Architecture

```text
Developer Portal
Kotlin Compose Web
        |
        | Firebase Authentication
        | HTTPS + JSON
        v
Ktor Backend
Kotlin
        |
        | Firebase Admin SDK
        v
Cloud Firestore
Projects, Flows, Steps, Configs, Events

Android Host App
Jetpack Compose
        |
        v
GuideFlow Android SDK
        |
        | HTTPS + JSON
        v
Ktor Backend
```

The Android SDK and portal must not access Firestore directly.

Correct architecture:

```text
Android SDK -> Ktor Backend -> Firestore
Portal -> Ktor Backend -> Firestore
```

---

## Repository Structure

Use one repository.

```text
guideflow/
├── guideflow-sdk/
│   └── Android library module
├── app/
│   └── Jetpack Compose Android demo app
│       (the `:app` module — keep this name; Android Studio's Run
│        configuration is bound to it, so do not rename it)
├── backend/
│   └── Ktor JVM server
├── portal/
│   └── Android app (Jetpack Compose) — the tutorial authoring tool
│       (implemented as Android, not Compose-for-Web; decision 2026-06-27)
├── shared/
│   └── Shared Kotlin models
├── docs/
│   ├── architecture.md
│   ├── api-reference.md
│   └── screenshots/
├── README.md
└── settings.gradle.kts
```

---

## Shared Module

The `shared` module should contain serializable DTOs used by the SDK, backend, and portal.

Recommended models:

```text
TutorialConfig
TutorialFlow
TutorialStep
StepType
FlowStatus
AnalyticsEvent
AnalyticsBatch
AnalyticsBatchResponse
ProjectDto
FlowDto
StepDto
AnalyticsSummary
ApiError
```

Example:

```kotlin
@Serializable
data class TutorialStep(
    val id: String,
    val order: Int,
    val type: StepType,
    val anchorKey: String?,
    val title: String,
    val body: String
)
```

Use Kotlinx Serialization for JSON.

---

## Android SDK Package Structure

```text
com.guideflow.sdk
├── api/
│   ├── GuideFlow.kt
│   ├── GuideFlowConfig.kt
│   ├── GuideFlowListener.kt
│   └── GuideFlowError.kt
├── compose/
│   ├── GuideFlowHost.kt
│   ├── GuideFlowAnchorModifier.kt
│   ├── TooltipOverlay.kt
│   ├── SpotlightOverlay.kt
│   └── ModalFallback.kt
├── config/
│   ├── ConfigClient.kt
│   ├── ConfigRepository.kt
│   └── ConfigStorage.kt
├── anchor/
│   ├── AnchorManager.kt
│   └── AnchorInfo.kt
├── flow/
│   ├── FlowCoordinator.kt
│   ├── ActiveFlowState.kt
│   └── FlowValidator.kt
├── analytics/
│   ├── AnalyticsManager.kt
│   ├── AnalyticsEventEntity.kt
│   ├── EventQueue.kt
│   └── AnalyticsUploadWorker.kt
├── storage/
│   ├── UserStateStorage.kt
│   └── EventDatabase.kt
└── network/
    ├── GuideFlowApi.kt
    └── NetworkModels.kt
```

---

## Public SDK API

The public API must remain small.

### Initialize

```kotlin
fun initialize(
    context: Context,
    projectKey: String,
    config: GuideFlowConfig = GuideFlowConfig()
)
```

Responsibilities:

- Initialize SDK modules
- Store project key
- Load cached config
- Refresh config from backend
- Schedule analytics sync

Configuration:

```kotlin
data class GuideFlowConfig(
    val baseUrl: String,
    val enableAnalytics: Boolean = true,
    val enableOfflineCache: Boolean = true,
    val debugLogging: Boolean = false
)
```

For a physical Android device, `baseUrl` must be the computer's LAN IP.

Example:

```kotlin
GuideFlow.initialize(
    context = applicationContext,
    projectKey = "gf_demo_123",
    config = GuideFlowConfig(
        baseUrl = "http://192.168.1.20:8080"
    )
)
```

Do not use `localhost` from the physical device.

### Set User

```kotlin
fun setUser(userId: String?)
```

Purpose:

- Associate tutorial state and analytics with a host-app user
- Allow anonymous use when null
- Hash the ID before sending it

The SDK end user is not the same as the portal Firebase user.

### Refresh Config

```kotlin
suspend fun refreshConfig(): Result<Unit>
```

Purpose:

- Fetch latest published config
- Validate response
- Cache valid config
- Keep previous cache on failure

### Start Flow

```kotlin
fun startFlow(flowKey: String): Result<Unit>
```

Possible failures:

- SDK not initialized
- Flow not found
- Flow empty
- Flow invalid
- Another flow active

### Stop Flow

```kotlin
fun stopFlow(reason: StopReason = StopReason.MANUAL)
```

### Listener

```kotlin
fun setListener(listener: GuideFlowListener?)
```

```kotlin
interface GuideFlowListener {
    fun onFlowStarted(flowKey: String) {}
    fun onStepChanged(flowKey: String, stepIndex: Int) {}
    fun onFlowCompleted(flowKey: String) {}
    fun onFlowSkipped(flowKey: String) {}
    fun onAnchorMissing(flowKey: String, anchorKey: String) {}
    fun onError(error: GuideFlowError) {}
}
```

### Flush Analytics

```kotlin
suspend fun flush(): Result<Int>
```

Return the number of accepted events.

Delete local events only after server acknowledgement.

### Compose Anchor Modifier

```kotlin
fun Modifier.guideFlowAnchor(
    key: String
): Modifier
```

Responsibilities:

- Register stable anchor key
- Observe screen coordinates
- Update bounds on layout changes
- Remove anchor when composable leaves composition

Example:

```kotlin
Button(
    modifier = Modifier.guideFlowAnchor("budget_button"),
    onClick = {}
) {
    Text("Budget Planner")
}
```

### GuideFlowHost

```kotlin
@Composable
fun GuideFlowHost(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
)
```

Responsibilities:

- Render host app content
- Render GuideFlow overlay above content
- Observe active flow state
- Route Next, Back, Skip, and Done events

Place it once near the app root.

---

## Internal SDK Modules

### ConfigClient

- Calls backend config endpoint
- Sends project key
- Parses JSON
- Handles network errors

### ConfigRepository

- Returns cached config
- Refreshes config
- Stores valid config
- Keeps previous config on failure

### AnchorManager

- Stores anchor keys and bounds
- Updates coordinates
- Removes anchors when disposed
- Resolves anchor for current step

```kotlin
data class AnchorInfo(
    val key: String,
    val bounds: Rect
)
```

### FlowCoordinator

- Stores active flow
- Stores current step index
- Handles Next
- Handles Back
- Handles Skip
- Handles Complete
- Prevents multiple flows
- Handles missing anchors

```kotlin
data class ActiveFlowState(
    val flow: TutorialFlow,
    val currentStepIndex: Int,
    val status: FlowStatus
)
```

### OverlayRenderer

- Reads active step
- Reads anchor bounds
- Selects visual component
- Draws overlay
- Sends actions to FlowCoordinator

### AnalyticsManager

- Creates analytics events
- Adds technical metadata
- Stores events locally
- Schedules WorkManager

### EventQueue

- Insert event
- Read pending events
- Mark uploaded
- Delete acknowledged
- Limit queue size

Use a maximum of 1,000 events.

When full, delete oldest events first.

### AnalyticsUploadWorker

- Runs only with network
- Reads pending events
- Sends batch
- Retries temporary failures
- Deletes accepted events

---

## Visual Components

### Tooltip

Contains:

- Title
- Body
- Next
- Back when applicable
- Skip
- Step progress

### Spotlight

Contains:

- Full-screen dim layer
- Transparent highlighted anchor area
- Text and controls

### Modal Fallback

Used when anchor is missing.

Also emit:

```text
ANCHOR_MISSING
```

---

## Step Types

```kotlin
enum class StepType {
    TOOLTIP,
    SPOTLIGHT,
    MODAL
}
```

Rules:

- Tooltip requires anchorKey
- Spotlight requires anchorKey
- Modal may omit anchorKey

---

## Tutorial Config Model

```json
{
  "projectId": "project_001",
  "configVersion": 4,
  "flows": [
    {
      "id": "flow_001",
      "flowKey": "budget_tutorial",
      "name": "Budget Tutorial",
      "status": "PUBLISHED",
      "steps": [
        {
          "id": "step_001",
          "order": 1,
          "type": "SPOTLIGHT",
          "anchorKey": "budget_button",
          "title": "Budget Planner",
          "body": "Use this section to manage your budget."
        }
      ]
    }
  ]
}
```

Rules:

- Return only published flows
- Sort steps by order
- Flow key unique per project
- Empty flow cannot be published
- Include configVersion
- Do not return analytics events in config

---

## Local Storage

### DataStore

Use for:

- Project key
- User ID hash
- Cached config JSON
- Config version
- Last successful sync time

### Room

Use for analytics queue.

```kotlin
@Entity(tableName = "guideflow_events")
data class AnalyticsEventEntity(
    @PrimaryKey val eventId: String,
    val flowId: String,
    val stepId: String?,
    val eventType: String,
    val payloadJson: String,
    val createdAt: Long,
    val uploadState: String
)
```

---

## Analytics Events

```kotlin
enum class EventType {
    FLOW_STARTED,
    STEP_SHOWN,
    STEP_COMPLETED,
    FLOW_SKIPPED,
    FLOW_COMPLETED,
    ANCHOR_MISSING
}
```

Event example:

```json
{
  "eventId": "event_uuid",
  "projectId": "project_001",
  "flowId": "flow_001",
  "stepId": "step_001",
  "userIdHash": "hashed_user",
  "sessionId": "session_uuid",
  "eventType": "STEP_SHOWN",
  "timestamp": "2026-06-26T11:00:00Z",
  "appVersion": "1.0.0",
  "sdkVersion": "1.0.0",
  "androidVersion": "16",
  "deviceModel": "Pixel"
}
```

Analytics flow:

```text
User action
-> SDK creates event
-> Event stored in Room
-> WorkManager scheduled
-> Batch uploaded
-> Server acknowledges events
-> Accepted events deleted locally
```

---

## Portal Authentication

Use Firebase Authentication with Google Sign-In.

Portal flow:

```text
User signs in with Google
-> Firebase Authentication returns user
-> Portal retrieves Firebase ID token
-> Portal sends Bearer token to Ktor
-> Ktor verifies token
-> Backend identifies Firebase UID
-> Backend returns only owned projects
```

Protected request:

```http
Authorization: Bearer <FIREBASE_ID_TOKEN>
```

---

## Portal User vs SDK User

### Portal User

- Developer or project owner
- Signs in with Google
- Identified by Firebase UID
- Creates projects and tutorials
- Views analytics

### SDK End User

- User of host Android app
- Set through `GuideFlow.setUser(...)`
- Does not sign in to GuideFlow
- Used only for tutorial state and analytics
- Must be hashed before upload

Never mix these identifiers.

---

## Firestore Data Model

### portalUsers

```text
portalUsers/{firebaseUid}
```

### projects

```text
projects/{projectId}
```

Fields:

```json
{
  "projectId": "project_001",
  "ownerUid": "firebase_uid",
  "name": "Finance Demo App",
  "projectKeyHash": "hashed_key",
  "configVersion": 1,
  "createdAt": "server timestamp"
}
```

### flows

```text
projects/{projectId}/flows/{flowId}
```

### steps

```text
projects/{projectId}/flows/{flowId}/steps/{stepId}
```

### publishedConfigs

```text
publishedConfigs/{projectId}
```

This is the single config payload returned to the SDK.

### events

```text
projects/{projectId}/events/{eventId}
```

### analyticsSummaries

```text
projects/{projectId}/analyticsSummaries/{flowId}
```

---

## Ktor Backend Responsibilities

- Verify Firebase portal tokens
- Identify SDK project by project key
- Check project ownership
- Create projects
- Generate project keys
- Create flows
- Create and update steps
- Validate flows
- Publish flows
- Build compiled config
- Return published config
- Receive analytics batches
- Store events
- Update analytics summaries
- Return analytics summary

---

## Project Key

The project key identifies the host application.

It is not a secret because it exists in the Android app.

Creation flow:

```text
Portal user creates project
-> Backend generates raw project key
-> Backend stores hash
-> Portal shows raw key
-> Developer copies key into SDK initialization
```

The project key is not the same as Firebase UID or SDK user ID.

---

## Backend API

### Projects

```text
POST /api/projects
GET /api/projects
GET /api/projects/{projectId}
```

### Flows

```text
POST /api/projects/{projectId}/flows
GET /api/projects/{projectId}/flows
GET /api/flows/{flowId}
PUT /api/flows/{flowId}
DELETE /api/flows/{flowId}
```

### Steps

```text
POST /api/flows/{flowId}/steps
PUT /api/steps/{stepId}
DELETE /api/steps/{stepId}
PUT /api/flows/{flowId}/steps/order
```

### Publishing

```text
POST /api/flows/{flowId}/publish
```

Validate:

- Flow has at least one step
- Step order unique
- Required fields present
- Tooltip and spotlight have anchorKey

### SDK Config

```text
GET /api/client/config
```

Header:

```http
X-GuideFlow-Project-Key: gf_demo_123
```

Optional query:

```text
?currentVersion=4
```

### Analytics Batch

```text
POST /api/client/events/batch
```

### Analytics Summary

```text
GET /api/flows/{flowId}/analytics
```

---

## Efficient Config Retrieval

The SDK must make one config request.

Do not request project, flows, and steps separately.

Use:

```text
GET /api/client/config
```

The backend:

1. Identifies project by project key
2. Reads one published config document
3. Compares config version
4. Returns one JSON payload

Benefits:

- One network call
- Fast startup
- Simple SDK
- Easy caching
- Better offline support
- Fewer Firestore reads

---

## Portal Screens

### Login

- Sign in with Google
- Logout

### Projects

- List projects
- Create project
- Copy project key

### Flow List

- Flow name
- Flow key
- Status
- Edit
- Publish

### Flow Editor

- Flow name
- Flow key
- Step list
- Add step
- Reorder
- Edit
- Delete
- Save
- Publish

### Step Editor

- Type
- Anchor key
- Title
- Body
- Order

### Analytics

- Started
- Completed
- Skipped
- Completion rate
- Anchor missing
- Step views

Keep the portal simple.

Do not implement a visual drag-and-drop editor.

---

## Demo Application

Use a small finance or task-management app.

Suggested anchors:

```text
budget_button
add_budget_button
profile_button
settings_button
```

Demo flow:

```text
Step 1: Spotlight on budget_button
Step 2: Tooltip on add_budget_button
Step 3: Tooltip on profile_button
```

The demo must prove:

- SDK import
- Initialization
- Anchor registration
- Config retrieval
- Tooltip
- Spotlight
- Modal fallback
- Multi-step navigation
- Offline config
- Analytics upload
- Portal publishing

---

## Error Handling

The SDK must never crash the host app.

```kotlin
sealed class GuideFlowError {
    data object NotInitialized : GuideFlowError()
    data class FlowNotFound(val flowKey: String) : GuideFlowError()
    data class AnchorMissing(val anchorKey: String) : GuideFlowError()
    data class NetworkError(val message: String) : GuideFlowError()
    data class InvalidConfig(val message: String) : GuideFlowError()
}
```

Rules:

- Invalid config is ignored safely
- Failed refresh uses previous cache
- Missing anchor uses modal fallback
- Failed upload retains events
- Unknown type becomes modal or is ignored
- Empty flow does not start
- SDK errors are reported through listener

---

## Privacy and Security

Do not collect:

- Location
- Contacts
- Camera
- Microphone
- IMEI
- Screenshots
- User text input

Requirements:

- Hash SDK user IDs
- Verify Firebase ID tokens
- Check project ownership
- Hash project keys in Firestore
- Validate all inputs
- Never expose Firestore directly to SDK
- Never expose database credentials
- Never allow portal user to access another user's project

---

## Physical Device Development

The demo app runs on a physical Android device.

The Ktor backend runs on the development computer.

Use the computer's LAN address:

```text
http://192.168.x.x:8080
```

The phone and computer must be on the same network.

Do not use `http://localhost:8080` from the phone.

---

## Development Order

### Phase 1: Foundation

1. Create repository
2. Create shared module
3. Create Android library module
4. Create demo app
5. Create Ktor backend
6. Configure Firestore
7. Configure Firebase Authentication

### Phase 2: Local SDK Engine

1. Implement `GuideFlowHost`
2. Implement `guideFlowAnchor`
3. Implement `AnchorManager`
4. Implement tutorial models
5. Implement `FlowCoordinator`
6. Implement Tooltip
7. Implement Spotlight
8. Implement Modal fallback
9. Test with hardcoded local config

Do not start with portal or analytics.

### Phase 3: Backend Config

1. Create project model
2. Create flow model
3. Create step model
4. Generate project key
5. Implement config endpoint
6. Implement published config
7. Connect SDK ConfigClient
8. Add DataStore cache

### Phase 4: Portal

1. Add Firebase Google Sign-In
2. Verify token in Ktor
3. Project list
4. Create project
5. Flow list
6. Flow editor
7. Step editor
8. Publish action

### Phase 5: Analytics

1. Create event model
2. Add Room database
3. Add event queue
4. Add batch endpoint
5. Add WorkManager
6. Add analytics summary
7. Add portal analytics page

### Phase 6: Documentation

1. README
2. Setup instructions
3. API reference
4. Architecture documentation
5. Demo video
6. JitPack only if time allows

---

## Testing

### SDK Unit Tests

- Start flow
- Next
- Back
- Skip
- Complete
- Invalid flow
- Config parsing
- Event creation

### Compose UI Tests

- Tooltip appears
- Spotlight appears
- Next changes step
- Skip closes flow
- Modal appears for missing anchor

### Backend Tests

- Firebase token verification
- Project ownership
- Project creation
- Flow creation
- Step validation
- Publish validation
- Config endpoint
- Analytics batch
- Analytics summary

### Integration Tests

- Portal creates flow
- Backend publishes flow
- SDK receives config
- SDK renders flow
- SDK sends analytics
- Portal displays analytics

---

## Definition of Done

The project is complete when:

- SDK imports into demo app
- SDK initializes with project key
- Compose elements register as anchors
- Portal user signs in with Google
- Portal user creates a project
- Portal user creates a flow and steps
- Portal publishes a tutorial
- Backend creates one published config document
- SDK downloads published config
- SDK caches config
- Tooltip renders
- Spotlight renders
- Modal fallback renders
- User can navigate and skip
- Analytics events are stored locally
- WorkManager sends analytics
- Backend stores events
- Portal shows basic analytics
- Entire system runs locally
- Demo works on physical Android device
- README explains how to start all components

---

## Rules for Claude Code

When modifying this repository:

1. Keep all application code in Kotlin.
2. Use Jetpack Compose for Android UI.
3. Use Kotlin Compose for portal UI.
4. Use Ktor Client and Ktor Server.
5. Use Kotlinx Serialization.
6. Keep the public SDK API minimal.
7. Do not let SDK or portal access Firestore directly.
8. Do not add out-of-scope enterprise features.
9. Do not add deployment configuration unless explicitly requested.
10. Do not add JavaScript or TypeScript.
11. Do not add React.
12. Do not add XML Android layouts.
13. Do not hardcode production URLs.
14. Do not expose secrets in source code.
15. Do not delete local events before server acknowledgement.
16. Do not allow SDK errors to crash the host app.
17. Prefer small, testable modules.
18. Add tests for core flow logic.
19. Update README when setup changes.
20. Ask before changing architecture or scope.

---

## First Milestone

The first milestone must be:

> A local Compose-only tutorial engine running inside the demo app using a hardcoded tutorial flow.

It must include:

- `GuideFlowHost`
- `Modifier.guideFlowAnchor`
- `AnchorManager`
- `FlowCoordinator`
- One tooltip
- One spotlight
- One modal fallback
- Next
- Skip
- Done

Only after this works should remote config, portal, Firestore, authentication, and analytics be added.
