package com.github.jengelman.gradle.plugins.shadow
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.github.jengelman.gradle.plugins.shadow.transformers.*
import com.github.jengelman.gradle.plugins.shadow.util.PluginSpecification
import org.gradle.testkit.functional.ExecutionResult
import spock.lang.Issue

import java.util.jar.JarFile
import java.util.jar.JarInputStream
import java.util.jar.Manifest

class TransformerSpec extends PluginSpecification {

    def 'service resource transformer'() {
        given:
        File one = buildJar('one.jar')
                .insertFile('META-INF/services/org.apache.maven.Shade',
                        'one # NOTE: No newline terminates this line/file')
                .insertFile('META-INF/services/com.acme.Foo', 'one')
                .write()

        File two = buildJar('two.jar')
                .insertFile('META-INF/services/org.apache.maven.Shade',
                        'two # NOTE: No newline terminates this line/file')
                .insertFile('META-INF/services/com.acme.Foo', 'two')
                .write()

        buildFile << """
            |task shadow(type: ${ShadowJar.name}) {
            |    destinationDir = new File(buildDir, 'libs')
            |    baseName = 'shadow'
            |    from('${escapedPath(one)}')
            |    from('${escapedPath(two)}')
            |    transform(${ServiceFileTransformer.name}) {
            |        exclude 'META-INF/services/com.acme.*'
            |    }
            |}
        """.stripMargin()

        when:
        runner.arguments << 'shadow'
        ExecutionResult result = runner.run()

        then:
        success(result)
        assert output.exists()

        and:
        String text1 = getJarFileContents(output, 'META-INF/services/org.apache.maven.Shade')
        assert text1.split('(\r\n)|(\r)|(\n)').size() == 2
        assert text1 == '''|one # NOTE: No newline terminates this line/file
                          |two # NOTE: No newline terminates this line/file'''.stripMargin()

        and:
        String text2 = getJarFileContents(output, 'META-INF/services/com.acme.Foo')
        assert text2.split('(\r\n)|(\r)|(\n)').size() == 1
        assert text2 == 'one'
    }

    def 'service resource transformer alternate path'() {
        given:
            File one = buildJar('one.jar').insertFile('META-INF/foo/org.apache.maven.Shade',
                    'one # NOTE: No newline terminates this line/file').write()

            File two = buildJar('two.jar').insertFile('META-INF/foo/org.apache.maven.Shade',
                    'two # NOTE: No newline terminates this line/file').write()

            buildFile << """
            |task shadow(type: ${ShadowJar.name}) {
            |    destinationDir = new File(buildDir, 'libs')
            |    baseName = 'shadow'
            |    from('${escapedPath(one)}')
            |    from('${escapedPath(two)}')
            |    transform(${ServiceFileTransformer.name}) {
            |        path = 'META-INF/foo'
            |    }
            |}
        """.stripMargin()

        when:
            runner.arguments << 'shadow'
            ExecutionResult result = runner.run()

        then:
            success(result)
            assert output.exists()

        and:
            String text = getJarFileContents(output, 'META-INF/foo/org.apache.maven.Shade')
            assert text.split('(\r\n)|(\r)|(\n)').size() == 2
            assert text == '''|one # NOTE: No newline terminates this line/file
                          |two # NOTE: No newline terminates this line/file'''.stripMargin()
    }

    def 'service resource transformer short syntax'() {
        given:
            File one = buildJar('one.jar')
                    .insertFile('META-INF/services/org.apache.maven.Shade',
                    'one # NOTE: No newline terminates this line/file')
                    .insertFile('META-INF/services/com.acme.Foo', 'one')
                    .write()

            File two = buildJar('two.jar')
                    .insertFile('META-INF/services/org.apache.maven.Shade',
                    'two # NOTE: No newline terminates this line/file')
                    .insertFile('META-INF/services/com.acme.Foo', 'two')
                    .write()

        buildFile << """
            |task shadow(type: ${ShadowJar.name}) {
            |    destinationDir = new File(buildDir, 'libs')
            |    baseName = 'shadow'
            |    from('${escapedPath(one)}')
            |    from('${escapedPath(two)}')
            |    mergeServiceFiles {
            |        exclude 'META-INF/services/com.acme.*'
            |    }
            |}
        """.stripMargin()

        when:
        runner.arguments << 'shadow'
        ExecutionResult result = runner.run()

        then:
        success(result)
        assert output.exists()

        and:
            String text1 = getJarFileContents(output, 'META-INF/services/org.apache.maven.Shade')
            assert text1.split('(\r\n)|(\r)|(\n)').size() == 2
            assert text1 == '''|one # NOTE: No newline terminates this line/file
                          |two # NOTE: No newline terminates this line/file'''.stripMargin()

        and:
            String text2 = getJarFileContents(output, 'META-INF/services/com.acme.Foo')
            assert text2.split('(\r\n)|(\r)|(\n)').size() == 1
            assert text2 == 'one'
    }

