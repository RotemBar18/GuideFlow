plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
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
