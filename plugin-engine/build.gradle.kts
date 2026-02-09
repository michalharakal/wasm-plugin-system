plugins {
    kotlin("multiplatform") version "2.3.0"
    kotlin("plugin.serialization") version "2.3.0"
}

kotlin {
    jvmToolchain(21)
    jvm()

    sourceSets {
        commonMain {
            dependencies {
                api(project(":plugin-api"))
                implementation("io.github.charlietap.chasm:chasm:1.3.1")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
            }
        }
    }
}

repositories {
    mavenCentral()
}