    def 'service resource transformer short syntax alternate path'() {
        given:
            File one = buildJar('one.jar').insertFile('META-INF/foo/org.apache.maven.Shade',
                    'one # NOTE: No newline terminates this line/file').write()

            File two = buildJar('two.jar').insertFile('META-INF/foo/org.apache.maven.Shade',
                    'two # NOTE: No newline terminates this line/file').write()

            buildFile << """
            |task shadow(type: ${ShadowJar.name}) {
            |    destinationDir = new File(buildDir, 'libs')
            |    baseName = 'shadow'
            |    from('${escapedPath(one)}')
            |    from('${escapedPath(two)}')
            |    mergeServiceFiles('META-INF/foo')
            |}
        """.stripMargin()

        when:
            runner.arguments << 'shadow'
            ExecutionResult result = runner.run()

        then:
            success(result)
            assert output.exists()

        and:
            String text = getJarFileContents(output, 'META-INF/foo/org.apache.maven.Shade')
            assert text.split('(\r\n)|(\r)|(\n)').size() == 2
            assert text == '''|one # NOTE: No newline terminates this line/file
                          |two # NOTE: No newline terminates this line/file'''.stripMargin()
    }

    @Issue(['SHADOW-70', 'SHADOW-71'])
    def 'apply transformers to project resources'() {
        given:
        File one = buildJar('one.jar').insertFile('META-INF/services/shadow.Shadow',
                'one # NOTE: No newline terminates this line/file').write()

        repo.module('shadow', 'two', '1.0').insertFile('META-INF/services/shadow.Shadow',
                'two # NOTE: No newline terminates this line/file').publish()

        buildFile << """
            |apply plugin: 'java'
            |apply plugin: ${ShadowPlugin.name}
            |
            |repositories { maven { url "${repo.uri}" } }
            |dependencies {
            |  compile 'shadow:two:1.0'
            |  compile files('${escapedPath(one)}')
            |}
            |
            |shadowJar {
            |  baseName = 'shadow'
            |  classifier = null
            |  mergeServiceFiles()
            |}
        """.stripMargin()

        file('src/main/resources/META-INF/services/shadow.Shadow') <<
                'three # NOTE: No newline terminates this line/file'

        when:
        runner.arguments << 'shadowJar'
        ExecutionResult result = runner.run()

        then:
        success(result)
        assert output.exists()

        and:
        String text = getJarFileContents(output, 'META-INF/services/shadow.Shadow')
        assert text.split('(\r\n)|(\r)|(\n)').size() == 3
        assert text == '''|three # NOTE: No newline terminates this line/file
                          |one # NOTE: No newline terminates this line/file
                          |two # NOTE: No newline terminates this line/file'''.stripMargin()
    }

    def 'appending transformer'() {
        given:
        File one = buildJar('one.jar').insertFile('test.properties',
                'one # NOTE: No newline terminates this line/file').write()

        File two = buildJar('two.jar').insertFile('test.properties',
                'two # NOTE: No newline terminates this line/file').write()

        buildFile << """
            |task shadow(type: ${ShadowJar.name}) {
            |    destinationDir = new File(buildDir, 'libs')
            |    baseName = 'shadow'
            |    from('${escapedPath(one)}')
            |    from('${escapedPath(two)}')
            |    transform(${AppendingTransformer.name}) {
            |        resource = 'test.properties'
            |    }
            |}
        """.stripMargin()

        when:
        runner.arguments << 'shadow'
        ExecutionResult result = runner.run()

        then:
        success(result)
        assert output.exists()

        and:
        String text = getJarFileContents(output, 'test.properties')
        assert text.split('(\r\n)|(\r)|(\n)').size() == 2
        assert text == '''|one # NOTE: No newline terminates this line/file
                          |two # NOTE: No newline terminates this line/file
                          |'''.stripMargin()
    }

