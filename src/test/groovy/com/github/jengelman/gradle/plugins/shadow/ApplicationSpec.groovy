package com.github.jengelman.gradle.plugins.shadow

import com.github.jengelman.gradle.plugins.shadow.util.PluginSpecification
import org.apache.tools.zip.ZipFile
import org.gradle.testkit.runner.BuildResult
import spock.lang.Issue

import java.util.jar.Attributes
import java.util.jar.JarFile

class ApplicationSpec extends PluginSpecification {

    def 'integration with application plugin'() {
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
            
            runShadow {
               args 'foo'
            }
        """.stripIndent()

        settingsFile << "rootProject.name = 'myapp'"

        when:
        BuildResult result = run('runShadow')

        then: 'tests that runShadow executed and exited'
        assert result.output.contains('TestApp: Hello World! (foo)')

        and: 'Check that the proper jar file was installed'
        File installedJar = getFile('build/install/myapp-shadow/lib/myapp-1.0-all.jar')
        assert installedJar.exists()

        and: 'And that jar file as the correct files in it'
        contains(installedJar, ['a.properties', 'a2.properties', 'myapp/Main.class'])

        and: 'Check the manifest attributes in the jar file are correct'
        JarFile jar = new JarFile(installedJar)
        Attributes attributes = jar.manifest.mainAttributes
        assert attributes.getValue('Main-Class') == 'myapp.Main'

        then: 'Check that the start scripts is written out and has the correct Java invocation'
        File startScript = getFile('build/install/myapp-shadow/bin/myapp')
        assert startScript.exists()
        assert startScript.text.contains("CLASSPATH=\$APP_HOME/lib/myapp-1.0-all.jar")
        assert startScript.text.contains("-jar \"\\\"\$CLASSPATH\\\"\" \"\$APP_ARGS\"")
        assert startScript.text.contains("exec \"\$JAVACMD\" \"\$@\"")

        cleanup:
        jar?.close()
    }

    def 'integration with application plugin and java toolchains'() {
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
            
            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(17)
                }
            }
            
            runShadow {
               args 'foo'
               doFirst {
                   logger.lifecycle("Running application with JDK \${it.javaLauncher.get().metadata.languageVersion.asInt()}")
               }
            }          
        """.stripIndent()

        settingsFile.write """ 
            plugins {
              // https://docs.gradle.org/8.0.1/userguide/toolchains.html#sub:download_repositories
              id("org.gradle.toolchains.foojay-resolver-convention") version("0.7.0")
            }
            
            rootProject.name = 'myapp'
        """.stripIndent()

        when:
        BuildResult result = run('runShadow')

        then: 'tests that runShadow executed and exited'
        assert result.output.contains('Running application with JDK 17')
        assert result.output.contains('TestApp: Hello World! (foo)')

        and: 'Check that the proper jar file was installed'
        File installedJar = getFile('build/install/myapp-shadow/lib/myapp-1.0-all.jar')
        assert installedJar.exists()

        and: 'And that jar file as the correct files in it'
        contains(installedJar, ['a.properties', 'a2.properties', 'myapp/Main.class'])

        and: 'Check the manifest attributes in the jar file are correct'
        JarFile jar = new JarFile(installedJar)
        Attributes attributes = jar.manifest.mainAttributes
        assert attributes.getValue('Main-Class') == 'myapp.Main'

        then: 'Check that the start scripts is written out and has the correct Java invocation'
        File startScript = getFile('build/install/myapp-shadow/bin/myapp')
        assert startScript.exists()
        assert startScript.text.contains("CLASSPATH=\$APP_HOME/lib/myapp-1.0-all.jar")
        assert startScript.text.contains("-jar \"\\\"\$CLASSPATH\\\"\" \"\$APP_ARGS\"")
        assert startScript.text.contains("exec \"\$JAVACMD\" \"\$@\"")

        cleanup:
        jar?.close()
    }

    @Issue('SHADOW-89')
    def 'shadow application distributions should use shadow jar'() {
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
               shadow 'shadow:a:1.0'
            }
            
            runShadow {
               args 'foo'
            }
        """.stripIndent()

        settingsFile << "rootProject.name = 'myapp'"

        when:
        run('shadowDistZip')

        then: 'Check that the distribution zip was created'
        File zip = getFile('build/distributions/myapp-shadow-1.0.zip')
        assert zip.exists()

        and: 'Check that the zip contains the correct library files & scripts'
        ZipFile zipFile = new ZipFile(zip)
        println zipFile.entries.collect { it.name }
        assert zipFile.entries.find { it.name == 'myapp-shadow-1.0/lib/myapp-1.0-all.jar' }
        assert zipFile.entries.find { it.name == 'myapp-shadow-1.0/lib/a-1.0.jar' }

        cleanup:
        zipFile?.close()
    }

    @Issue('SHADOW-90')
    def 'installShadow does not execute dependent shadow task'() {
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
            
            runShadow {
               args 'foo'
            }
        """.stripIndent()

        settingsFile << "rootProject.name = 'myapp'"

        when:
        run(ShadowApplicationPlugin.SHADOW_INSTALL_TASK_NAME)

        then: 'Check that the proper jar file was installed'
        File installedJar = getFile('build/install/myapp-shadow/lib/myapp-1.0-all.jar')
        assert installedJar.exists()
    }
}
