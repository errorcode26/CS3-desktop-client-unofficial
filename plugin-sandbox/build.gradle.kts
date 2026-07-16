plugins {
    kotlin("jvm")
    application
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

dependencies {
    // Needs library to know what a plugin is
    implementation(project(":library"))
    implementation(project(":plugin-runtime"))
    implementation(project(":android-stubs"))

    // Needs desktop-app to scan your Android stubs
    implementation(project(":desktop-app"))
    implementation(project(":common"))
    implementation(project(":player-abstraction"))
    implementation("org.bouncycastle:bcprov-jdk18on:1.77")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // ASM for bytecode analysis
    implementation("org.ow2.asm:asm:9.7")
    implementation("org.ow2.asm:asm-tree:9.7")
    implementation("org.ow2.asm:asm-commons:9.7")

    // JSON and Coroutines
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    // Dalvik-to-JVM compatibility layer
    implementation("de.femtopedia.dex2jar:dex-tools:2.4.35")

    // For command line arguments (optional, but good for testers)
    implementation("com.github.ajalt.clikt:clikt:4.2.2")
}

application {
    mainClass.set("PluginTesterKt")
    applicationDefaultJvmArgs = listOf("-Djava.security.manager=allow")
}

tasks.named<JavaExec>("run") {
    if (project.hasProperty("args")) {
        args = listOf(project.property("args") as String)
    }
}

tasks.register<JavaExec>("runFetcher") {
    group = "application"
    description = "Runs the RepoFetcher script to download plugins."
    mainClass.set("RepoFetcherKt")
    classpath = sourceSets["main"].runtimeClasspath

    // Pass along arguments
    if (project.hasProperty("args")) {
        args(project.property("args") as String)
    }
}
