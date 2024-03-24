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
import org.gradle.api.file.FileTreeElement
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional

import java.util.jar.Attributes
import java.util.jar.Attributes.Name
import java.util.jar.JarFile
import java.util.jar.Manifest

/**
 * A resource processor that allows the arbitrary addition of attributes to
 * the first MANIFEST.MF that is found in the set of JARs being processed, or
 * to a newly created manifest for the shaded JAR.
 * <p>
 * Modified from org.apache.maven.plugins.shade.resource.ManifestResourceTransformer
 * @author Jason van Zyl
 * @author John Engelman
 */
class ManifestResourceTransformer implements Transformer {

    // Configuration
    @Optional
    @Input
    String mainClass

    @Optional
    @Input
    Map<String, Attributes> manifestEntries

    // Fields
    private boolean manifestDiscovered

    private Manifest manifest

    boolean canTransformResource(FileTreeElement element) {
        def path = element.relativePath.pathString
        if (JarFile.MANIFEST_NAME.equalsIgnoreCase(path)) {
            return true
        }

        return false
    }

    void transform(TransformerContext context) {
        // We just want to take the first manifest we come across as that's our project's manifest. This is the behavior
        // now which is situational at best. Right now there is no context passed in with the processing so we cannot
        // tell what artifact is being processed.
        if (!manifestDiscovered) {
            manifest = new Manifest(context.is)
            manifestDiscovered = true
            try {
                context.is
            } catch (IOException ignored) {
            }
        }
    }

    boolean hasTransformedResource() {
        return true
    }

    void modifyOutputStream(ZipOutputStream os, boolean preserveFileTimestamps) {
        // If we didn't find a manifest, then let's create one.
        if (manifest == null) {
            manifest = new Manifest()
        }

        Attributes attributes = manifest.getMainAttributes()

        if (mainClass != null) {
            attributes.put(Name.MAIN_CLASS, mainClass)
        }

        if (manifestEntries != null) {
            for (Map.Entry<String, Attributes> entry : manifestEntries.entrySet()) {
                attributes.put(new Name(entry.getKey()), entry.getValue())
            }
        }

        ZipEntry entry = new ZipEntry(JarFile.MANIFEST_NAME)
        entry.time = TransformerContext.getEntryTimestamp(preserveFileTimestamps, entry.time)
        os.putNextEntry(entry)
        manifest.write(os)
    }

    ManifestResourceTransformer attributes(Map<String, ?> attributes) {
        if (manifestEntries == null) {
            manifestEntries = [:]
        }
        manifestEntries.putAll(attributes)
        this
    }
}
