plugins {
    kotlin("multiplatform") version "2.3.0"
    kotlin("plugin.serialization") version "2.3.0"
}

repositories {
    mavenCentral()
}

@OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
kotlin {
    wasmWasi {
        binaries.executable()
    }

    sourceSets {
        wasmWasiMain {
            dependencies {
                implementation(project(":plugin-api"))
            }
        }
    }
}
