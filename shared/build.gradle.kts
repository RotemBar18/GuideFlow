plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.maven.publish)
}

kotlin {
    jvmToolchain(11)
}

dependencies {
    // Exposed via `api` so the SDK, backend, and portal get the serialization
    // runtime transitively when (de)serializing the @Serializable models below.
    api(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
}

// Published to JitPack as a sibling module; the SDK's POM references it.
publishing {
    publications {
        create<MavenPublication>("release") {
            groupId = "com.github.RotemBar18.GuideFlow"
            artifactId = "shared"
            version = "1.1.0"
            from(components["java"])
        }
    }
}
