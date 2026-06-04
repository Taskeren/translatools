import org.gradle.kotlin.dsl.kotlin
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("standalone")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":cursefetch"))

    implementation(libs.jspecify)

    implementation(libs.ktor.client.contentNegotiation)
    implementation(libs.ktor.serialization.kotlinxJson)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)

    implementation(libs.dotenv)

    testImplementation(kotlin("test"))
    testImplementation(libs.ktor.client.logging)
    testImplementation(libs.logback.classic)

    standaloneImplementation(libs.clikt)
    standaloneImplementation(project(path = ":cursefetch", configuration = "standaloneApiElements"))
}

kotlin {
    explicitApiWarning()

    compilerOptions {
        freeCompilerArgs.addAll("-Xcontext-parameters", "-Xexplicit-backing-fields")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.standaloneJar.configure {
    mainClass = "cn.elytra.translatools.MainKt"
}
