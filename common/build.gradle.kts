plugins {
    kotlin("jvm")
    alias(libs.plugins.sqldelight)
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
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.1")
    implementation(libs.slf4j.api)

    implementation(libs.sqldelight.sqlite.driver)
    implementation(libs.sqldelight.coroutines.extensions)

    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

sqldelight {
    databases {
        create("DesktopDatabase") {
            packageName.set("com.lagradost.common.db")
        }
    }
}
