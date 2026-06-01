plugins {
    kotlin("jvm") version libs.versions.kotlin.get() apply false
    kotlin("plugin.serialization") version libs.versions.kotlin.get() apply false
}

group = "cn.elytra"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}
