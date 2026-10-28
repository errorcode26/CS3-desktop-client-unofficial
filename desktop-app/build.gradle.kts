plugins {
    kotlin("jvm")
    id("org.jetbrains.compose") version "1.11.1"
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.20"
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        freeCompilerArgs.add("-Xannotation-default-target=param-property")
    }
}

configurations.all {
    exclude(group = "org.slf4j", module = "slf4j-simple")
    // Override the library module's strict constraint — desktop-app is pure JVM
    // and needs a newer jackson version to handle Kotlin 2.x @Metadata in plugins.
    resolutionStrategy.force("com.fasterxml.jackson.module:jackson-module-kotlin:2.18.3")
    resolutionStrategy.force("com.fasterxml.jackson.core:jackson-databind:2.18.3")
    resolutionStrategy.force("com.fasterxml.jackson.core:jackson-core:2.18.3")
    resolutionStrategy.force("com.fasterxml.jackson.core:jackson-annotations:2.18.3")
}

dependencies {
    // CloudStream Library (KMP, JVM target)
    // Contains: MainAPI, extractors, metaproviders, WebViewResolver (JVM actual), etc.
    implementation(project(":library"))
    implementation(libs.kotlinx.serialization.json)

    // ASM Bytecode Scanner
    implementation("org.ow2.asm:asm:9.6")
    implementation("org.ow2.asm:asm-tree:9.6")

    // Android Stubs
    implementation(project(":android-stubs"))

    implementation(project(":plugin-runtime"))
    implementation(project(":player-abstraction"))
    implementation(project(":common"))

    // HTTP
    implementation(libs.nicehttp)
    implementation(libs.newpipeextractor)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:okhttp-dnsoverhttps:4.12.0")

    // JSON
    implementation("com.google.code.gson:gson:2.11.0") // Required for plugins using JsonParser.parseString (matches Android app)
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.18.3")
    implementation(kotlin("reflect")) // Required for Jackson to deserialize plugin Kotlin data classes
    implementation("org.json:json:20240303") // Required for plugins using org.json (natively included on Android)

    // Coroutines (swing provides Dispatchers.Main on desktop JVM)
    val coroutinesVersion = "1.10.2"
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:$coroutinesVersion")

    // Desktop counterpart of Android's WebView system (now native CDP).
    // Android's built-in AES-GCM crypto is not available on desktop JVM.
    implementation("org.bouncycastle:bcprov-jdk18on:1.77")
    implementation("org.conscrypt:conscrypt-openjdk-uber:2.5.2")

    // JNA for MPV
    implementation("net.java.dev.jna:jna:5.14.0")
    implementation("net.java.dev.jna:jna-platform:5.14.0")

    // Compose Desktop UI
    implementation(compose.desktop.currentOs)
    implementation(compose.material3) // material3 already includes core icons
    implementation(compose.materialIconsExtended)
    implementation(compose.ui)
    implementation(compose.foundation)
    implementation("dev.chrisbanes.haze:haze:1.3.1")

    // Decompose Navigation
    implementation(libs.decompose)
    implementation(libs.decompose.extensions.compose)

    // Image loading
    implementation("io.coil-kt.coil3:coil-compose:3.0.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.0.0")
    implementation("io.coil-kt.coil3:coil-svg:3.0.0")

    // Logging
    implementation(libs.slf4j.api)
    implementation(libs.logback.classic)

    testImplementation(kotlin("test"))

    // SQLDelight
    implementation(libs.sqldelight.sqlite.driver)
    implementation(libs.sqldelight.coroutines.extensions)
}

