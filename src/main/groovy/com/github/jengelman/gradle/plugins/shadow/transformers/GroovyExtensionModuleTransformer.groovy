/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
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
 * Modified from eu.appsatori.gradle.fatjar.tasks.PrepareFiles.groovy
 *
 * Resource transformer that merges Groovy extension module descriptor files into a single file. If there are several
 * META-INF/services/org.codehaus.groovy.runtime.ExtensionModule resources spread across many JARs the individual
 * entries will all be merged into a single META-INF/services/org.codehaus.groovy.runtime.ExtensionModule resource
 * packaged into the resultant JAR produced by the shadowing process.
 */
class GroovyExtensionModuleTransformer implements Transformer {

    private static final GROOVY_EXTENSION_MODULE_DESCRIPTOR_PATH =
            "META-INF/services/org.codehaus.groovy.runtime.ExtensionModule"

    private static final MODULE_NAME_KEY = 'moduleName'
    private static final MODULE_VERSION_KEY = 'moduleVersion'
    private static final EXTENSION_CLASSES_KEY = 'extensionClasses'
    private static final STATIC_EXTENSION_CLASSES_KEY = 'staticExtensionClasses'

    private static final MERGED_MODULE_NAME = 'MergedByShadowJar'
    private static final MERGED_MODULE_VERSION = '1.0.0'

    private final Properties module = new Properties()

    @Override
    boolean canTransformResource(FileTreeElement element) {
        return element.relativePath.pathString == GROOVY_EXTENSION_MODULE_DESCRIPTOR_PATH
    }

    @Override
    void transform(String jarName, String path, InputStream is, List<Relocator> relocators) {
        def props = new Properties()
        props.load(is)
        props.each { String key, String value ->
            switch (key) {
                case MODULE_NAME_KEY:
                    handle(key, value) {
                        module.setProperty(key, MERGED_MODULE_NAME)
                    }
                    break
                case MODULE_VERSION_KEY:
                    handle(key, value) {
                        module.setProperty(key, MERGED_MODULE_VERSION)
                    }
                    break
                case [EXTENSION_CLASSES_KEY, STATIC_EXTENSION_CLASSES_KEY]:
                    handle(key, value) { String existingValue ->
                        def newValue = "${existingValue},${value}"
                        module.setProperty(key, newValue)
                    }
                    break
            }
        }
    }

    private handle(String key, String value, Closure mergeValue) {
        def existingValue = module.getProperty(key)
        if (existingValue) {
            mergeValue(existingValue)
        } else {
            module.setProperty(key, value)
        }
    }

    @Override
    boolean hasTransformedResource() {
        return module.size() > 0
    }

    @Override
    void modifyOutputStream(ZipOutputStream os) {
        os.putNextEntry(new ZipEntry(GROOVY_EXTENSION_MODULE_DESCRIPTOR_PATH))
        IOUtil.copy(toInputStream(module), os)
        os.closeEntry()
    }

    private static InputStream toInputStream(Properties props) {
        def baos = new ByteArrayOutputStream()
        props.store(baos, null)
        return new ByteArrayInputStream(baos.toByteArray())
    }

}
