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
    implementation(project(":plugin-engine"))
}

application {
    mainClass.set("ChasmTestKt")
}

tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
}
