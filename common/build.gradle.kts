plugins {
    kotlin("jvm")
    alias(libs.plugins.sqldelight)
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
