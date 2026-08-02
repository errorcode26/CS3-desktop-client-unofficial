allprojects {
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri(File(rootProject.projectDir, "../packages/flutter_torrent_server/android/repo"))
        }
    }
}

/**
 * Centralized Project Settings
 * These versions are enforced across the app and all plugins.
 */
extra["projectCompileSdk"] = 36
extra["projectTargetSdk"] = 36
val projectJvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17

val newBuildDir: Directory =
    rootProject.layout.buildDirectory
        .dir("../../build")
        .get()
rootProject.layout.buildDirectory.value(newBuildDir)

subprojects {
    // Standardize subproject build directories
    val newSubprojectBuildDir: Directory = newBuildDir.dir(project.name)
    project.layout.buildDirectory.value(newSubprojectBuildDir)

    // Ensure :app is evaluated first for dependency resolution
    project.evaluationDependsOn(":app")

    /**
     * Unified SDK & Toolchain Enforcement
     * This logic forces all subprojects (including plugins) to use consistent SDKs.
     */
    val configureAction: (Project) -> Unit = { project ->
        if (project.hasProperty("android")) {
            project.extensions.configure<com.android.build.gradle.BaseExtension>("android") {
                // Force API 36 to satisfy modern AndroidX dependencies (e.g. fragment 1.7.1)
                compileSdkVersion(rootProject.extra["projectCompileSdk"] as Int)
                defaultConfig {
                    @Suppress("DEPRECATION")
                    targetSdkVersion(rootProject.extra["projectTargetSdk"] as Int)
                }
            }
        }

        // Standardize Kotlin JVM Target to 17
        project.tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile>().configureEach {
            compilerOptions {
                jvmTarget.set(projectJvmTarget)
            }
        }
    }

    /**
     * Resilient Configuration Hook
     * We use afterEvaluate to ensure we have the "last word" on versions, 
     * while checking state.executed to avoid "already evaluated" crashes.
     */
    if (state.executed) {
        configureAction(this)
    } else {
        afterEvaluate { configureAction(this) }
    }
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
