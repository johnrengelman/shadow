package com.github.jengelman.gradle.plugins.shadow.util.repo.maven

import groovy.xml.XmlParser

/**
 * http://maven.apache.org/ref/3.0.1/maven-repository-metadata/repository-metadata.html
 */
class DefaultMavenMetaData implements MavenMetaData{

    String text

    String groupId
    String artifactId
    String version

    List<String> versions = []
    String lastUpdated

    DefaultMavenMetaData(File file) {
        text = file.text
        def xml = new XmlParser().parseText(text)

        groupId = xml.groupId[0]?.text()
        artifactId = xml.artifactId[0]?.text()
        version = xml.version[0]?.text()

        def versioning = xml.versioning[0]

        lastUpdated = versioning.lastUpdated[0]?.text()

        versioning.versions[0].version.each {
            versions << it.text()
        }
    }
}
