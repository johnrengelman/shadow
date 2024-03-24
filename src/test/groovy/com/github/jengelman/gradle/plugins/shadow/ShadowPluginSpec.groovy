package com.github.jengelman.gradle.plugins.shadow

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.github.jengelman.gradle.plugins.shadow.util.PluginSpecification
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.plugins.JavaPlugin
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testkit.runner.BuildResult
import spock.lang.Ignore
import spock.lang.Issue
import spock.lang.Unroll

import java.util.jar.Attributes
import java.util.jar.JarFile

class ShadowPluginSpec extends PluginSpecification {

    def 'apply plugin'() {
        given:
        String projectName = 'myshadow'
        String version = '1.0.0'

        Project project = ProjectBuilder.builder().withName(projectName).build()
        project.version = version

        when:
        project.plugins.apply(ShadowPlugin)

        then:
        project.plugins.hasPlugin(ShadowPlugin)

        and:
        assert !project.tasks.findByName('shadowJar')

        when:
        project.plugins.apply(JavaPlugin)

        then:
        ShadowJar shadow = project.tasks.findByName('shadowJar')
        assert shadow
        assert shadow.archiveBaseName.get() == projectName
        assert shadow.destinationDirectory.get().asFile == new File(project.layout.buildDirectory.asFile.get(), 'libs')
        assert shadow.archiveVersion.get() == version
        assert shadow.archiveClassifier.get() == 'all'
        assert shadow.archiveExtension.get() == 'jar'

        and:
        Configuration shadowConfig = project.configurations.findByName('shadow')
        assert shadowConfig
        shadowConfig.artifacts.file.contains(shadow.archiveFile.get().asFile)

    }

    @Unroll
    def 'Compatible with Gradle #version'() {
        given:
        File one = buildJar('one.jar').insertFile('META-INF/services/shadow.Shadow',
                'one # NOTE: No newline terminates this line/file').write()

        repo.module('shadow', 'two', '1.0').insertFile('META-INF/services/shadow.Shadow',
                'two # NOTE: No newline terminates this line/file').publish()

        buildFile << """
            dependencies {
              implementation 'junit:junit:3.8.2'
              implementation files('${escapedPath(one)}')
            }

            shadowJar {
               mergeServiceFiles()
            }
        """.stripIndent()

        when:
        run(['shadowJar']) {
            it.withGradleVersion(version)
            it.withDebug(true)
            it.withTestKitDir(getTestKitDir())
        }

        then:
        assert output.exists()

        where:
        version << ['8.3']
    }

    def 'Error in Gradle versions < 8.3'() {
        given:
        buildFile << """
            dependencies {
              implementation 'junit:junit:3.8.2'
            }

            shadowJar {
               mergeServiceFiles()
            }
        """.stripIndent()

        expect:
        runWithFailure(['shadowJar']) {
            it.withGradleVersion('7.0')
            it.withDebug(true)
            it.withTestKitDir(getTestKitDir())
        }
    }

    def 'shadow copy'() {
        given:
        URL artifact = this.class.classLoader.getResource('test-artifact-1.0-SNAPSHOT.jar')
        URL project = this.class.classLoader.getResource('test-project-1.0-SNAPSHOT.jar')

        buildFile << """
            shadowJar {
                from('${artifact.path}')
                from('${project.path}')
            }
        """.stripIndent()

        when:
        run('shadowJar')

        then:
        assert output.exists()
    }

    def 'include project sources'() {
        given:
        file('src/main/java/shadow/Passed.java') << '''
            package shadow;
            public class Passed {}
        '''.stripIndent()

        buildFile << """
            dependencies { implementation 'junit:junit:3.8.2' }

            // tag::rename[]
            shadowJar {
               archiveBaseName = 'shadow'
               archiveClassifier = null
               archiveVersion = null
               archiveVersion.convention(null)
            }
            // end::rename[]
        """.stripIndent()

        when:
        run('shadowJar')

        then:
        contains(output("shadow.jar"), ['shadow/Passed.class', 'junit/framework/Test.class'])

        and:
        doesNotContain(output("shadow.jar"), ['/'])
    }

