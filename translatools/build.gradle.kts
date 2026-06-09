plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    alias(libs.plugins.shadow)
    id("com.github.taskeren.standalone")
}

repositories {
    mavenCentral()
    maven("https://jitpack.io") {
        mavenContent {
            @Suppress("UnstableApiUsage")
            includeGroupAndSubgroups("com.github")
        }
    }
}

dependencies {
    implementation(project(":cursefetch"))

    implementation(libs.jspecify)

    implementation(libs.ktor.client.contentNegotiation)
    implementation(libs.ktor.serialization.kotlinxJson)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)

    implementation(libs.dotenv)

    implementation(libs.ftbsnbt)
    implementation(libs.hellonbt.taskeren)

    testImplementation(kotlin("test"))
    testImplementation(libs.ktor.client.logging)
    testImplementation(libs.logback.classic)

    standaloneImplementation(libs.clikt)
    standaloneImplementation(project(path = ":cursefetch", configuration = "standaloneApiElements"))
}

configurations.all {
    resolutionStrategy.dependencySubstitution {
        // substitute(module("org.glavo:HelloNBT")) using module("com.github.Taskeren:HelloNBT:13593c05b3")
    }
}

kotlin {
    explicitApiWarning()

    compilerOptions {
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

standalone {
    mainClass = "cn.elytra.translatools.MainKt"
}
