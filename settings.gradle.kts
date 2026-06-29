pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "GuideFlow"
include(":shared")
include(":backend")
// ponytail: backend-only builds (Docker/Cloud Run) skip Android modules so no Android SDK
// is needed in the image. Pass -PbackendOnly. Remove the gate if Android ever builds in CI here.
if (!providers.gradleProperty("backendOnly").isPresent) {
    include(":app")
    include(":guideflow-sdk")
    include(":portal")
}
 