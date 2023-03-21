/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License") you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.github.jengelman.gradle.plugins.shadow.transformers

import com.github.jengelman.gradle.plugins.shadow.ShadowStats
import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
import org.apache.tools.zip.ZipOutputStream
import org.junit.Before
import org.junit.Test

import java.util.zip.ZipFile

import static java.util.Arrays.asList
import static org.junit.Assert.*

/**
 * Test for {@link ManifestAppenderTransformer}.
 */
class ManifestAppenderTransformerTest extends TransformerTestSupport {
    public static final String MANIFEST_NAME = "META-INF/MANIFEST.MF"

    private ManifestAppenderTransformer transformer

    @Before
    void setUp() {
        transformer = new ManifestAppenderTransformer()
    }

    @Test
    void testCanTransformResource() {
        transformer.with {
            append('Name', 'org/foo/bar/')
            append('Sealed', true)
        }

        assertTrue(transformer.canTransformResource(getFileElement(MANIFEST_NAME)))
        assertTrue(transformer.canTransformResource(getFileElement(MANIFEST_NAME.toLowerCase())))
    }

    @Test
    void testHasTransformedResource() {
        transformer.append('Tag', 'Something')

        assertTrue(transformer.hasTransformedResource())
    }

    @Test
    void testHasNotTransformedResource() {
        assertFalse(transformer.hasTransformedResource())
    }

    @Test
    void testTransformation() {
        transformer.with {
            append('Name', 'org/foo/bar/')
            append('Sealed', true)
            append('Name', 'com/example/')
            append('Sealed', false)

            transform(new TransformerContext(MANIFEST_NAME, getResourceStream(MANIFEST_NAME), Collections.<Relocator>emptyList(), new ShadowStats()))
        }

        def testableZipFile = File.createTempFile("testable-zip-file-", ".jar")
        def fileOutputStream = new FileOutputStream(testableZipFile)
        def bufferedOutputStream = new BufferedOutputStream(fileOutputStream)
        def zipOutputStream = new ZipOutputStream(bufferedOutputStream)

        try {
            transformer.modifyOutputStream(zipOutputStream, true)
        } finally {
            zipOutputStream.close()
        }

        def targetLines = readFrom(testableZipFile, MANIFEST_NAME)
        assertFalse(targetLines.isEmpty())
        assertTrue(targetLines.size() > 4)

        def trailer = targetLines.with { subList(size() - 5, size()) }
        assertEquals(asList(
            "Name: org/foo/bar/",
            "Sealed: true",
            "Name: com/example/",
            "Sealed: false",
            ""), trailer
        )
    }

    @Test
    void testNoTransformation() {
        def sourceLines = getResourceStream(MANIFEST_NAME).readLines()

        transformer.transform(new TransformerContext(MANIFEST_NAME, getResourceStream(MANIFEST_NAME), Collections.<Relocator>emptyList(), new ShadowStats()))

        def testableZipFile = File.createTempFile("testable-zip-file-", ".jar")
        def fileOutputStream = new FileOutputStream(testableZipFile)
        def bufferedOutputStream = new BufferedOutputStream(fileOutputStream)
        def zipOutputStream = new ZipOutputStream(bufferedOutputStream)

        try {
            transformer.modifyOutputStream(zipOutputStream, true)
        } finally {
            zipOutputStream.close()
        }
        def targetLines = readFrom(testableZipFile, MANIFEST_NAME)

        assertEquals(sourceLines, targetLines)
    }

    @Test
    void testTransformationTrimmingTrailingWhitespace() {
        def sourceLines = getResourceStream(MANIFEST_NAME).readLines()
        def trimmedContent = sourceLines
        while (trimmedContent.last().trim().isEmpty()) {
            trimmedContent.removeLast()
        }

        transformer.with {
            trimTrailingWhitespace()
            append('Name', 'org/foo/bar/')
            transform(new TransformerContext(MANIFEST_NAME, getResourceStream(MANIFEST_NAME), Collections.<Relocator>emptyList(), new ShadowStats()))
        }

        def testableZipFile = File.createTempFile("testable-zip-file-", ".jar")
        def fileOutputStream = new FileOutputStream(testableZipFile)
        def bufferedOutputStream = new BufferedOutputStream(fileOutputStream)
        def zipOutputStream = new ZipOutputStream(bufferedOutputStream)

        try {
            transformer.modifyOutputStream(zipOutputStream, true)
        } finally {
            zipOutputStream.close()
        }

        def targetLines = readFrom(testableZipFile, MANIFEST_NAME)
        assertEquals(trimmedContent + "Name: org/foo/bar/" + "", targetLines)
    }

    static List<String> readFrom(File jarFile, String resourceName) {
        def zip = new ZipFile(jarFile)
        try {
            def entry = zip.getEntry(resourceName)
            if (!entry) {
                return Collections.emptyList()
            }
            return zip.getInputStream(entry).readLines()
        } finally {
            zip.close()
        }
    }

    InputStream getResourceStream(String resource) {
        this.class.classLoader.getResourceAsStream(resource)
    }
}