    def 'include project dependencies'() {
        given:
        file('settings.gradle') << """
            include 'client', 'server'
        """.stripIndent()

        file('client/src/main/java/client/Client.java') << """
            package client;
            public class Client {}
            """.stripIndent()

        file('client/build.gradle') << """
            apply plugin: 'java'
            repositories { maven { url "${repo.uri}" } }
            dependencies { implementation 'junit:junit:3.8.2' }
        """.stripIndent()

        file('server/src/main/java/server/Server.java') << """
            package server;

            import client.Client;

            public class Server {}
        """.stripIndent()

        file('server/build.gradle') << """
            apply plugin: 'java'
            apply plugin: 'com.github.johnrengelman.shadow'

            repositories { maven { url "${repo.uri}" } }
            dependencies { implementation project(':client') }

        """.stripIndent()

        File serverOutput = getFile('server/build/libs/server-all.jar')

        when:
        run(':server:shadowJar')

        then:
        serverOutput.exists()
        contains(serverOutput, [
                'client/Client.class',
                'server/Server.class',
                'junit/framework/Test.class'
        ])
    }

    /**
     * 'Server' depends on 'Client'. 'junit' is independent.
     * The minimize shall remove 'junit'.
     */
    def 'minimize by keeping only transitive dependencies'() {
        given:
        file('settings.gradle') << """
            include 'client', 'server'
        """.stripIndent()

        file('client/src/main/java/client/Client.java') << """
            package client;
            public class Client {}
        """.stripIndent()

        file('client/build.gradle') << """
            apply plugin: 'java'
            repositories { maven { url "${repo.uri}" } }
            dependencies { implementation 'junit:junit:3.8.2' }
        """.stripIndent()

        file('server/src/main/java/server/Server.java') << """
            package server;

            import client.Client;

            public class Server {
                private final String client = Client.class.getName();
            }
        """.stripIndent()

        file('server/build.gradle') << """
            apply plugin: 'java'
            apply plugin: 'com.github.johnrengelman.shadow'

            shadowJar {
                minimize()
            }

            repositories { maven { url "${repo.uri}" } }
            dependencies { implementation project(':client') }
        """.stripIndent()

        File serverOutput = getFile('server/build/libs/server-all.jar')

        when:
        runWithDebug(':server:shadowJar')

        then:
        serverOutput.exists()
        contains(serverOutput, [
                'client/Client.class',
                'server/Server.class'
        ])
        doesNotContain(serverOutput, ['junit/framework/Test.class'])
    }

    /**
     * 'Client', 'Server' and 'junit' are independent.
     * 'junit' is excluded from the minimize.
     * The minimize shall remove 'Client' but not 'junit'.
     */
    def 'exclude a dependency from minimize'() {
        given:
        file('settings.gradle') << """
            include 'client', 'server'
        """.stripIndent()

        file('client/src/main/java/client/Client.java') << """
            package client;
            public class Client {}
        """.stripIndent()

        file('client/build.gradle') << """
            apply plugin: 'java'
            repositories { maven { url "${repo.uri}" } }
            dependencies { implementation 'junit:junit:3.8.2' }
        """.stripIndent()

        file('server/src/main/java/server/Server.java') << """
            package server;
            public class Server {}
        """.stripIndent()

        file('server/build.gradle') << """
            apply plugin: 'java'
            apply plugin: 'com.github.johnrengelman.shadow'

            shadowJar {
                minimize {
                    exclude(dependency('junit:junit:.*'))
                }
            }

            repositories { maven { url "${repo.uri}" } }
            dependencies { implementation project(':client') }
        """.stripIndent()

        File serverOutput = getFile('server/build/libs/server-all.jar')

        when:
        runWithDebug(':server:shadowJar')

        then:
        serverOutput.exists()
        contains(serverOutput, [
                'server/Server.class',
                'junit/framework/Test.class'
        ])
        doesNotContain(serverOutput, ['client/Client.class'])
    }

    /**
     * 'Client', 'Server' and 'junit' are independent.
     * Unused classes of 'client' and theirs dependencies shouldn't be removed.
     */
    def 'exclude a project from minimize '() {
        given:
        file('settings.gradle') << """
            include 'client', 'server'
        """.stripIndent()

        file('client/src/main/java/client/Client.java') << """
            package client;
            public class Client {}
        """.stripIndent()

        file('client/build.gradle') << """
            apply plugin: 'java'
            repositories { maven { url "${repo.uri}" } }
        """.stripIndent()

        file('server/src/main/java/server/Server.java') << """
            package server;
            public class Server {}
        """.stripIndent()

        file('server/build.gradle') << """
            apply plugin: 'java'
            apply plugin: 'com.github.johnrengelman.shadow'

            shadowJar {
                minimize {
                    exclude(project(':client'))
                }
            }

            repositories { maven { url "${repo.uri}" } }
            dependencies { implementation project(':client') }
        """.stripIndent()

        File serverOutput = file('server/build/libs/server-all.jar')

        when:
        runWithDebug(':server:shadowJar')

        then:
        contains(serverOutput, [
                'client/Client.class',
                'server/Server.class'
        ])
    }

