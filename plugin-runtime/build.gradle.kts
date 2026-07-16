plugins {
    kotlin("jvm")
}

dependencies {
    // Needs access to stubs to pass to plugins
    implementation(project(":android-stubs"))

    // Needs access to base CloudstreamPlugin and Extractors
    implementation(project(":library"))

    // Logging and common utils
    implementation(project(":common"))

    // Dalvik-to-JVM transcompiler
    implementation("de.femtopedia.dex2jar:dex-tools:2.4.35")

    // JSON for manifest parsing
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.1")

    // ASM Bytecode Manipulation for Static Verification
    implementation("org.ow2.asm:asm:9.6")
    implementation("org.ow2.asm:asm-tree:9.6")

    testImplementation(kotlin("test"))
}

tasks.withType<Test> {
    useJUnitPlatform()
}
