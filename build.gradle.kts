plugins {
    kotlin("jvm") version libs.versions.kotlin.get() apply false
    kotlin("plugin.serialization") version libs.versions.kotlin.get() apply false
    alias(libs.plugins.shadow) apply false
    id("com.github.taskeren.standalone") version "1.0.1" apply false
}

group = "cn.elytra"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}
