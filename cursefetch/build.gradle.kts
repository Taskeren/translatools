plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    alias(libs.plugins.shadow)
    id("com.github.taskeren.standalone")
}

repositories {
    mavenCentral()
}

dependencies {
    api(libs.ktor.client.core)

    implementation(libs.ktor.client.contentNegotiation)
    implementation(libs.ktor.serialization.kotlinxJson)

    runtimeOnly(libs.ktor.client.cio)

    standaloneImplementation(sourceSets.main.get().output)
    standaloneImplementation(libs.clikt)
    standaloneImplementation(libs.slf4j.nop)
}
