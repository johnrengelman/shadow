package com.github.jengelman.gradle.plugins.shadow.transformers

import spock.lang.Unroll

class SamePathFilesTransformerSpec extends TransformerSpecSupport {

    @Unroll
    def "Path #path should be #status"() {
        given:
        SamePathFilesTransformer transformer = new SamePathFilesTransformer()
        if (includes) {
            transformer.include(includes)
        }
        if (excludes) {
            transformer.exclude(excludes)
        }

        when:
        def actual = transformer.canTransformResource(getFileElement(path))

        then:
        actual == expected

        where:
        path                                 | includes       | excludes | expected
        'file.txt'                           | null           | null     | true
        'dir/file.txt'                       | '*'            | null     | false
        'dir/file.txt'                       | 'otherFile'    | null     | false
        'dir/file.txt'                       | '**'           | null     | true
        'dir/file.txt'                       | 'dir/*'        | null     | true
        'dir/file.txt'                       | '**'           | '**'     | false
        'dir/file.txt'                       | '**'           | 'dir/*'  | false
        'dir2/file.txt'                      | '**'           | 'dir/*'  | true
        'dir/file.txt'                       | 'dir/file.txt' | 'dir/*'  | false

        status = expected ? "included" : "excluded"
    }
}
