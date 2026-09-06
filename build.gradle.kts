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
    // Only apply Spotless and desktop Java 21 targets to our custom modules, NEVER to the upstream android-reference
    if (project.name != "library") {
        plugins.withType<JavaPlugin> {
            configure<JavaPluginExtension> {
                sourceCompatibility = JavaVersion.VERSION_21
                targetCompatibility = JavaVersion.VERSION_21
            }
        }

        tasks.withType<JavaCompile>().configureEach {
            options.release.set(21)
        }

        tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
            compilerOptions {
                jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
            }
        }

        apply(plugin = "com.diffplug.spotless")
        
        configure<com.diffplug.gradle.spotless.SpotlessExtension> {
            kotlin {
                target("**/*.kt")
                targetExclude("build/**/*.kt", "**/bin/**/*.kt", "bin/**/*.kt")
                ktlint().editorConfigOverride(mapOf(
                    "ktlint_standard_filename" to "disabled",
                    "ktlint_standard_value-parameter-comment" to "disabled",
                    "max_line_length" to "off",
                    "ktlint_standard_max-line-length" to "disabled",
                    "ktlint_standard_property-naming" to "disabled",
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
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        compilerOptions {
            freeCompilerArgs.addAll(
                "-opt-in=kotlin.uuid.ExperimentalUuidApi",
                "-opt-in=kotlin.RequiresOptIn",
                "-opt-in=kotlin.ExperimentalMultiplatform"
            )
        }
    }
}
