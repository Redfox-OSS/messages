import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.register

/**
 * A `:feature:*` module: presentation only (State / Intent / Effect / ViewModel / Screen + its
 * nav entry). Depends on the shared core layers, never on another feature, and must follow the
 * Obsidian design rules (checked by [VerifyDesignRulesTask] before every build).
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("messages.android.library")
        pluginManager.apply("messages.android.compose")
        pluginManager.apply("messages.hilt")
        pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")

        dependencies {
            add("implementation", project(":core:ui"))
            add("implementation", project(":core:domain"))
            add("implementation", project(":core:model"))
            add("implementation", project(":core:common"))

            add("implementation", libs.lib("androidx-lifecycle-runtime-compose"))
            add("implementation", libs.lib("androidx-lifecycle-viewmodel-compose"))
            add("implementation", libs.lib("androidx-hilt-lifecycle-viewmodel-compose"))
            add("implementation", libs.lib("androidx-navigation3-runtime"))
            add("implementation", libs.lib("kotlinx-serialization-core"))
            add("implementation", libs.lib("kotlinx-collections-immutable"))
        }

        val verify = tasks.register<VerifyDesignRulesTask>("verifyDesignRules") {
            sources.from(fileTree("src/main") { include("**/*.kt") })
            report.set(layout.buildDirectory.file("reports/verifyDesignRules.txt"))
        }
        tasks.matching { it.name == "preBuild" || it.name == "check" }.configureEach { dependsOn(verify) }
    }
}