    /**
     * 'Client', 'Server' and 'junit' are independent.
     * Unused classes of 'client' and theirs dependencies shouldn't be removed.
     */
    def 'exclude a project from minimize - shall not exclude transitive dependencies that are used in subproject'() {
        given:
        file('settings.gradle') << """
            include 'client', 'server'
        """.stripIndent()

        file('client/src/main/java/client/Client.java') << """
            package client;
            import junit.framework.TestCase;
            public class Client extends TestCase {
                public static void main(String[] args) {} 
            }
        """.stripIndent()

        file('client/build.gradle') << """
            apply plugin: 'java'
            repositories { maven { url "${repo.uri}" } }
            dependencies { implementation 'junit:junit:3.8.2' }
        """.stripIndent()

        file('server/src/main/java/server/Server.java') << """
            package server;
            public class Server {}
        """.stripIndent()

        file('server/build.gradle') << """
            apply plugin: 'java'
            apply plugin: 'com.github.johnrengelman.shadow'

            shadowJar {
                minimize {
                    exclude(project(':client'))
                }
            }

            repositories { maven { url "${repo.uri}" } }
            dependencies { implementation project(':client') }
        """.stripIndent()

        File serverOutput = file('server/build/libs/server-all.jar')

        when:
        runWithDebug(':server:shadowJar')

        then:
        contains(serverOutput, [
                'client/Client.class',
                'server/Server.class',
                'junit/framework/TestCase.class'
        ])
    }

    /**
     * 'Client', 'Server' and 'junit' are independent.
     * Unused classes of 'client' and theirs dependencies shouldn't be removed.
     */
    def 'exclude a project from minimize - shall not exclude transitive dependencies from subproject that are not used'() {
        given:
        file('settings.gradle') << """
            include 'client', 'server'
        """.stripIndent()

        file('client/src/main/java/client/Client.java') << """
            package client;
            public class Client { }
        """.stripIndent()

        file('client/build.gradle') << """
            apply plugin: 'java'
            repositories { maven { url "${repo.uri}" } }
            dependencies { implementation 'junit:junit:3.8.2' }
        """.stripIndent()

        file('server/src/main/java/server/Server.java') << """
            package server;
            public class Server {}
        """.stripIndent()

        file('server/build.gradle') << """
            apply plugin: 'java'
            apply plugin: 'com.github.johnrengelman.shadow'

            shadowJar {
                minimize {
                    exclude(project(':client'))
                }
            }

            repositories { maven { url "${repo.uri}" } }
            dependencies { implementation project(':client') }
        """.stripIndent()

        File serverOutput = file('server/build/libs/server-all.jar')

        when:
        runWithDebug(':server:shadowJar')

        then:
        contains(serverOutput, [
                'client/Client.class',
                'server/Server.class',
                'junit/framework/TestCase.class'
        ])
    }


