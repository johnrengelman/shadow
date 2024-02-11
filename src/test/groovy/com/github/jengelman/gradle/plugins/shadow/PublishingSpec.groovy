package com.github.jengelman.gradle.plugins.shadow

import com.github.jengelman.gradle.plugins.shadow.util.AppendableMavenFileRepository
import com.github.jengelman.gradle.plugins.shadow.util.PluginSpecification
import groovy.json.JsonSlurper
import groovy.xml.XmlSlurper
import org.gradle.api.attributes.Bundling
import org.gradle.api.attributes.Usage

class PublishingSpec extends PluginSpecification {

    AppendableMavenFileRepository publishingRepo

    def setup() {
        publishingRepo = repo('remote_repo')
    }

    def "publish shadow jar with maven-publish plugin"() {
        given:
        repo.module('shadow', 'a', '1.0')
                .insertFile('a.properties', 'a')
                .insertFile('a2.properties', 'a2')
                .publish()
        repo.module('shadow', 'b', '1.0')
                .insertFile('b.properties', 'b')
                .publish()

        settingsFile << "rootProject.name = 'maven'"
        buildFile << """
            apply plugin: 'maven-publish'

            dependencies {
               implementation 'shadow:a:1.0'
               shadow 'shadow:b:1.0'
            }
            
            shadowJar {
               archiveClassifier = ''
               archiveBaseName = 'maven-all'
            }
            
            publishing {
               publications {
                   shadow(MavenPublication) { publication ->
                       project.shadow.component(publication)
                       artifactId = 'maven-all'
                   }
               }
               repositories {
                   maven {
                       url "${publishingRepo.uri}"
                   }
               }
            }
        """.stripIndent()

        when:
        run('publish')

        then:
        File publishedFile = publishingRepo.rootDir.file('shadow/maven-all/1.0/maven-all-1.0.jar').canonicalFile
        assert publishedFile.exists()

        and:
        contains(publishedFile, ['a.properties', 'a2.properties'])

        and:
        File pom = publishingRepo.rootDir.file('shadow/maven-all/1.0/maven-all-1.0.pom').canonicalFile
        assert pom.exists()

        def contents = new XmlSlurper().parse(pom)
        assert contents.dependencies.size() == 1
        assert contents.dependencies[0].dependency.size() == 1

        def dependency = contents.dependencies[0].dependency[0]
        assert dependency.groupId.text() == 'shadow'
        assert dependency.artifactId.text() == 'b'
        assert dependency.version.text() == '1.0'
    }

    def "publish shadow jar with maven-publish plugin using custom classifier and extension"() {
        given:
        repo.module('shadow', 'a', '1.0')
                .insertFile('a.properties', 'a')
                .insertFile('a2.properties', 'a2')
                .publish()
        repo.module('shadow', 'b', '1.0')
                .insertFile('b.properties', 'b')
                .publish()

        settingsFile << "rootProject.name = 'maven'"
        buildFile << """
            apply plugin: 'maven-publish'

            dependencies {
               implementation 'shadow:a:1.0'
               shadow 'shadow:b:1.0'
            }
            
            shadowJar {
               archiveClassifier = 'my-classifier'
               archiveExtension = 'my-ext'
               archiveBaseName = 'maven-all'
            }
            
            publishing {
               publications {
                   shadow(MavenPublication) { publication ->
                       project.shadow.component(publication)
                       artifactId = 'maven-all'
                   }
               }
               repositories {
                   maven {
                       url "${publishingRepo.uri}"
                   }
               }
            }
        """.stripIndent()

        when:
        run('publish')

        then:
        File publishedFile = publishingRepo.rootDir.file('shadow/maven-all/1.0/maven-all-1.0-my-classifier.my-ext').canonicalFile
        assert publishedFile.exists()
    }

    def "publish multiproject shadow jar with maven-publish plugin"() {
        given:

        settingsFile << """
            rootProject.name = 'maven'
            include 'a'
            include 'b'
            include 'c'
        """.stripMargin()

        buildFile.text = """
            subprojects {
                apply plugin: 'java'
                apply plugin: 'maven-publish'

                version = "1.0"
                group = 'shadow'
    
                repositories { maven { url "${repo.uri}" } }
                publishing {
                   repositories {
                       maven {
                           url "${publishingRepo.uri}"
                       }
                   }
                }
            }
        """.stripIndent()

        file('a/build.gradle') << """
            plugins {
                id 'java'
                id 'maven-publish'
            }
        """.stripMargin()

        file('a/src/main/resources/a.properties') << 'a'
        file('a/src/main/resources/a2.properties') << 'a2'

        file('b/build.gradle') << """
            plugins {
                id 'java'
                id 'maven-publish'
            }
        """.stripMargin()

        file('b/src/main/resources/b.properties') << 'b'

        file('c/build.gradle') << """
            plugins {
                id 'com.github.johnrengelman.shadow'
            }
            
            dependencies {
                implementation project(':a')
                shadow project(':b')
            }

            shadowJar {
               archiveClassifier = ''
               archiveBaseName = 'maven-all'
            }
            
            publishing {
               publications {
                   shadow(MavenPublication) { publication ->
                       project.shadow.component(publication)
                       artifactId = 'maven-all'
                   }
               }
            }
        """.stripMargin()

        when:
        run('publish')

        then:
        File publishedFile = publishingRepo.rootDir.file('shadow/maven-all/1.0/maven-all-1.0.jar').canonicalFile
        assert publishedFile.exists()

        and:
        contains(publishedFile, ['a.properties', 'a2.properties'])

        and:
        File pom = publishingRepo.rootDir.file('shadow/maven-all/1.0/maven-all-1.0.pom').canonicalFile
        assert pom.exists()

        def contents = new XmlSlurper().parse(pom)
        assert contents.dependencies.size() == 1
        assert contents.dependencies[0].dependency.size() == 1

        def dependency = contents.dependencies[0].dependency[0]
        assert dependency.groupId.text() == 'shadow'
        assert dependency.artifactId.text() == 'b'
        assert dependency.version.text() == '1.0'
    }

