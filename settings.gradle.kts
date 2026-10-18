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
        // Obsidian design system, published from github.com/Redfox-OSS/obsidian. Kept on one line so
        // F-Droid's recipe can delete it: F-Droid builds Obsidian from source instead (see below).
        maven("https://redfox-oss.github.io/obsidian/maven") { content { includeGroup("dev.redfox") } }
    }
}

// -Pobsidian.fromSource=true builds Obsidian from the external/obsidian submodule (pinned to the
// release in libs.versions.toml) instead of downloading it. F-Droid builds this way.
if (providers.gradleProperty("obsidian.fromSource").orNull == "true") {
    includeBuild("external/obsidian")
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