    def 'appending transformer short syntax'() {
        given:
        File one = buildJar('one.jar').insertFile('test.properties',
                'one # NOTE: No newline terminates this line/file').write()

        File two = buildJar('two.jar').insertFile('test.properties',
                'two # NOTE: No newline terminates this line/file').write()

        buildFile << """
            |task shadow(type: ${ShadowJar.name}) {
            |    destinationDir = new File(buildDir, 'libs')
            |    baseName = 'shadow'
            |    from('${escapedPath(one)}')
            |    from('${escapedPath(two)}')
            |    append('test.properties')
            |}
        """.stripMargin()

        when:
        runner.arguments << 'shadow'
        ExecutionResult result = runner.run()

        then:
        success(result)
        assert output.exists()

        and:
        String text = getJarFileContents(output, 'test.properties')
        assert text.split('(\r\n)|(\r)|(\n)').size() == 2
        assert text == '''|one # NOTE: No newline terminates this line/file
                          |two # NOTE: No newline terminates this line/file
                          |'''.stripMargin()
    }

    def 'manifest retained'() {
        given:
        File main = file('src/main/java/shadow/Main.java')
        main << '''
            |package shadow;
            |
            |public class Main {
            |
            |   public static void main(String[] args) { }
            |}
        '''.stripMargin()

        buildFile << """
            |apply plugin: 'java'
            |apply plugin: ${ShadowPlugin.name}
            |
            |jar {
            |   manifest {
            |       attributes 'Main-Class': 'shadow.Main'
            |       attributes 'Test-Entry': 'PASSED'
            |   }
            |}
            |
            |shadowJar {
            |   baseName = 'shadow'
            |   classifier = null
            |}
        """.stripMargin()

        when:
        runner.arguments << 'shadowJar'
        ExecutionResult result = runner.run()

        then:
        success(result)
        assert output.exists()

        and:
        JarInputStream jis = new JarInputStream(output.newInputStream())
        Manifest mf = jis.manifest
        jis.close()

        assert mf
        assert mf.mainAttributes.getValue('Test-Entry') == 'PASSED'
        assert mf.mainAttributes.getValue('Main-Class') == 'shadow.Main'
    }

    def 'manifest transformed'() {
        given:
        File main = file('src/main/java/shadow/Main.java')
        main << '''
            |package shadow;
            |
            |public class Main {
            |
            |   public static void main(String[] args) { }
            |}
        '''.stripMargin()

        buildFile << """
            |apply plugin: 'java'
            |apply plugin: ${ShadowPlugin.name}
            |
            |jar {
            |   manifest {
            |       attributes 'Main-Class': 'shadow.Main'
            |       attributes 'Test-Entry': 'FAILED'
            |   }
            |}
            |
            |shadowJar {
            |   baseName = 'shadow'
            |   classifier = null
            |   manifest {
            |       attributes 'Test-Entry': 'PASSED'
            |       attributes 'New-Entry': 'NEW'
            |   }
            |}
        """.stripMargin()

        when:
        runner.arguments << 'shadowJar'
        ExecutionResult result = runner.run()

        then:
        success(result)
        assert output.exists()

        and:
        JarInputStream jis = new JarInputStream(output.newInputStream())
        Manifest mf = jis.manifest
        jis.close()

        assert mf
        assert mf.mainAttributes.getValue('Test-Entry') == 'PASSED'
        assert mf.mainAttributes.getValue('Main-Class') == 'shadow.Main'
        assert mf.mainAttributes.getValue('New-Entry') == 'NEW'
    }

