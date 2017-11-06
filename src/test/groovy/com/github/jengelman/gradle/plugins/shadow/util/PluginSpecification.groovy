package com.github.jengelman.gradle.plugins.shadow.util

import com.github.jengelman.gradle.plugins.shadow.util.file.TestFile
import com.google.common.base.StandardSystemProperty
import org.apache.tools.zip.ZipEntry
import org.apache.tools.zip.ZipFile
import org.codehaus.plexus.util.IOUtil
import org.gradle.testkit.runner.GradleRunner
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

import java.util.jar.JarEntry
import java.util.jar.JarFile

class PluginSpecification extends Specification {

    @Rule TemporaryFolder dir

    private static final String SHADOW_VERSION = PluginSpecification.classLoader.getResource("shadow-version.txt").text.trim()

    AppendableMavenFileRepository repo

    def setup() {
        repo = repo()
        repo.module('junit', 'junit', '3.8.2').use(testJar).publish()

        buildFile << defaultBuildScript

        settingsFile << '''
            rootProject.name = 'shadow'
        '''
    }

    def cleanup() {
        println buildFile.text
    }

    String getDefaultBuildScript() {
        return """
        plugins {
            id 'java'
            id 'com.github.johnrengelman.shadow'
        }

        version = "1.0"
        group = 'shadow'

        sourceSets {
          integTest
        }

        repositories { maven { url "${repo.uri}" } }
        """.stripIndent()
    }

    GradleRunner getRunner() {
        GradleRunner.create()
                .withProjectDir(dir.root)
                .forwardOutput()
                .withPluginClasspath()
    }

    File getLocalRepo() {
        def rootRelative = new File("build/localrepo")
        rootRelative.directory ? rootRelative : new File(new File(StandardSystemProperty.USER_DIR.value()).parentFile, "build/localrepo")
    }

    File getBuildFile() {
        file('build.gradle')
    }

    File getSettingsFile() {
        file('settings.gradle')
    }

    File file(String path) {
        File f = new File(dir.root, path)
        if (!f.exists()) {
            f.parentFile.mkdirs()
            return dir.newFile(path)
        }
        return f
    }

    AppendableMavenFileRepository repo(String path = 'maven-repo') {
        new AppendableMavenFileRepository(new TestFile(dir.root, path))
    }

    void assertJarFileContentsEqual(File f, String path, String contents) {
        assert getJarFileContents(f, path) == contents
    }

    String getJarFileContents(File f, String path) {
        JarFile jf = new JarFile(f)
        def is = jf.getInputStream(new JarEntry(path))
        StringWriter sw = new StringWriter()
        IOUtil.copy(is, sw)
        is.close()
        jf.close()
        return sw.toString()
    }

    List<ZipEntry> getZipEntries(File f) {
        new ZipFile(f).entries.toList()
    }

    void contains(File f, List<String> paths) {
        JarFile jar = new JarFile(f)
        paths.each { path ->
            assert jar.getJarEntry(path), "${f.path} does not contain [$path]"
        }
        jar.close()
    }

    void doesNotContain(File f, List<String> paths) {
        JarFile jar = new JarFile(f)
        paths.each { path ->
            assert !jar.getJarEntry(path), "${f.path} contains [$path]"
        }
        jar.close()
    }

    AppendableJar buildJar(String path) {
        return new AppendableJar(file(path))
    }

    protected getOutput() {
        file('build/libs/shadow-1.0-all.jar')
    }

    protected output(String name) {
        file("build/libs/${name}")
    }

    protected File getTestJar(String name = 'junit-3.8.2.jar') {
        return new File(this.class.classLoader.getResource(name).toURI())
    }

    public static File getTestKitDir() {
        def gradleUserHome = System.getenv("GRADLE_USER_HOME")
        if (!gradleUserHome) {
            gradleUserHome = new File(System.getProperty("user.home"), ".gradle").absolutePath
        }
        return new File(gradleUserHome, "testkit")
    }
}
