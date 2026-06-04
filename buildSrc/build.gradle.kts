plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
}

dependencies {
    api(gradleApi())
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:${libs.versions.kotlin.get()}")
    implementation("com.gradleup.shadow:shadow-gradle-plugin:${libs.versions.shadow.get()}")
}

gradlePlugin {
    plugins {
        create("standalone") {
            id = "standalone"
            implementationClass = "cn.elytra.buildsrc.StandaloneTargetPlugin"
        }
    }
}