    def 'append xml files'() {
        given:
        File xml1 = buildJar('xml1.jar').insertFile('properties.xml', '''|<!DOCTYPE properties SYSTEM "http://java.sun.com/dtd/properties.dtd">
            |
            |<properties version="1.0">
            |   <entry key="key1">val1</entry>
            |</properties>
            |'''.stripMargin()
        ).write()

        File xml2 = buildJar('xml2.jar').insertFile('properties.xml', '''|<!DOCTYPE properties SYSTEM "http://java.sun.com/dtd/properties.dtd">
            |
            |<properties version="1.0">
            |   <entry key="key2">val2</entry>
            |</properties>
            |'''.stripMargin()
        ).write()

        buildFile << """
            |task shadow(type: ${ShadowJar.name}) {
            |   destinationDir = new File(buildDir, 'libs')
            |   baseName = 'shadow'
            |   from('${escapedPath(xml1)}')
            |   from('${escapedPath(xml2)}')
            |   transform(${XmlAppendingTransformer.name}) {
            |       resource = 'properties.xml'
            |   }
            |}
        """.stripMargin()

        when:
        runner.arguments << 'shadow'
        ExecutionResult result = runner.run()

        then:
        success(result)
        assert output.exists()

        and:
        String text = getJarFileContents(output, 'properties.xml')
        assert text.replaceAll('\r\n', '\n') == '''|<?xml version="1.0" encoding="UTF-8"?>
            |<!DOCTYPE properties SYSTEM "http://java.sun.com/dtd/properties.dtd">
            |<properties version="1.0">
            |  <entry key="key1">val1</entry>
            |  <entry key="key2">val2</entry>
            |</properties>
            |'''.stripMargin()
    }

    @Issue('SHADOW-82')
    def 'shadow.manifest leaks to jar.manifest'() {
        given:
        File main = file('src/main/java/shadow/Main.java')
        main << '''
            |package shadow;
            |
            |public class Main {
            |
            |   public static void main(String[] args) { }
            |}
        '''.stripMargin()

        buildFile << """
            |apply plugin: 'java'
            |apply plugin: ${ShadowPlugin.name}
            |
            |jar {
            |   baseName = 'jar'
            |   manifest {
            |       attributes 'Main-Class': 'shadow.Main'
            |       attributes 'Test-Entry': 'FAILED'
            |   }
            |}
            |
            |shadowJar {
            |   baseName = 'shadow'
            |   classifier = null
            |   manifest {
            |       attributes 'Test-Entry': 'PASSED'
            |       attributes 'New-Entry': 'NEW'
            |   }
            |}
        """.stripMargin()

        when:
        runner.arguments << 'jar'
        runner.arguments << 'shadowJar'
        ExecutionResult result = runner.run()

        then:
        success(result)
        File jar = file('build/libs/jar.jar')
        assert jar.exists()
        assert output.exists()

        then: 'Check contents of Shadow jar manifest'
        JarInputStream jis = new JarInputStream(output.newInputStream())
        Manifest mf = jis.manifest

        assert mf
        assert mf.mainAttributes.getValue('Test-Entry') == 'PASSED'
        assert mf.mainAttributes.getValue('Main-Class') == 'shadow.Main'
        assert mf.mainAttributes.getValue('New-Entry') == 'NEW'

        then: 'Check contents of jar manifest'
        JarInputStream jis2 = new JarInputStream(jar.newInputStream())
        Manifest mf2 = jis2.manifest

        assert mf2
        assert mf2.mainAttributes.getValue('Test-Entry') == 'FAILED'
        assert mf2.mainAttributes.getValue('Main-Class') == 'shadow.Main'
        assert !mf2.mainAttributes.getValue('New-Entry')

        cleanup:
        jis?.close()
        jis2?.close()
    }

