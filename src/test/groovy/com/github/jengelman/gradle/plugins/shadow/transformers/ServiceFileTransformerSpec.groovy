package com.github.jengelman.gradle.plugins.shadow.transformers

import spock.lang.Unroll

@Unroll
class ServiceFileTransformerSpec extends TransformerSpecSupport {

    def "#status path #path #transform transformed"() {
        given:
            def transformer = new ServiceFileTransformer()
            if (exclude) {
                transformer.exclude(path)
            }

        when:
            def actual = transformer.canTransformResource(getFileElement(path))

        then:
            actual == expected

        where:
            path                                                      | exclude | expected
            'META-INF/services/java.sql.Driver'                       | false   | true
            'META-INF/services/io.dropwizard.logging.AppenderFactory' | false   | true
            'META-INF/services/org.apache.maven.Shade'                | true    | false
            'META-INF/services/foo/bar/moo.goo.Zoo'                   | false   | true
            'foo/bar.properties'                                      | false   | false
            'foo.props'                                               | false   | false

            transform = expected ? 'can be' : 'can not be'
            status = exclude ? 'excluded' : 'non-excluded'
    }

    def "transforms service file"() {
        given:
            def element = getFileElement(path)
            def transformer = new ServiceFileTransformer()

        when:
            if (transformer.canTransformResource(element)) {
                transformer.transform('jarNameToBeIgnored', path, toInputStream(input1), [])
                transformer.transform('jarNameToBeIgnored', path, toInputStream(input2), [])
            }

        then:
            transformer.hasTransformedResource()
            output == transformer.serviceEntries[path].toInputStream().text

        where:
            path                             | input1     | input2 || output
            'META-INF/services/com.acme.Foo' | 'foo'      | 'bar'  || 'foo\nbar'
            'META-INF/services/com.acme.Bar' | 'foo\nbar' | 'zoo'  || 'foo\nbar\nzoo'
    }

    def "excludes Groovy extension module descriptor files by default"() {
        given:
            def transformer = new ServiceFileTransformer()
            def element = getFileElement('META-INF/services/org.codehaus.groovy.runtime.ExtensionModule')

        expect:
            !transformer.canTransformResource(element)
    }

    private static InputStream toInputStream(String str) {
        return new ByteArrayInputStream(str.bytes)
    }

}
