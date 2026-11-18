plugins {
    kotlin("multiplatform") version "2.4.21"
    kotlin("plugin.serialization") version "2.4.21"
}

@OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
kotlin {
    jvmToolchain(21)
    jvm()
    wasmWasi()
    macosArm64()
    iosArm64()
    iosSimulatorArm64()
    linuxArm64()
    linuxX64()
    mingwX64()

    sourceSets {
        commonMain {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.13.0")
            }
        }
    }
}

repositories {
    mavenCentral()
}
