package cn.elytra.buildsrc

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.getByName
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.getValue
import org.gradle.kotlin.dsl.register
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

class StandaloneTargetPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.plugins.withId("java") {
            val javaExtension = project.extensions.getByType<JavaPluginExtension>()
            val sourceSets = javaExtension.sourceSets
            val mainSourceSet = sourceSets.getByName("main")
            val standaloneSourceSet by sourceSets.register("standalone") {
                compileClasspath += mainSourceSet.compileClasspath + mainSourceSet.output
                runtimeClasspath += mainSourceSet.runtimeClasspath + mainSourceSet.output
            }
            val configurations = project.configurations
            configurations.getByName("standaloneImplementation") {
                extendsFrom(configurations.getByName("implementation"))
            }
            configurations.getByName("standaloneRuntimeOnly") {
                extendsFrom(configurations.getByName("runtimeOnly"))
            }

            // make standalone accessible with main internal stuff
            val jvmProjectExtension = project.extensions.getByType<KotlinJvmProjectExtension>()
            val jvmProtectTarget = jvmProjectExtension.target
            jvmProtectTarget.compilations
                .getByName("standalone")
                .associateWith(jvmProtectTarget.compilations.getByName("main"))

            // task standaloneJarThin
            project.tasks.register<Jar>("standaloneJarThin") {
                group = "standalone"
                description = "Assembles standalone jar archive"
                archiveClassifier.set("standalone-thin")
                from(mainSourceSet.output)
                from(standaloneSourceSet.output)
            }

            // add shadow
            // FIXME: make it optional
            project.pluginManager.apply("com.gradleup.shadow")

            // configure shadow if capable
            project.pluginManager.withPlugin("com.gradleup.shadow") {
                project.tasks.register<ShadowJar>("standaloneJar") {
                    group = "standalone"
                    description = "Assembles standalone jar archive for shadow jar"

                    archiveClassifier.set("standalone")
                    from(mainSourceSet.output)
                    from(standaloneSourceSet.output)
                    this.configurations.set(listOf(configurations.getByName("standaloneRuntimeClasspath")))
                    mergeServiceFiles()
                }
            }
        }
    }
}