    /**
     * 'api' used as api for 'impl', and depended on 'lib'. 'junit' is independent.
     * The minimize shall remove 'junit', but not 'api'.
     * Unused classes of 'api' and theirs dependencies also shouldn't be removed.
     */
    def 'use minimize with dependencies with api scope'() {
        given:
        file('settings.gradle') << """
            include 'api', 'lib', 'impl'
        """.stripIndent()

        file('lib/src/main/java/lib/LibEntity.java') << """
            package lib;
            public interface LibEntity {}
        """.stripIndent()

        file('lib/src/main/java/lib/UnusedLibEntity.java') << """
            package lib;
            public class UnusedLibEntity implements LibEntity {}
        """.stripIndent()

        file('lib/build.gradle') << """
            apply plugin: 'java'
            repositories { maven { url "${repo.uri}" } }
        """.stripIndent()

        file('api/src/main/java/api/Entity.java') << """
            package api;
            public interface Entity {}
        """.stripIndent()

        file('api/src/main/java/api/UnusedEntity.java') << """
            package api;
            import lib.LibEntity;
            public class UnusedEntity implements LibEntity {}
        """.stripIndent()

        file('api/build.gradle') << """
            apply plugin: 'java'
            repositories { maven { url "${repo.uri}" } }
            dependencies {
                implementation 'junit:junit:3.8.2'
                implementation project(':lib')
            }
        """.stripIndent()

        file('impl/src/main/java/impl/SimpleEntity.java') << """
            package impl;
            import api.Entity;
            public class SimpleEntity implements Entity {}
        """.stripIndent()

        file('impl/build.gradle') << """
            apply plugin: 'java-library'
            apply plugin: 'com.github.johnrengelman.shadow'

            shadowJar {
                minimize()
            }

            repositories { maven { url "${repo.uri}" } }
            dependencies { api project(':api') }
        """.stripIndent()

        File serverOutput = getFile('impl/build/libs/impl-all.jar')

        when:
        runWithDebug(':impl:shadowJar')

        then:
        serverOutput.exists()
        contains(serverOutput, [
                'impl/SimpleEntity.class',
                'api/Entity.class',
                'api/UnusedEntity.class',
                'lib/LibEntity.class',
        ])
        doesNotContain(serverOutput, ['junit/framework/Test.class', 'lib/UnusedLibEntity.class'])
    }

    /**
     * 'api' used as api for 'impl', and 'lib' used as api for 'api'.
     * Unused classes of 'api' and 'lib' shouldn't be removed.
     */
    def 'use minimize with transitive dependencies with api scope'() {
        given:
        file('settings.gradle') << """
            include 'api', 'lib', 'impl'
        """.stripIndent()

        file('lib/src/main/java/lib/LibEntity.java') << """
            package lib;
            public interface LibEntity {}
        """.stripIndent()

        file('lib/src/main/java/lib/UnusedLibEntity.java') << """
            package lib;
            public class UnusedLibEntity implements LibEntity {}
        """.stripIndent()

        file('lib/build.gradle') << """
            apply plugin: 'java'
            repositories { maven { url "${repo.uri}" } }
        """.stripIndent()

        file('api/src/main/java/api/Entity.java') << """
            package api;
            public interface Entity {}
        """.stripIndent()

        file('api/src/main/java/api/UnusedEntity.java') << """
            package api;
            import lib.LibEntity;
            public class UnusedEntity implements LibEntity {}
        """.stripIndent()

        file('api/build.gradle') << """
            apply plugin: 'java-library'
            repositories { maven { url "${repo.uri}" } }
            dependencies { api project(':lib') }
        """.stripIndent()

        file('impl/src/main/java/impl/SimpleEntity.java') << """
            package impl;
            import api.Entity;
            public class SimpleEntity implements Entity {}
        """.stripIndent()

        file('impl/build.gradle') << """
            apply plugin: 'java-library'
            apply plugin: 'com.github.johnrengelman.shadow'

            shadowJar {
                minimize()
            }

            repositories { maven { url "${repo.uri}" } }
            dependencies { api project(':api') }
        """.stripIndent()

        File serverOutput = getFile('impl/build/libs/impl-all.jar')

        when:
        runWithDebug(':impl:shadowJar')

        then:
        serverOutput.exists()
        contains(serverOutput, [
                'impl/SimpleEntity.class',
                'api/Entity.class',
                'api/UnusedEntity.class',
                'lib/LibEntity.class',
                'lib/UnusedLibEntity.class'
        ])
    }

    def 'depend on project shadow jar'() {
        given:
        file('settings.gradle') << """
            include 'client', 'server'
        """.stripIndent()

        file('client/src/main/java/client/Client.java') << """
            package client;
            public class Client {}
            """.stripIndent()

        file('client/build.gradle') << """
            apply plugin: 'java'
            apply plugin: 'com.github.johnrengelman.shadow'
            repositories { maven { url "${repo.uri}" } }
            dependencies { implementation 'junit:junit:3.8.2' }

            shadowJar {
               relocate 'junit.framework', 'client.junit.framework'
            }
        """.stripIndent()

        file('server/src/main/java/server/Server.java') << """
            package server;

            import client.Client;
            import client.junit.framework.Test;

            public class Server {}
        """.stripIndent()

        file('server/build.gradle') << """
            apply plugin: 'java'

            repositories { maven { url "${repo.uri}" } }
            dependencies { implementation project(path: ':client', configuration: 'shadow') }
        """.stripIndent()

        File serverOutput = getFile('server/build/libs/server.jar')

        when:
        run(':server:jar')

        then:
        serverOutput.exists()
        contains(serverOutput, [
                'server/Server.class'
        ])

        and:
        doesNotContain(serverOutput, [
                'client/Client.class',
                'junit/framework/Test.class',
                'client/junit/framework/Test.class'
        ])
    }

