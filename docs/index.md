# GuideFlow

Interactive in-app tutorials for Jetpack Compose apps: tooltips, spotlights, and modals, authored in a portal and delivered over the air. Change a tutorial by editing it in the portal and pressing Publish; live apps pick it up on their next launch, with no new app release.

## Documentation

- **[Full integration guide](documentation.md)**: concepts, a step-by-step setup, theming, behaviour, API reference, and a troubleshooting FAQ.
- **[Project README](https://github.com/RotemBar18/GuideFlow)**: architecture, database, endpoints, and diagrams.

## Install

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories { maven("https://jitpack.io") }
}

// app/build.gradle.kts
dependencies {
    implementation("com.github.RotemBar18.GuideFlow:guideflow-sdk:1.2.0")
}
```

## The whole integration

Add the JitPack dependency above, then declare your project key in the manifest (the SDK auto-initializes at startup — no `initialize()` call):

```xml
<!-- AndroidManifest.xml, inside <application> -->
<meta-data android:name="com.guideflow.PROJECT_KEY" android:value="gf_your_key" />
```

```kotlin
setContent {
    GuideFlowHost {                                                     // once, at the root
        Button(Modifier.guideFlowAnchor("budget")) { Text("Budget") }  // tag targets
    }
}

GuideFlow.startFlow("budget_tutorial")                                  // run a published tutorial
```

See the [full guide](documentation.md) for everything else.

---

© 2026 Rotem Bar. Licensed under MIT.
