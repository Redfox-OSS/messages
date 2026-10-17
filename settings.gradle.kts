pluginManagement {
    includeBuild("build-logic")
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
        // Obsidian design system, published from Alarmzy with `./gradlew :obsidian:publishToMavenLocal`.
        mavenLocal {
            content { includeGroup("dev.redfox") }
        }
    }
}

rootProject.name = "Messages"

include(":app")

include(":core:common")
include(":core:contacts")
include(":core:model")
include(":core:domain")
include(":core:data")
include(":core:database")
include(":core:datastore")
include(":core:telephony")
include(":core:mms")
include(":core:ui")

include(":feature:onboarding")
include(":feature:conversations")
include(":feature:thread")
include(":feature:starred")
include(":feature:scheduled")
include(":feature:settings")
include(":feature:search")
