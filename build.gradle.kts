plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.lint) apply false
    alias(libs.plugins.android.multiplatform.library) apply false
    alias(libs.plugins.buildkonfig) apply false // Universal build config
    alias(libs.plugins.dokka) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.spotless) apply false
}


subprojects {
    // Only apply Spotless to our custom modules, NEVER to the upstream android-reference
    if (project.name != "library") {
        apply(plugin = "com.diffplug.spotless")
        
        configure<com.diffplug.gradle.spotless.SpotlessExtension> {
            kotlin {
                target("**/*.kt")
                targetExclude("build/**/*.kt")
                ktlint().editorConfigOverride(mapOf(
                    "ktlint_standard_filename" to "disabled",
                    "ktlint_standard_value-parameter-comment" to "disabled",
                    "max_line_length" to "off",
                    "ktlint_standard_max-line-length" to "disabled",
                    "ktlint_standard_no-wildcard-imports" to "disabled",
                    "ktlint_standard_function-naming" to "disabled",
                    "ktlint_standard_value-argument-comment" to "disabled"
                ))
            }
            kotlinGradle {
                target("*.gradle.kts")
                ktlint()
            }
        }
    }
}

allprojects {
    // https://docs.gradle.org/current/userguide/upgrading_major_version_9.html#test_task_fails_when_no_tests_are_discovered
    tasks.withType<AbstractTestTask>().configureEach {
        failOnNoDiscoveredTests = false
    }
}
