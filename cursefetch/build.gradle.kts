import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    alias(libs.plugins.shadow)
}

repositories {
    mavenCentral()
}

val standalone: SourceSet =
    sourceSets.create("standalone") {
        compileClasspath += sourceSets.main.get().compileClasspath
        runtimeClasspath += sourceSets.main.get().runtimeClasspath
    }

val standaloneImplementation: Configuration by configurations.getting {
    extendsFrom(configurations.implementation.get())
}

val standaloneRuntimeOnly: Configuration by configurations.getting {
    extendsFrom(configurations.runtimeOnly.get())
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

tasks.register<ShadowJar>("standaloneJar") {
    group = "shadow"
    description = "Create a fat-jar for standalone"

    archiveClassifier = "standalone"
    manifest {
        attributes["Main-Class"] = "cn.elytra.translatools.cursefetch.CurseFetchKt"
    }
    from(sourceSets.main.get().output)
    from(standalone.output)
    configurations = listOf(project.configurations.named("standaloneRuntimeClasspath").get())
    mergeServiceFiles()
}
