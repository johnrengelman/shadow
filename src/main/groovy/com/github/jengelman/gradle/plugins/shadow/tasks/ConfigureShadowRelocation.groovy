package com.github.jengelman.gradle.plugins.shadow.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction

import java.util.jar.JarFile


class ConfigureShadowRelocation extends DefaultTask {

    @Input
    ShadowJar target

    @Input
    String prefix = "shadow"

    @Input
    List<String> excludedPackages = []

    @InputFiles @Optional
    List<Configuration> getConfigurations() {
        return target.configurations
    }

    @TaskAction
    void configureRelocation() {
        def packages = [] as Set<String>
        configurations.each { configuration ->
            configuration.files.each { jar ->
                JarFile jf = new JarFile(jar)
                jf.entries().each { entry ->
                    if (entry.name.endsWith(".class") && !matchesExcludeList(entry.name)) {
                        packages << entry.name[0..entry.name.lastIndexOf('/')-1].replaceAll('/', '.')
                    }
                }
                jf.close()
            }
        }
        packages.each {
            target.relocate(it, "${prefix}.${it}")
        }

    }

    static String taskName(Task task) {
        return "configureRelocation${task.name.capitalize()}"
    }

    boolean matchesExcludeList(String className) {
        return excludedPackages.any {
            if (className.startsWith(it)) {
                return true
            }
        }
    }
}
