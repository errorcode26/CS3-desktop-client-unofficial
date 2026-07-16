plugins {
    kotlin("jvm")
    id("org.jetbrains.compose") version "1.7.3"
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.20"
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

sourceSets {
    main {
        java.srcDirs("src/main/java")
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

dependencies {
    // CloudStream Library (KMP, JVM target)
    // Contains: MainAPI, extractors, metaproviders, WebViewResolver (JVM actual), etc.
    implementation(project(":library"))

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
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:okhttp-dnsoverhttps:4.12.0")

    // JSON
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.1")
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
    implementation("dev.chrisbanes.haze:haze:0.7.3")

    // Image loading
    implementation("io.coil-kt.coil3:coil-compose:3.0.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.0.0")

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
        jvmArgs += listOf("-Djava.security.manager=allow", "-Djava.net.preferIPv4Stack=true", "-Djava.library.path=build/libs;\$APPDIR/resources/jni", "-Dfile.encoding=UTF-8")

        buildTypes.release.proguard {
            isEnabled.set(false)
        }

        nativeDistributions {
            // Windows only — no Mac or Linux targets
            targetFormats(org.jetbrains.compose.desktop.application.dsl.TargetFormat.AppImage)
            packageName = "CloudStream-Desktop"
            packageVersion = "0.1.2"
            description = "CloudStream Desktop Client"
            vendor = "CloudStream"
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
                "jdk.localedata" // Required by Rhino JS Date functions
            )
            appResourcesRootDir.set(project.layout.projectDirectory.dir("appResources"))

            windows {
                iconFile.set(project.file("src/main/resources/logo_installer.ico"))
                menuGroup = "CloudStream Desktop"
                upgradeUuid = "d7e9b04f-723a-4467-84df-fcf470c1ae02"
                shortcut = true // Creates a Desktop shortcut during install
                perUserInstall = true // Installs per-user, avoids needing admin rights
            }
        }
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.register<JavaExec>("runTestWebViewPlayer") {
    mainClass.set("com.lagradost.cloudstream3.desktop.test.TestWebViewPlayerKt")
    classpath = sourceSets["main"].runtimeClasspath
    jvmArgs("-Djava.library.path=build/libs", "-Djna.library.path=appResources/windows/mpv")
}

tasks.register<JavaExec>("runTestMpvPlayer") {
    mainClass.set("com.lagradost.cloudstream3.desktop.test.TestMpvPlayerKt")
    classpath = sourceSets["main"].runtimeClasspath
    jvmArgs("-Djna.library.path=appResources/windows/mpv")
}