    def 'shadow a project shadow jar'() {
        given:
        file('settings.gradle') << """
            include 'client', 'server'
        """.stripIndent()

        file('client/src/main/java/client/Client.java') << """
            package client;
            public class Client {}
        """.stripIndent()

        file('client/build.gradle') << """
            apply plugin: 'java'
            apply plugin: 'com.github.johnrengelman.shadow'
            repositories { maven { url "${repo.uri}" } }
            dependencies { implementation 'junit:junit:3.8.2' }

            shadowJar {
               relocate 'junit.framework', 'client.junit.framework'
            }
        """.stripIndent()

        file('server/src/main/java/server/Server.java') << """
            package server;

            import client.Client;
            import client.junit.framework.Test;

            public class Server {}
        """.stripIndent()

        file('server/build.gradle') << """
            apply plugin: 'java'
            apply plugin: 'com.github.johnrengelman.shadow'

            repositories { maven { url "${repo.uri}" } }
            dependencies { implementation project(path: ':client', configuration: 'shadow') }
        """.stripIndent()

        File serverOutput = getFile('server/build/libs/server-all.jar')

        when:
        run(':server:shadowJar')

        then:
        serverOutput.exists()
        contains(serverOutput, [
                'client/Client.class',
                'client/junit/framework/Test.class',
                'server/Server.class',
        ])

        and:
        doesNotContain(serverOutput, [
                'junit/framework/Test.class'
        ])
    }

    def "exclude INDEX.LIST, *.SF, *.DSA, and *.RSA by default"() {
        given:
        repo.module('shadow', 'a', '1.0')
                .insertFile('a.properties', 'a')
                .insertFile('META-INF/INDEX.LIST', 'JarIndex-Version: 1.0')
                .insertFile('META-INF/a.SF', 'Signature File')
                .insertFile('META-INF/a.DSA', 'DSA Signature Block')
                .insertFile('META-INF/a.RSA', 'RSA Signature Block')
                .insertFile('META-INF/a.properties', 'key=value')
                .publish()

        file('src/main/java/shadow/Passed.java') << '''
            package shadow;
            public class Passed {}
        '''.stripIndent()

        buildFile << """
            dependencies { implementation 'shadow:a:1.0' }
        """.stripIndent()

        when:
        run('shadowJar')

        then:
        contains(output, ['a.properties', 'META-INF/a.properties'])

        and:
        doesNotContain(output, ['META-INF/INDEX.LIST', 'META-INF/a.SF', 'META-INF/a.DSA', 'META-INF/a.RSA'])
    }

    def "include runtime configuration by default"() {
        given:
        repo.module('shadow', 'a', '1.0')
                .insertFile('a.properties', 'a')
                .publish()

        repo.module('shadow', 'b', '1.0')
                .insertFile('b.properties', 'b')
                .publish()

        buildFile << """
            dependencies {
               runtimeOnly 'shadow:a:1.0'
               shadow 'shadow:b:1.0'
            }
        """.stripIndent()

        when:
        run('shadowJar')

        then:
        contains(output, ['a.properties'])

        and:
        doesNotContain(output, ['b.properties'])
    }

    def "include java-library configurations by default"() {
        given:
        repo.module('shadow', 'api', '1.0')
                .insertFile('api.properties', 'api')
                .publish()

        repo.module('shadow', 'implementation-dep', '1.0')
                .insertFile('implementation-dep.properties', 'implementation-dep')
                .publish()

        repo.module('shadow', 'implementation', '1.0')
                .insertFile('implementation.properties', 'implementation')
                .dependsOn('implementation-dep')
                .publish()

        repo.module('shadow', 'runtimeOnly', '1.0')
                .insertFile('runtimeOnly.properties', 'runtimeOnly')
                .publish()

        buildFile.text = getDefaultBuildScript('java-library')
        buildFile << """
            dependencies {
               api 'shadow:api:1.0'
               implementation 'shadow:implementation:1.0'
               runtimeOnly 'shadow:runtimeOnly:1.0'
            }
        """.stripIndent()

        when:
        runWithDebug('shadowJar')

        then:
        contains(output, ['api.properties', 'implementation.properties',
                          'runtimeOnly.properties', 'implementation-dep.properties'])
    }

