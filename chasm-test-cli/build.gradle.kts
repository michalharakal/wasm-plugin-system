plugins {
    kotlin("jvm") version "2.3.0"
    application
}

kotlin {
    jvmToolchain(21)
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.github.charlietap.chasm:chasm:1.3.1")
}

application {
    mainClass.set("ChasmTestKt")
}