// Compose Desktop application configuration
compose.desktop {
    application {
        mainClass = "com.lagradost.cloudstream3.desktop.MainKt"
        jvmArgs +=
            listOf(
                "-Xms128m",
                "-Xmx1024m",
                "-XX:+UseG1GC",
                "-XX:MaxGCPauseMillis=50",
                "-XX:G1PeriodicGCInterval=15000",
                "-XX:CICompilerCount=2",
                "-Djava.security.manager=allow",
                "-Djava.net.preferIPv6Addresses=true",
                "-Djava.library.path=\$APPDIR/resources/jni",
                "-Djna.library.path=\$APPDIR/resources/mpv",
                "-Dcloudstream.version=${project.findProperty("APP_VERSION")}",
                "-Dfile.encoding=UTF-8",
            )
        buildTypes.release.proguard {
            isEnabled.set(false)
        }

        val rawAppVer = project.findProperty("APP_VERSION")?.toString()?.removePrefix("v")?.substringBefore('-')?.takeIf { it.isNotBlank() } ?: "0.1.8"
        val semverParts = rawAppVer.split('.').filter { it.isNotBlank() }
        val msiSemver = when (semverParts.size) {
            0 -> "0.1.0"
            1 -> "${semverParts[0]}.0.0"
            2 -> "${semverParts[0]}.${semverParts[1]}.0"
            else -> "${semverParts[0]}.${semverParts[1]}.${semverParts[2]}"
        }
        project.version = msiSemver

        nativeDistributions {
            targetFormats(org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi)
            packageName = "CloudStream-Desktop"
            // jpackage STRICTLY requires version to be numeric (e.g. 0.1.5). Strip any -beta or -pre-alpha suffixes.
            packageVersion = msiSemver
            description = "CloudStream Desktop Client"
            vendor = "CloudStream"
            copyright = "Copyright (C) CloudStream"
            licenseFile.set(project.file("../installer/LICENSE.rtf"))
            includeAllModules = false
            modules(
                "java.base",
                "java.desktop",
                "java.instrument",
                "java.logging",
                "java.management",
                "java.naming",
                "java.net.http",
                "java.prefs",
                "java.scripting",
                "java.sql",
                "java.xml",
                "jdk.dynalink",
                "jdk.unsupported", // Required by JNA & Coroutines Unsafe
                "jdk.crypto.ec", // Required for HTTPS
                "jdk.crypto.cryptoki",
                "jdk.crypto.mscapi", // Required on Windows for some HTTPS cert verifications
                "jdk.management",
                "jdk.charsets", // Required to decode some foreign websites
                "jdk.zipfs", // Required by dex2jar for JAR generation
                "java.compiler", // Required by Rhino JS compiler
                "jdk.compiler", // Required by Rhino JS compiler
                "jdk.localedata", // Required by Rhino JS Date functions
            )
            appResourcesRootDir.set(project.layout.projectDirectory.dir("appResources"))

            windows {
                iconFile.set(project.file("src/main/resources/app_icon.ico"))
                menuGroup = "CloudStream Desktop"
                upgradeUuid = "d7e9b04f-723a-4467-84df-fcf470c1ae02"
                shortcut = true // Creates a Desktop shortcut during install
                perUserInstall = false // Installs machine-wide in Program Files matching Inno Setup
                msiPackageVersion = msiSemver
            }
        }
    }
}

tasks.matching { it.name == "run" }.configureEach {
    val runTask = this as JavaExec
    runTask.jvmArgs(
        "-Xms256m",
        "-Xmx2048m",
        "-XX:+UseG1GC",
        "-XX:MaxGCPauseMillis=50",
        "-XX:CICompilerCount=2",
        "-Djna.library.path=${project.file("appResources/windows/mpv").absolutePath}",
        "-Djava.library.path=${project.file("appResources/windows/jni").absolutePath}",
        "-Dcloudstream.version=${project.findProperty("APP_VERSION")}",
    )
}

val generateInstallerVersion by tasks.registering {
    val versionFile = project.file("../installer/version.iss")
    outputs.file(versionFile)
    doLast {
        val appVer = project.findProperty("APP_VERSION")?.toString()?.takeIf { it.isNotBlank() } ?: "0.1.0-dev"
        val rawNumeric = appVer.substringBefore('-').filter { c -> c.isDigit() || c == '.' }
        val parts = rawNumeric.split('.').filter { it.isNotBlank() }
        val numericVer = (parts + listOf("0", "0", "0", "0")).take(4).joinToString(".")
        versionFile.writeText("#define AppVersion \"$appVer\"\n#define NumericVersion \"$numericVer\"\n")
    }
}

tasks.named("processResources") {
    dependsOn(generateInstallerVersion)
}

tasks.matching { it.name == "createDistributable" }.configureEach {
    doLast {
        val runtimeBin = project.file("build/compose/binaries/main/app/CloudStream-Desktop/runtime/bin")
        if (runtimeBin.exists()) {
            val javaHome = org.gradle.internal.jvm.Jvm.current().javaHome
            val srcJavaExe = File(javaHome, "bin/java.exe")
            val dstJavaExe = File(runtimeBin, "java.exe")
            if (srcJavaExe.exists() && !dstJavaExe.exists()) {
                srcJavaExe.copyTo(dstJavaExe, overwrite = true)
            }
        }
    }
}

tasks.withType<Test> {
    useJUnitPlatform {
        // Exclude integration tests that require native binaries (e.g. libmpv-2.dll)
        // Run them manually with: ./gradlew :desktop-app:test -Dtags=native
        excludeTags("native")
    }
}

val packagePortableZip by tasks.registering(Zip::class) {
    group = "distribution"
    description = "Packages the standalone application into a fully portable zip archive with local data storage."
    dependsOn("createDistributable")

    val appDistDir = project.file("build/compose/binaries/main/app/CloudStream-Desktop")
    val outputsDir = project.file("build/outputs")
    val appVer = project.findProperty("APP_VERSION")?.toString()?.takeIf { it.isNotBlank() } ?: "0.1.8"

    destinationDirectory.set(outputsDir)
    archiveBaseName.set("CloudStream-Portable")
    archiveVersion.set(appVer)
    archiveClassifier.set("windows-x64")

    from(appDistDir) {
        into("CloudStream-Portable")
    }

    val markerFile = project.layout.buildDirectory.file("tmp/portable.txt")
    doFirst {
        val f = markerFile.get().asFile
        f.parentFile.mkdirs()
        f.writeText(
            "# CloudStream Desktop Portable Configuration\n" +
            "# When this file is present, all user data, plugins, watch history, and caches\n" +
            "# are stored locally in 'CloudStreamData/' right next to the executable.\n"
        )
    }

    from(markerFile) {
        into("CloudStream-Portable")
    }
}
