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

import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
import org.apache.tools.zip.ZipEntry
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.file.FileTreeElement
import org.gradle.mvn3.org.codehaus.plexus.util.IOUtil

/**
 * A resource processor that appends content for a resource, separated by a newline.
 *
 * Modified from org.apache.maven.plugins.shade.resource.AppendingTransformer.java
 *
 * Modifications
 * @author John Engelman
 */
class AppendingTransformer implements Transformer {
    String resource

    ByteArrayOutputStream data = new ByteArrayOutputStream()

    boolean canTransformResource(FileTreeElement element) {
        def path = element.relativePath.pathString
        if (resource != null && resource.equalsIgnoreCase(path)) {
            return true
        }

        return false
    }

    void transform(String jarName, String path, InputStream is, List<Relocator> relocators) {
        IOUtil.copy(is, data)
        data.write('\n'.bytes)

        is.close()
    }

    boolean hasTransformedResource() {
        return data.size() > 0
    }

    void modifyOutputStream(ZipOutputStream os) {
        os.putNextEntry(new ZipEntry(resource))

        IOUtil.copy(new ByteArrayInputStream(data.toByteArray()), os)
        data.reset()
    }
}
