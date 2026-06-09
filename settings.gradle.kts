pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://jitpack.io")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "translatools"

include("cursefetch")
include("translatools")

// TODO: remove this
includeBuild("vendor/HelloNBT") {
    dependencySubstitution {
        substitute(module("com.github.Taskeren:HelloNBT")).using(project(":"))
    }
}
