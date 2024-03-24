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
import com.github.jengelman.gradle.plugins.shadow.relocation.RelocateClassContext
import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
import org.apache.commons.io.IOUtils
import org.apache.commons.io.output.CloseShieldOutputStream
import org.apache.logging.log4j.core.config.plugins.processor.PluginCache
import org.apache.logging.log4j.core.config.plugins.processor.PluginEntry
import org.apache.tools.zip.ZipEntry
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.file.FileTreeElement

import static org.apache.logging.log4j.core.config.plugins.processor.PluginProcessor.PLUGIN_CACHE_FILE

/**
 * Modified from the maven equivalent to work with gradle
 *
 * @author Paul Nelson Baker
 * @see <a href="https://www.linkedin.com/in/paul-n-baker/">LinkedIn</a>
 * @see <a href="https://github.com/paul-nelson-baker/">GitHub</a>
 * @see <a href="https://github.com/edwgiz/maven-shaded-log4j-transformer">edwgiz/maven-shaded-log4j-transformer</a>
 * @see <a href="https://github.com/edwgiz/maven-shaded-log4j-transformer/blob/master/src/main/java/com/github/edwgiz/mavenShadePlugin/log4j2CacheTransformer/PluginsCacheFileTransformer.java">PluginsCacheFileTransformer.java</a>
 */
@CacheableTransformer
class Log4j2PluginsCacheFileTransformer implements Transformer {

    private final List<File> temporaryFiles
    private final List<Relocator> relocators

    private ShadowStats stats

    Log4j2PluginsCacheFileTransformer() {
        temporaryFiles = new ArrayList<>()
        relocators = new ArrayList<>()
    }

    @Override
    boolean canTransformResource(FileTreeElement element) {
        return PLUGIN_CACHE_FILE == element.name
    }

    @Override
    void transform(TransformerContext context) {
        def inputStream = context.is
        def temporaryFile = File.createTempFile("Log4j2Plugins", ".dat")
        temporaryFile.deleteOnExit()
        temporaryFiles.add(temporaryFile)
        FileOutputStream fos = new FileOutputStream(temporaryFile)
        try {
            IOUtils.copy(inputStream, fos)
        } finally {
            fos.close()
        }
        def contextRelocators = context.relocators
        if (contextRelocators != null) {
            this.relocators.addAll(contextRelocators)
        }
        if (this.stats == null) {
            this.stats = context.stats
        }
    }

    @Override
    boolean hasTransformedResource() {
        // This functionality matches the original plugin, however, I'm not clear what
        // the exact logic is. From what I can tell temporaryFiles should be never be empty
        // if anything has been performed.
        def hasTransformedMultipleFiles = temporaryFiles.size() > 1
        def hasAtLeastOneFileAndRelocator = !temporaryFiles.isEmpty() && !relocators.isEmpty()
        def hasTransformedResources = hasTransformedMultipleFiles || hasAtLeastOneFileAndRelocator
        return hasTransformedResources
    }

    @Override
    void modifyOutputStream(ZipOutputStream zipOutputStream, boolean preserveFileTimestamps) {
        PluginCache pluginCache = new PluginCache()
        pluginCache.loadCacheFiles(getUrlEnumeration())
        relocatePlugins(pluginCache)
        ZipEntry entry = new ZipEntry(PLUGIN_CACHE_FILE)
        entry.time = TransformerContext.getEntryTimestamp(preserveFileTimestamps, entry.time)
        zipOutputStream.putNextEntry(entry)
        pluginCache.writeCache(CloseShieldOutputStream.wrap(zipOutputStream))
        temporaryFiles.clear()
    }

    private Enumeration<URL> getUrlEnumeration() {
        def urls = temporaryFiles.collect({ it.toURI().toURL() }).asList()
        return Collections.enumeration(urls)
    }

    private void relocatePlugins(PluginCache pluginCache) {
        for (Map<String, PluginEntry> currentMap : pluginCache.getAllCategories().values()) {
            pluginEntryLoop:
            for (PluginEntry currentPluginEntry : currentMap.values()) {
                String className = currentPluginEntry.getClassName()
                RelocateClassContext relocateClassContext = new RelocateClassContext(className, stats)
                for (Relocator currentRelocator : relocators) {
                    // If we have a relocator that can relocate our current entry...
                    if (currentRelocator.canRelocateClass(className)) {
                        // Then we perform that relocation and update the plugin entry to reflect the new value.
                        String relocatedClassName = currentRelocator.relocateClass(relocateClassContext)
                        currentPluginEntry.setClassName(relocatedClassName)
                        continue pluginEntryLoop
                    }
                }
            }
        }
    }
}