plugins {
    kotlin("multiplatform") version "2.4.21"
    kotlin("plugin.serialization") version "2.4.21"
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
