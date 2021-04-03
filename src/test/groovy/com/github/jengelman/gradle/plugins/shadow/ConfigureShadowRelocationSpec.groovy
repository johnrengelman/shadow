package com.github.jengelman.gradle.plugins.shadow

import com.github.jengelman.gradle.plugins.shadow.util.PluginSpecification


class ConfigureShadowRelocationSpec extends PluginSpecification {

    def "auto relocate plugin dependencies"() {
        given:
        buildFile << """

            task relocateShadowJar(type: com.github.jengelman.gradle.plugins.shadow.tasks.ConfigureShadowRelocation) {
                target = tasks.shadowJar
            }

            tasks.shadowJar.dependsOn tasks.relocateShadowJar

            dependencies {
               compile 'junit:junit:3.8.2'
            }
        """.stripIndent()

        when:
        run('shadowJar', '-s')

        then:
        contains(output, [
                'META-INF/MANIFEST.MF',
                'shadow/junit/textui/ResultPrinter.class',
                'shadow/junit/textui/TestRunner.class',
                'shadow/junit/framework/Assert.class',
                'shadow/junit/framework/AssertionFailedError.class',
                'shadow/junit/framework/ComparisonCompactor.class',
                'shadow/junit/framework/ComparisonFailure.class',
                'shadow/junit/framework/Protectable.class',
                'shadow/junit/framework/Test.class',
                'shadow/junit/framework/TestCase.class',
                'shadow/junit/framework/TestFailure.class',
                'shadow/junit/framework/TestListener.class',
                'shadow/junit/framework/TestResult$1.class',
                'shadow/junit/framework/TestResult.class',
                'shadow/junit/framework/TestSuite$1.class',
                'shadow/junit/framework/TestSuite.class'
        ])
    }

    def "auto relocate plugin dependencies with exclusion list"() {
        given:
        buildFile << """

            task relocateShadowJar(type: com.github.jengelman.gradle.plugins.shadow.tasks.ConfigureShadowRelocation) {
                target = tasks.shadowJar
                excludedPackages = ["junit/framework"]
            }

            tasks.shadowJar.dependsOn tasks.relocateShadowJar

            dependencies {
               compile 'junit:junit:3.8.2'
            }
        """.stripIndent()

        when:
        runner.withArguments('shadowJar', '-s').build()

        then:
        contains(output, [
            'META-INF/MANIFEST.MF',
            'shadow/junit/textui/ResultPrinter.class',
            'shadow/junit/textui/TestRunner.class',
            'junit/framework/Assert.class',
            'junit/framework/AssertionFailedError.class',
            'junit/framework/ComparisonCompactor.class',
            'junit/framework/ComparisonFailure.class',
            'junit/framework/Protectable.class',
            'junit/framework/Test.class',
            'junit/framework/TestCase.class',
            'junit/framework/TestFailure.class',
            'junit/framework/TestListener.class',
            'junit/framework/TestResult$1.class',
            'junit/framework/TestResult.class',
            'junit/framework/TestSuite$1.class',
            'junit/framework/TestSuite.class'
        ])
    }
}
