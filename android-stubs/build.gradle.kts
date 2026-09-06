plugins {
    kotlin("jvm")
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

dependencies {
    // Only standard library, no desktop-app dependencies.
    implementation(kotlin("stdlib"))
    implementation(project(":common"))
}

sourceSets {
    main {
        kotlin.srcDirs("src/main/java")
        java.srcDirs("src/main/java")
    }
}
