plugins {
    kotlin("jvm")
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