    @Issue('SHADOW-82')
    def 'shadow manifest leaks to jar manifest'() {
        given:
        File main = file('src/main/java/shadow/Main.java')
        main << '''
            |package shadow;
            |
            |public class Main {
            |
            |   public static void main(String[] args) { }
            |}
        '''.stripMargin()

        buildFile << """
            |apply plugin: 'java'
            |apply plugin: ${ShadowPlugin.name}
            |
            |jar {
            |   baseName = 'jar'
            |   manifest {
            |       attributes 'Main-Class': 'shadow.Main'
            |       attributes 'Test-Entry': 'FAILED'
            |   }
            |}
            |
            |shadowJar {
            |   baseName = 'shadow'
            |   classifier = null
            |   manifest {
            |       attributes 'Test-Entry': 'PASSED'
            |       attributes 'New-Entry': 'NEW'
            |   }
            |}
        """.stripMargin()

        when:
        runner.arguments << 'jar'
        runner.arguments << 'shadowJar'
        ExecutionResult result = runner.run()

        then:
        success(result)
        File jar = file('build/libs/jar.jar')
        assert jar.exists()
        assert output.exists()

        then: 'Check contents of Shadow jar manifest'
        JarInputStream jis = new JarInputStream(output.newInputStream())
        Manifest mf = jis.manifest

        assert mf
        assert mf.mainAttributes.getValue('Test-Entry') == 'PASSED'
        assert mf.mainAttributes.getValue('Main-Class') == 'shadow.Main'
        assert mf.mainAttributes.getValue('New-Entry') == 'NEW'

        then: 'Check contents of jar manifest'
        JarInputStream jis2 = new JarInputStream(jar.newInputStream())
        Manifest mf2 = jis2.manifest

        assert mf2
        assert mf2.mainAttributes.getValue('Test-Entry') == 'FAILED'
        assert mf2.mainAttributes.getValue('Main-Class') == 'shadow.Main'
        assert !mf2.mainAttributes.getValue('New-Entry')

        cleanup:
        jis?.close()
        jis2?.close()
    }
    
    def 'Groovy extension module transformer'() {
        given:
            def one = buildJar('one.jar')
                    .insertFile('META-INF/services/org.codehaus.groovy.runtime.ExtensionModule',
                    '''|moduleName=foo
                       |moduleVersion=1.0.5
                       |extensionClasses=com.acme.foo.FooExtension,com.acme.foo.BarExtension
                       |staticExtensionClasses=com.acme.foo.FooStaticExtension'''.stripMargin())
                    .write()

            def two = buildJar('two.jar')
                    .insertFile('META-INF/services/org.codehaus.groovy.runtime.ExtensionModule',
                    '''|moduleName=bar
                       |moduleVersion=2.3.5
                       |extensionClasses=com.acme.bar.SomeExtension,com.acme.bar.AnotherExtension
                       |staticExtensionClasses=com.acme.bar.SomeStaticExtension'''.stripMargin())
                    .write()

            buildFile << """
                |task shadow(type: ${ShadowJar.name}) {
                |    destinationDir = new File(buildDir, 'libs')
                |    baseName = 'shadow'
                |    from('${escapedPath(one)}')
                |    from('${escapedPath(two)}')
                |    transform(${GroovyExtensionModuleTransformer.name})
                |}
            """.stripMargin()

        when:
            runner.arguments << 'shadow'
            def result = runner.run()

        then:
            success(result)
            assert output.exists()

        and:
            def text = getJarFileContents(output, 'META-INF/services/org.codehaus.groovy.runtime.ExtensionModule')
            def props = new Properties()
            props.load(new StringReader(text))
            assert props.getProperty('moduleName') == 'MergedByShadowJar'
            assert props.getProperty('moduleVersion') == '1.0.0'
            assert props.getProperty('extensionClasses') == 'com.acme.foo.FooExtension,com.acme.foo.BarExtension,com.acme.bar.SomeExtension,com.acme.bar.AnotherExtension'
            assert props.getProperty('staticExtensionClasses') == 'com.acme.foo.FooStaticExtension,com.acme.bar.SomeStaticExtension'
    }

