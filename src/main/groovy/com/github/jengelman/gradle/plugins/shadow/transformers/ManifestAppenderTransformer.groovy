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

import org.apache.tools.zip.ZipEntry
import org.apache.tools.zip.ZipOutputStream
import org.codehaus.plexus.util.IOUtil
import org.gradle.api.file.FileTreeElement
import org.gradle.api.tasks.Input

import static java.nio.charset.StandardCharsets.UTF_8
import static java.util.jar.JarFile.MANIFEST_NAME

/**
 * A resource processor that can append arbitrary attributes to the first MANIFEST.MF
 * that is found in the set of JARs being processed. The attributes are appended in
 * the specified order, and duplicates are allowed.
 * <p>
 * Modified from {@link ManifestResourceTransformer}.
 * @author Chris Rankin
 */
class ManifestAppenderTransformer implements Transformer {
    private static final byte[] EOL = "\r\n".getBytes(UTF_8)
    private static final byte[] SEPARATOR = ": ".getBytes(UTF_8)

    private byte[] manifestContents = []
    private final List<Tuple2<String, ? extends Comparable<?>>> attributes = []
    private boolean trimTrailingWhitespace = false

    ManifestAppenderTransformer() {}

    @Input
    List<Tuple2<String, ? extends Comparable<?>>> getAttributes() { attributes }

    ManifestAppenderTransformer append(String name, Comparable<?> value) {
        attributes.add(new Tuple2<String, ? extends Comparable<?>>(name, value))
        this
    }

    ManifestAppenderTransformer trimTrailingWhitespace() {
        trimTrailingWhitespace = true
        this
    }

    @Override
    boolean canTransformResource(FileTreeElement element) {
        MANIFEST_NAME.equalsIgnoreCase(element.relativePath.pathString)
    }

    @Override
    void transform(TransformerContext context) {
        if (manifestContents.length == 0) {
            manifestContents = IOUtil.toByteArray(context.is)
            IOUtil.close(context.is)
        }
    }

    @Override
    boolean hasTransformedResource() {
        !attributes.isEmpty()
    }

    @Override
    void modifyOutputStream(ZipOutputStream os, boolean preserveFileTimestamps) {
        ZipEntry entry = new ZipEntry(MANIFEST_NAME)
        entry.time = TransformerContext.getEntryTimestamp(preserveFileTimestamps, entry.time)
        os.putNextEntry(entry)

        if (trimTrailingWhitespace) {
            os.write(trimTrailingWhitespace(manifestContents))
            os.write(EOL)
        } else {
            os.write(manifestContents)
        }

        if (!attributes.isEmpty()) {
            for (attribute in attributes) {
                os.write(attribute.first.getBytes(UTF_8))
                os.write(SEPARATOR)
                os.write(attribute.second.toString().getBytes(UTF_8))
                os.write(EOL)
            }
            os.write(EOL)
            attributes.clear()
        }
    }

    static byte[] trimTrailingWhitespace(byte[] contents) {
        new String(contents, UTF_8).replaceFirst("\\s++\$", "").getBytes(UTF_8)
    }
}
