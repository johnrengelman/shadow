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


import org.apache.commons.io.FilenameUtils
import org.apache.commons.io.IOUtils
import org.apache.tools.zip.ZipEntry
import org.apache.tools.zip.ZipOutputStream
import org.codehaus.plexus.util.IOUtil
import org.gradle.api.file.FileTreeElement

/**
 * Merges standard files, like "META-INF/license.txt", "META-INF/notice.txt", "readme.txt" into one,
 * writing as prefix where the content comes from, so no license information or important hints gets lost.
 *
 * @author Jan-Hendrik Diederich
 */
@CacheableTransformer
class StandardFilesMergeTransformer implements Transformer {
    private class StandardFile implements Serializable {
        List<String> origins = new ArrayList<>();
        String content;

        StandardFile(String origin, String content) {
            this.origins.add(origin)
            this.content = content
        }
    }

    private final List<String> mergedFiles = [
            "META-INF/license", //
            "META-INF/notice", //
            "META-INF/readme", //
            "readme", //
    ]

    private final List<String> fileExtensions = [
            "txt", "md", "htm", "html"
    ]

    // Can't use normal HashMap, ...notice.txt and ...NOTICE.txt would otherwise be different entries.
    private Map<String, List<StandardFile>> fileEntries = new TreeMap<>(String.CASE_INSENSITIVE_ORDER)

    @Override
    boolean canTransformResource(FileTreeElement element) {
        String path = element.relativePath.pathString
        mergedFiles.stream() //
                .anyMatch(mergeFile -> {
                    if (path.equalsIgnoreCase(mergeFile)) {
                        return true
                    } else {
                        for (extension in fileExtensions) {
                            if (path.equalsIgnoreCase(mergeFile + "." + extension)) {
                                return true
                            }
                        }
                        return false
                    }
                })
    }

    @Override
    void transform(TransformerContext context) {
        List<StandardFile> files = fileEntries.computeIfAbsent(context.path, key -> new ArrayList<>())

        OutputStream outputStream = new ByteArrayOutputStream()
        IOUtils.copyLarge(context.is, outputStream)

        def fileContent = outputStream.toString()
        // Remove leading and trailing newlines. Don't trim whitespaces, so centered headers stay centered.
        def trimmedFileContent = fileContent.replaceAll("^[\\r\\n]+|[\\r\\n]+\$", "")

        var standardFile = files.stream() //
                .filter(entry -> trimmedFileContent.equalsIgnoreCase(entry.content)) //
                .findAny()
        String originName = context.origin != null
                ? FilenameUtils.getName(context.origin.name)
                : "Sourcecode"
        if (standardFile.isPresent()) {
            standardFile.get().origins.add(originName)
        } else {
            files.add(new StandardFile(originName, trimmedFileContent))
        }
    }

    @Override
    boolean hasTransformedResource() {
        return fileEntries.size() > 0
    }

    @Override
    void modifyOutputStream(ZipOutputStream os, boolean preserveFileTimestamps) {
        fileEntries.each { String path, List<StandardFile> files ->
            ZipEntry entry = new ZipEntry(path)
            entry.time = TransformerContext.getEntryTimestamp(preserveFileTimestamps, entry.time)
            os.putNextEntry(entry)
            IOUtil.copy(toInputStream(files), os)
            os.closeEntry()
        }
    }

    private static InputStream toInputStream(List<StandardFile> entries) {
        String joined = entries.stream() //
                .map(entry -> "Origins: " + entry.origins.sort().join(", ") //
                        + "\n\n" + entry.content) //
                .collect() //
                .join("\n" + "=".repeat(80) + "\n")
        new ByteArrayInputStream(joined.getBytes())
    }
}