    def 'same file transformer'() {
        given:
        def one = buildJar('one.jar')
                .insertFile('fileInJar.txt', 'This is file one content')
                .insertFile('toExclude', 'First content to exclude')
                .write()

        def two = buildJar('two.jar')
                .insertFile('fileInJar.txt', 'This is file two content')
                .insertFile('toExclude', 'Second content to exclude')
                .write()

        buildFile << """
                |task shadow(type: ${ShadowJar.name}) {
                |    destinationDir = new File(buildDir, 'libs')
                |    baseName = 'shadow'
                |    from('${escapedPath(one)}')
                |    from('${escapedPath(two)}')
                |    transform(${SamePathFilesTransformer.name}) {
                |        exclude 'toExclude'
                |    }
                |}
            """.stripMargin()

        when:
        runner.arguments << 'shadow'
        def result = runner.run()

        JarFile jf = new JarFile(output)
        def paths = ""
        jf.entries().each {
            paths += "\n$it"
        }

        then:
        success(result)
        assert output.exists()

        and:
        doesNotContain(output, ['fileInJar'])
        doesNotContain(output, ['toExclude_one', 'toExclude_two'])
        contains(output, ['toExclude'])

        and:
        def text = getJarFileContents(output, 'fileInJar_one.txt')
        def text2 = getJarFileContents(output, 'fileInJar_two.txt')
        def text3 = getJarFileContents(output, 'toExclude')
        assert text == "This is file one content"
        assert text2 == "This is file two content"
        assert text3 == "First content to exclude" || text3 == "Second content to exclude"
    }

    def 'Groovy same file transformer for file without extension'() {
        given:
        def one = buildJar('one.jar')
                .insertFile('fileInJar',
                            'This is file one content'.stripMargin())
                .write()

        def two = buildJar('two.jar')
                .insertFile('fileInJar',
                            'This is file two content'.stripMargin())
                .write()

        buildFile << """
                |task shadow(type: ${ShadowJar.name}) {
                |    destinationDir = new File(buildDir, 'libs')
                |    baseName = 'shadow'
                |    from('${escapedPath(one)}')
                |    from('${escapedPath(two)}')
                |    transform(${SamePathFilesTransformer.name})
                |}
            """.stripMargin()

        when:
        runner.arguments << 'shadow'
        def result = runner.run()

        then:
        success(result)
        assert output.exists()

        and:
        doesNotContain(output, ['fileInJar'])

        and:
        def text = getJarFileContents(output, 'fileInJar_one')
        assert text == "This is file one content"

        and:
        def text2 = getJarFileContents(output, 'fileInJar_two')
        assert text2 == "This is file two content"
    }

    def 'Groovy extension module transformer short syntax'() {
        given:
            def one = buildJar('one.jar')
                    .insertFile('META-INF/services/org.codehaus.groovy.runtime.ExtensionModule',
                    '''|moduleName=foo
                       |moduleVersion=1.0.5
                       |extensionClasses=com.acme.foo.FooExtension,com.acme.foo.BarExtension
                       |staticExtensionClasses=com.acme.foo.FooStaticExtension'''.stripMargin())
                    .write()

            def two = buildJar('two.jar')
                    .insertFile('META-INF/services/org.codehaus.groovy.runtime.ExtensionModule',
                    '''|moduleName=bar
                       |moduleVersion=2.3.5
                       |extensionClasses=com.acme.bar.SomeExtension,com.acme.bar.AnotherExtension
                       |staticExtensionClasses=com.acme.bar.SomeStaticExtension'''.stripMargin())
                    .write()

            buildFile << """
                |task shadow(type: ${ShadowJar.name}) {
                |    destinationDir = new File(buildDir, 'libs')
                |    baseName = 'shadow'
                |    from('${escapedPath(one)}')
                |    from('${escapedPath(two)}')
                |    mergeGroovyExtensionModules()
                |}
            """.stripMargin()

        when:
            runner.arguments << 'shadow'
            def result = runner.run()

        then:
            success(result)
            assert output.exists()

        and:
            def text = getJarFileContents(output, 'META-INF/services/org.codehaus.groovy.runtime.ExtensionModule')
            def props = new Properties()
            props.load(new StringReader(text))
            assert props.getProperty('moduleName') == 'MergedByShadowJar'
            assert props.getProperty('moduleVersion') == '1.0.0'
            assert props.getProperty('extensionClasses') == 'com.acme.foo.FooExtension,com.acme.foo.BarExtension,com.acme.bar.SomeExtension,com.acme.bar.AnotherExtension'
            assert props.getProperty('staticExtensionClasses') == 'com.acme.foo.FooStaticExtension,com.acme.bar.SomeStaticExtension'
    }

    private String escapedPath(File file) {
        file.path.replaceAll('\\\\', '\\\\\\\\')
    }
}