    def "publish shadow jar with maven-publish plugin and Gradle metadata"() {
        given:
        repo.module('shadow', 'a', '1.0')
                .insertFile('a.properties', 'a')
                .insertFile('a2.properties', 'a2')
                .publish()
        repo.module('shadow', 'b', '1.0')
                .insertFile('b.properties', 'b')
                .publish()

        settingsFile << """
            rootProject.name = 'maven'
        """
        buildFile << """
            apply plugin: 'maven-publish'
            dependencies {
               implementation 'shadow:a:1.0'
               implementation 'shadow:b:1.0'
               shadow 'shadow:b:1.0'
            }
            group = 'com.acme'
            version = '1.0'
            publishing {
               publications {
                   java(MavenPublication) {
                       from components.java
                   }
               }
               repositories {
                   maven {
                       url "${publishingRepo.uri}"
                   }
               }
            }
        """.stripIndent()

        when:
        run('publish')

        then:
        File mainJar = publishingRepo.rootDir.file('com/acme/maven/1.0/maven-1.0.jar').canonicalFile
        File shadowJar = publishingRepo.rootDir.file('com/acme/maven/1.0/maven-1.0-all.jar').canonicalFile
        assert mainJar.exists()
        assert shadowJar.exists()

        and:
        contains(shadowJar, ['a.properties', 'a2.properties'])

        and: "publishes both a POM file and a Gradle metadata file"
        File pom = publishingRepo.rootDir.file('com/acme/maven/1.0/maven-1.0.pom').canonicalFile
        File gmm = publishingRepo.rootDir.file('com/acme/maven/1.0/maven-1.0.module').canonicalFile
        pom.exists()
        gmm.exists()

        when: "POM file corresponds to a regular Java publication"
        def pomContents = new XmlSlurper().parse(pom)
        pomContents.dependencies.size() == 2

        then:
        def dependency1 = pomContents.dependencies[0].dependency[0]
        dependency1.groupId.text() == 'shadow'
        dependency1.artifactId.text() == 'a'
        dependency1.version.text() == '1.0'

        def dependency2 = pomContents.dependencies[0].dependency[1]
        dependency2.groupId.text() == 'shadow'
        dependency2.artifactId.text() == 'b'
        dependency2.version.text() == '1.0'

        when: "Gradle module metadata contains the Shadow variants"
        def gmmContents = new JsonSlurper().parse(gmm)

        then:
        gmmContents.variants.size() == 3
        gmmContents.variants.name as Set == ['apiElements', 'runtimeElements', 'shadowRuntimeElements'] as Set

        def apiVariant = gmmContents.variants.find { it.name == 'apiElements' }
        apiVariant.attributes[Usage.USAGE_ATTRIBUTE.name] == Usage.JAVA_API
        apiVariant.attributes[Bundling.BUNDLING_ATTRIBUTE.name] == Bundling.EXTERNAL
        !apiVariant.dependencies

        def runtimeVariant = gmmContents.variants.find { it.name == 'runtimeElements' }
        runtimeVariant.attributes[Usage.USAGE_ATTRIBUTE.name] == Usage.JAVA_RUNTIME
        runtimeVariant.attributes[Bundling.BUNDLING_ATTRIBUTE.name] == Bundling.EXTERNAL
        runtimeVariant.dependencies.size() == 2
        runtimeVariant.dependencies.module as Set == ['a', 'b'] as Set

        def shadowRuntimeVariant = gmmContents.variants.find { it.name == 'shadowRuntimeElements' }
        shadowRuntimeVariant.attributes[Usage.USAGE_ATTRIBUTE.name] == Usage.JAVA_RUNTIME
        shadowRuntimeVariant.attributes[Bundling.BUNDLING_ATTRIBUTE.name] == Bundling.SHADOWED
        shadowRuntimeVariant.dependencies.size() == 1
        shadowRuntimeVariant.dependencies.module as Set == ['b'] as Set

    }
}
