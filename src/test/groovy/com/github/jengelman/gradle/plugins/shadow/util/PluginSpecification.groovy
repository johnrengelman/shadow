package com.github.jengelman.gradle.plugins.shadow.util

import com.github.jengelman.gradle.plugins.shadow.util.file.TestFile
import org.codehaus.plexus.util.IOUtil
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

import java.util.function.Function
import java.util.jar.JarEntry
import java.util.jar.JarFile

abstract class PluginSpecification extends Specification {

    @Rule TemporaryFolder dir

    public static final String SHADOW_VERSION = PluginSpecification.classLoader.getResource("shadow-version.txt").text.trim()

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

    String getDefaultBuildScript(String javaPlugin = 'java') {
        return """
        plugins {
            id '${javaPlugin}'
            id 'com.github.johnrengelman.shadow' version '${SHADOW_VERSION}'
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

    GradleRunner runner(Collection<String> tasks) {
        runner.withArguments(["-Dorg.gradle.warning.mode=all", "--configuration-cache", "--stacktrace"] + tasks.toList())
    }

    BuildResult run(String... tasks) {
        run(tasks.toList())
    }

    BuildResult run(List<String> tasks, Function<GradleRunner, GradleRunner> runnerFunction = { it }) {
        def result = runnerFunction.apply(runner(tasks)).build()
        assertNoDeprecationWarnings(result)
        return result
    }

    BuildResult runWithDebug(String... tasks) {
        return run(tasks.toList(), { it.withDebug(true) })
    }

    BuildResult runWithFailure(List<String> tasks, Function<GradleRunner, GradleRunner> runnerFunction = { it }) {
        def result = runnerFunction.apply(runner(tasks)).buildAndFail()
        assertNoDeprecationWarnings(result)
        return result
    }

    static void assertNoDeprecationWarnings(BuildResult result) {
        result.output.eachLine {
            assert !containsDeprecationWarning(it)
        }
    }

    static boolean containsDeprecationWarning(String output) {
        output.contains("has been deprecated and is scheduled to be removed in Gradle") ||
                output.contains("has been deprecated. This is scheduled to be removed in Gradle")
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

    File getFile(String path) {
        new File(dir.root, path)
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

    protected File getOutput() {
        getFile('build/libs/shadow-1.0-all.jar')
    }

    protected File output(String name) {
        getFile("build/libs/${name}")
    }

    protected File getTestJar(String name = 'junit-3.8.2.jar') {
        return new File(this.class.classLoader.getResource(name).toURI())
    }

    static File getTestKitDir() {
        def gradleUserHome = System.getenv("GRADLE_USER_HOME")
        if (!gradleUserHome) {
            gradleUserHome = new File(System.getProperty("user.home"), ".gradle").absolutePath
        }
        return new File(gradleUserHome, "testkit")
    }
}
