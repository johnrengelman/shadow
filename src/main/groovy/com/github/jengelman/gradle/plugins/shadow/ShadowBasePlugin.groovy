package com.github.jengelman.gradle.plugins.shadow

import com.github.jengelman.gradle.plugins.shadow.tasks.KnowsTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.util.GradleVersion

class ShadowBasePlugin implements Plugin<Project> {

    public static final String EXTENSION_NAME = 'shadow'
    public static final String CONFIGURATION_NAME = 'shadow'

    @Override
    void apply(Project project) {
        if (GradleVersion.current() < GradleVersion.version("8.3")) {
            throw new GradleException("This version of Shadow supports Gradle 8.3+ only. Please upgrade.")
        }
        project.extensions.create(EXTENSION_NAME, ShadowExtension, project)
        createShadowConfiguration(project)

        project.tasks.register(KnowsTask.NAME, KnowsTask) { knows ->
            knows.group = ShadowJavaPlugin.SHADOW_GROUP
            knows.description = DESC
        }
    }

    private static void createShadowConfiguration(Project project) {
        project.configurations.create(CONFIGURATION_NAME)
    }
}
