package com.github.jengelman.gradle.plugins.shadow

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.SelfResolvingDependency
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.publish.maven.MavenPom
import org.gradle.api.publish.maven.MavenPublication

class ShadowExtension {
    private final Provider<Archive> archive
    private final Provider<List<Dep>> allDependencies

    ShadowExtension(Project project) {
        archive = project.provider {
            def archiveTask = project.tasks.withType(ShadowJar).getByName("shadowJar")
            new Archive(archiveTask.archiveFile, archiveTask.archiveClassifier)
        }
        allDependencies = project.provider {
            project.configurations.getByName("shadow").allDependencies.collect {
                if ((it instanceof ProjectDependency) || !(it instanceof SelfResolvingDependency)) {
                    new Dep(it.group, it.name, it.version)
                }
            }
        }
    }

    void component(MavenPublication publication) {
        publication.artifact([
                source    : archive.get().file,
                classifier: archive.get().classifier.get()
        ])

        // Don't inline this variable, it seems Groovy closure capturing is confused by the field instead of a local variable.
        final def allDeps = allDependencies
        publication.pom { MavenPom pom ->
            pom.withXml { xml ->
                def dependenciesNode = xml.asNode().get('dependencies') ?: xml.asNode().appendNode('dependencies')
                allDeps.get().each {
                    def dependencyNode = dependenciesNode.appendNode('dependency')
                    dependencyNode.appendNode('groupId', it.group)
                    dependencyNode.appendNode('artifactId', it.name)
                    dependencyNode.appendNode('version', it.version)
                    dependencyNode.appendNode('scope', 'runtime')
                }
            }
        }
    }

    private class Archive {
        Provider<RegularFile> file
        Property<String> classifier

        Archive(Provider<RegularFile> file, Property<String> classifier) {
            this.file = file
            this.classifier = classifier
        }
    }

    private class Dep {
        String group
        String name
        String version

        Dep(String group, String name, String version) {
            this.group = group
            this.name = name
            this.version = version
        }
    }
}
