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
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.apache.tools.zip.ZipOutputStream
import org.junit.Before
import org.junit.Test

import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertFalse
import static org.junit.Assert.assertTrue

/**
 * Test for {@link JsonTransformer}.
 *
 * @author Jan-Hendrik Diederich
 *
 * Modified from com.github.jengelman.gradle.plugins.shadow.transformers.XmlAppendingTransformerTest.java
 */
class JsonAppendingTransformerTest extends TransformerTestSupport {

    JsonTransformer transformer

    static final String TEST_ARTIFACT_JAR = 'test-artifact-1.0-SNAPSHOT.jar'
    static final String TEST_PROJECT_JAR = 'test-project-1.0-SNAPSHOT.jar'

    static final String TEST_JSON = 'test.json'
    static final String TEST2_JSON = 'test2.json'

    @Before
    void setUp() {
        transformer = new JsonTransformer()
    }

    @Test
    void testCanTransformResource() {
        transformer.paths = ["test.json"]

        assertTrue(this.transformer.canTransformResource(getFileElement("test.json")))
        assertFalse(this.transformer.canTransformResource(getFileElement("META-INF/MANIFEST.MF")))
    }

    @Test
    void transformResource() {
        transformer.transform(new TransformerContext(TEST_JSON, readFromTestJar(TEST_ARTIFACT_JAR, TEST_JSON),
                Collections.<Relocator> emptyList(), new ShadowStats()))
        transformer.transform(new TransformerContext(TEST2_JSON, readFromTestJar(TEST_ARTIFACT_JAR, TEST2_JSON),
                Collections.<Relocator> emptyList(), new ShadowStats()))

        transformer.transform(new TransformerContext(TEST_JSON, readFromTestJar(TEST_PROJECT_JAR, TEST_JSON),
                Collections.<Relocator> emptyList(), new ShadowStats()))
        transformer.transform(new TransformerContext(TEST2_JSON, readFromTestJar(TEST_PROJECT_JAR, TEST2_JSON),
                Collections.<Relocator> emptyList(), new ShadowStats()))

        def zipFileName = "testable-zip-file-"
        def zipFileSuffix = ".jar"
        def testableZipFile = File.createTempFile(zipFileName, zipFileSuffix)
        def fileOutputStream = new FileOutputStream(testableZipFile)
        def bufferedOutputStream = new BufferedOutputStream(fileOutputStream)
        def zipOutputStream = new ZipOutputStream(bufferedOutputStream)

        transformer.paths = [TEST_JSON, TEST2_JSON]
        try {
            transformer.modifyOutputStream(zipOutputStream, false)
        } finally {
            zipOutputStream.close()
            bufferedOutputStream.close()
            fileOutputStream.close()
        }
        // Read 1st file.
        String targetJson = readFromZipFile(testableZipFile.absolutePath, TEST_JSON)
        println("Target JSON: \"" + targetJson + "\"")

        assertFalse(targetJson.isEmpty())
        assertTrue(targetJson.contains("\"C: Only here\""))

        JsonElement jsonElement = JsonParser.parseString(targetJson)
        JsonObject jsonObject = jsonElement.getAsJsonObject()

        JsonElement subAA = jsonObject.get("a.a")

        JsonElement subAA1 = subAA.getAsJsonObject().get("a.sub1")
        assertEquals("A Sub 1", subAA1.asString)

        JsonElement subAA2 = subAA.getAsJsonObject().get("a.sub2")
        assertEquals("A Sub 2", subAA2.asString)

        // Read 2nd file.
        String target2Json = readFromZipFile(testableZipFile.absolutePath, TEST2_JSON)
        assertFalse(target2Json.isEmpty())
        JsonElement jsonElement2 = JsonParser.parseString(target2Json)
        JsonObject jsonObject2 = jsonElement2.getAsJsonObject()
        JsonArray jsonArray2 = jsonObject2.get("Array").asJsonArray
        assertEquals(List<String>.of("A", "B", "C", "C", "D", "E"),
                (List<String>) jsonArray2.collect({ it -> it.getAsString() }))
    }

    static InputStream readFromTestJar(String resourceName, String fileName) {
        try (ZipInputStream inputStream = new ZipInputStream(getResourceStream(resourceName))) {
            while (true) {
                ZipEntry entry = inputStream.nextEntry
                if (entry == null) {
                    break
                } else if (entry.name == fileName) {
                    // Read the content of the entry
                    byte[] buffer = new byte[entry.size]
                    inputStream.read(buffer)
                    return new ByteArrayInputStream(buffer)
                }
            }
        }
        throw new IllegalArgumentException("Missing entry " + fileName)
    }

    static String readFromZipFile(String resourceName, String fileName) {
        def zip = new ZipFile(resourceName)
        try {
            ZipEntry entry = zip.getEntry(fileName)
            if (!entry) {
                throw new IllegalArgumentException("Missing entry " + fileName + " in " + resourceName)
            }
            return new String(zip.getInputStream(entry).readAllBytes())
        } finally {
            zip.close()
        }
    }

    private static InputStream getResourceStream(String resource) {
        JsonAppendingTransformerTest.class.classLoader.getResourceAsStream(resource)
    }
}
