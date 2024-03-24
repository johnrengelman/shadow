package com.github.jengelman.gradle.plugins.shadow.util.repo.maven

import groovy.xml.XmlParser

class MavenPom {
    String groupId
    String artifactId
    String version
    String packaging
    String description
    final Map<String, MavenScope> scopes = [:]

    MavenPom(File pomFile) {
        if (pomFile.exists()){
            def pom = new XmlParser().parse(pomFile)

            groupId = pom.groupId[0]?.text()
            artifactId = pom.artifactId[0]?.text()
            version = pom.version[0]?.text()
            packaging = pom.packaging[0]?.text()
            description = pom.description[0]?.text()

            pom.dependencies.dependency.each { dep ->
                def scopeElement = dep.scope
                def scopeName = scopeElement ? scopeElement.text() : "runtime"
                def scope = scopes[scopeName]
                if (!scope) {
                    scope = new MavenScope()
                    scopes[scopeName] = scope
                }
                MavenDependency mavenDependency = new MavenDependency(
                        groupId: dep.groupId.text(),
                        artifactId: dep.artifactId.text(),
                        version: dep.version.text(),
                        classifier: dep.classifier ? dep.classifier.text() : null,
                        type: dep.type ? dep.type.text() : null
                )
                def key = "${mavenDependency.groupId}:${mavenDependency.artifactId}:${mavenDependency.version}"
                key += mavenDependency.classifier ? ":${mavenDependency.classifier}" : ""
                scope.dependencies[key] = mavenDependency
            }
        }
    }
}
