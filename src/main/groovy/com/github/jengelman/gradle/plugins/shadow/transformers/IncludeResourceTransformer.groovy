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
 * A resource processor that allows the addition of an arbitrary file
 * content into the shaded JAR.
 *
 * Modified from org.apache.maven.plugins.shade.resource.IncludeResourceTransformer.java
 *
 * Modifications
 * @author John Engelman
 */
public class IncludeResourceTransformer implements Transformer {
    File file

    String resource

    public boolean canTransformResource(FileTreeElement element) {
        return false
    }

    public void transform(String jarName, String path, InputStream is, List<Relocator> relocators) {
        // no op
    }

    public boolean hasTransformedResource() {
        return file != null ? file.exists() : false
    }

    public void modifyOutputStream(ZipOutputStream os) {
        os.putNextEntry(new ZipEntry(resource))

        InputStream is = new FileInputStream(file)
        IOUtil.copy(is, os)
        is.close()
    }
}
