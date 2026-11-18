plugins {
    kotlin("multiplatform") version "2.4.21"
    kotlin("plugin.serialization") version "2.4.21"
}

kotlin {
    jvmToolchain(21)
    jvm()
    macosArm64()
    iosArm64()
    iosSimulatorArm64()
    linuxArm64()
    linuxX64()
    mingwX64()

    sourceSets {
        commonMain {
            dependencies {
                api(project(":plugin-api"))
                implementation("io.github.charlietap.chasm:chasm:2.5.0")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.13.0")
            }
        }
    }
}

repositories {
    mavenCentral()
}