    def "doesn't include compileOnly configuration by default"() {
        given:
        repo.module('shadow', 'a', '1.0')
                .insertFile('a.properties', 'a')
                .publish()

        repo.module('shadow', 'b', '1.0')
                .insertFile('b.properties', 'b')
                .publish()

        buildFile << """
            dependencies {
               runtimeOnly 'shadow:a:1.0'
               compileOnly 'shadow:b:1.0'
            }
        """.stripIndent()

        when:
        run('shadowJar')

        then:
        contains(output, ['a.properties'])

        and:
        doesNotContain(output, ['b.properties'])
    }

    def "default copying strategy"() {
        given:
        repo.module('shadow', 'a', '1.0')
                .insertFile('META-INF/MANIFEST.MF', 'MANIFEST A')
                .publish()

        repo.module('shadow', 'b', '1.0')
                .insertFile('META-INF/MANIFEST.MF', 'MANIFEST B')
                .publish()

        buildFile << """
            dependencies {
               runtimeOnly 'shadow:a:1.0'
               runtimeOnly 'shadow:b:1.0'
            }
        """.stripIndent()

        when:
        run('shadowJar')

        then:
        JarFile jar = new JarFile(output)
        assert jar.entries().collect().size() == 2
    }

    def "Class-Path in Manifest not added if empty"() {
        given:

        buildFile << """
            dependencies { implementation 'junit:junit:3.8.2' }
        """.stripIndent()

        when:
        run('shadowJar')

        then:
        assert output.exists()

        and:
        JarFile jar = new JarFile(output)
        Attributes attributes = jar.manifest.getMainAttributes()
        assert attributes.getValue('Class-Path') == null
    }

    @Issue('SHADOW-65')
    def "add shadow configuration to Class-Path in Manifest"() {
        given:

        buildFile << """
            // tag::shadowConfig[]
            dependencies {
              shadow 'junit:junit:3.8.2'
            }
            // end::shadowConfig[]

            // tag::jarManifest[]
            jar {
               manifest {
                   attributes 'Class-Path': '/libs/a.jar'
               }
            }
            // end::jarManifest[]
        """.stripIndent()

        when:
        run('shadowJar')

        then:
        assert output.exists()

        and: 'SHADOW-65 - combine w/ existing Class-Path'
        JarFile jar = new JarFile(output)
        Attributes attributes = jar.manifest.getMainAttributes()
        String classpath = attributes.getValue('Class-Path')
        assert classpath == '/libs/a.jar junit-3.8.2.jar'

    }

    @Issue('SHADOW-92')
    def "do not include null value in Class-Path when jar file does not contain Class-Path"() {
        given:

        buildFile << """
            dependencies { shadow 'junit:junit:3.8.2' }
        """.stripIndent()

        when:
        run('shadowJar')

        then:
        assert output.exists()

        and:
        JarFile jar = new JarFile(output)
        Attributes attributes = jar.manifest.getMainAttributes()
        String classpath = attributes.getValue('Class-Path')
        assert classpath == 'junit-3.8.2.jar'

    }

    @Issue('SHADOW-203')
    def "support ZipCompression.STORED"() {
        given:

        buildFile << """
            dependencies { shadow 'junit:junit:3.8.2' }

            shadowJar {
                zip64 true
                entryCompression = org.gradle.api.tasks.bundling.ZipEntryCompression.STORED
            }
        """.stripIndent()

        when:
        run('shadowJar')

        then:
        assert output.exists()

    }

    def 'api project dependency with version'() {
        given:
        file('settings.gradle') << """
            include 'api', 'lib', 'impl'
        """.stripIndent()

        file('lib/build.gradle') << """
            apply plugin: 'java'
            version = '1.0'
            repositories { maven { url "${repo.uri}" } }
        """.stripIndent()

        file('api/src/main/java/api/UnusedEntity.java') << """
            package api;
            public class UnusedEntity {}
        """.stripIndent()

        file('api/build.gradle') << """
            apply plugin: 'java'
            version = '1.0'
            repositories { maven { url "${repo.uri}" } }
            dependencies {
                implementation 'junit:junit:3.8.2'
                implementation project(':lib')
            }
        """.stripIndent()

        file('impl/build.gradle') << """
            apply plugin: 'java-library'
            apply plugin: 'com.github.johnrengelman.shadow'

            version = '1.0'
            repositories { maven { url "${repo.uri}" } }
            dependencies { api project(':api') }
            
            shadowJar.minimize()
        """.stripIndent()

        File serverOutput = getFile('impl/build/libs/impl-1.0-all.jar')

        when:
        runWithDebug(':impl:shadowJar')

        then:
        serverOutput.exists()
        contains(serverOutput, [
                'api/UnusedEntity.class',
        ])
    }

    @Issue('SHADOW-143')
    @Ignore("This spec requires > 15 minutes and > 8GB of disk space to run")
    def "check large zip files with zip64 enabled"() {
        given:
        repo.module('shadow', 'a', '1.0')
                .insertFile('a.properties', 'a')
                .insertFile('a2.properties', 'a2')
                .publish()

        file('src/main/java/myapp/Main.java') << """
            package myapp;
            public class Main {
               public static void main(String[] args) {
                   System.out.println("TestApp: Hello World! (" + args[0] + ")");
               }
            }
        """.stripIndent()

        buildFile << """
            apply plugin: 'application'

            application {
               mainClass = 'myapp.Main'
            }

            dependencies {
               implementation 'shadow:a:1.0'
            }

            def generatedResourcesDir = new File(project.layout.buildDirectory.asFile.get(), "generated-resources")

            task generateResources {
                doLast {
                    def rnd = new Random()
                    def buf = new byte[128 * 1024]
                    for (x in 0..255) {
                        def dir = new File(generatedResourcesDir, x.toString())
                        dir.mkdirs()
                        for (y in 0..255) {
                            def file = new File(dir, y.toString())
                            rnd.nextBytes(buf)
                            file.bytes = buf
                        }
                    }
                }
            }

            sourceSets {
                main {
                    output.dir(generatedResourcesDir, builtBy: generateResources)
                }
            }

            shadowJar {
                zip64 = true
            }

            runShadow {
               args 'foo'
            }
        """.stripIndent()

        settingsFile << "rootProject.name = 'myapp'"

        when:
        BuildResult result = run('runShadow')

        then: 'tests that runShadow executed and exited'
        assert result.output.contains('TestApp: Hello World! (foo)')


    }

    @Issue("SHADOW-303")
    @Ignore("Plugin has been deprecated")
    def "doesn't error when adding aspectj plugin"() {
        given:
        buildFile.text = """
        buildscript {
            repositories {
                maven {
                    url "https://maven.eveoh.nl/content/repositories/releases"
                }
            }

            dependencies {
                classpath "nl.eveoh:gradle-aspectj:2.0"
            }
        }
        """.stripIndent()

        buildFile << defaultBuildScript

        buildFile << """
            project.ext {
                aspectjVersion = '1.8.12'
            }

            apply plugin: 'aspectj'
            apply plugin: 'application'

            application {
               mainClass = 'myapp.Main'
            }

            repositories {
                mavenCentral()
            }

            runShadow {
               args 'foo'
            }

        """

        file('src/main/java/myapp/Main.java') << """
            package myapp;
            public class Main {
               public static void main(String[] args) {
                   System.out.println("TestApp: Hello World! (" + args[0] + ")");
               }
            }
        """.stripIndent()

        when:
        BuildResult result = run('runShadow')

        then: 'tests that runShadow executed and exited'
        assert result.output.contains('TestApp: Hello World! (foo)')
    }

    @Issue("https://github.com/johnrengelman/shadow/issues/609")
    def "doesn't error when using application mainClass property"() {
        given:
        buildFile.text = defaultBuildScript

        buildFile << """
            project.ext {
                aspectjVersion = '1.8.12'
            }

            apply plugin: 'application'

            application {
                mainClass.set('myapp.Main')
            }

            repositories {
                mavenCentral()
            }

            runShadow {
               args 'foo'
            }

        """

        file('src/main/java/myapp/Main.java') << """
            package myapp;
            public class Main {
               public static void main(String[] args) {
                   System.out.println("TestApp: Hello World! (" + args[0] + ")");
               }
            }
        """.stripIndent()

        when:
        BuildResult result = run('runShadow')

        then: 'tests that runShadow executed and exited'
        assert result.output.contains('TestApp: Hello World! (foo)')
    }

    private String escapedPath(File file) {
        file.path.replaceAll('\\\\', '\\\\\\\\')
    }
}
