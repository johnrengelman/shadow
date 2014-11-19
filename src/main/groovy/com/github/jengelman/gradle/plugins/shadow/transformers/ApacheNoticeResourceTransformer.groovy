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
import org.gradle.mvn3.org.codehaus.plexus.util.StringUtils

import java.text.SimpleDateFormat

/**
 * Merges <code>META-INF/NOTICE.TXT</code> files.
 *
 * Modified from org.apache.maven.plugins.shade.resource.ApacheNoticeResourceTransformer.javA
 *
 * Modifications
 * @author John Engelman
 */
class ApacheNoticeResourceTransformer implements Transformer {
    Set<String> entries = new LinkedHashSet<String>()

    Map<String, Set<String>> organizationEntries = new LinkedHashMap<String, Set<String>>()

    String projectName = "" // MSHADE-101 :: NullPointerException when projectName is missing

    boolean addHeader = true

    String preamble1 = "// ------------------------------------------------------------------\n" +
            "// NOTICE file corresponding to the section 4d of The Apache License,\n" +
            "// Version 2.0, in this case for "

    String preamble2 = "\n// ------------------------------------------------------------------\n"

    String preamble3 = "This product includes software developed at\n"

    //defaults overridable via config in pom
    String organizationName = "The Apache Software Foundation"

    String organizationURL = "http://www.apache.org/"

    String inceptionYear = "2006"

    String copyright

    /**
     * The file encoding of the <code>NOTICE</code> file.
     */
    String encoding

    private static final String NOTICE_PATH = "META-INF/NOTICE"

    private static final String NOTICE_TXT_PATH = "META-INF/NOTICE.txt"

    boolean canTransformResource(FileTreeElement element) {
        def path = element.relativePath.pathString
        if (NOTICE_PATH.equalsIgnoreCase(path) || NOTICE_TXT_PATH.equalsIgnoreCase(path)) {
            return true
        }

        return false
    }

    void transform(String jarName, String path, InputStream is, List<Relocator> relocators) {
        if (entries.isEmpty()) {
            String year = new SimpleDateFormat("yyyy").format(new Date())
            if (!inceptionYear.equals(year)) {
                year = inceptionYear + "-" + year
            }

            //add headers
            if (addHeader) {
                entries.add(preamble1 + projectName + preamble2)
            } else {
                entries.add("")
            }
            //fake second entry, we'll look for a real one later
            entries.add(projectName + "\nCopyright " + year + " " + organizationName + "\n")
            entries.add(preamble3 + organizationName + " (" + organizationURL + ").\n")
        }

        BufferedReader reader
        if (StringUtils.isNotEmpty(encoding)) {
            reader = new BufferedReader(new InputStreamReader(is, encoding))
        } else {
            reader = new BufferedReader(new InputStreamReader(is))
        }

        String line = reader.readLine()
        StringBuffer sb = new StringBuffer()
        Set<String> currentOrg = null
        int lineCount = 0
        while (line != null) {
            String trimedLine = line.trim()

            if (!trimedLine.startsWith("//")) {
                if (trimedLine.length() > 0) {
                    if (trimedLine.startsWith("- ")) {
                        //resource-bundle 1.3 mode
                        if (lineCount == 1
                                && sb.toString().indexOf("This product includes/uses software(s) developed by") != -1) {
                            currentOrg = organizationEntries.get(sb.toString().trim())
                            if (currentOrg == null) {
                                currentOrg = new TreeSet<String>()
                                organizationEntries.put(sb.toString().trim(), currentOrg)
                            }
                            sb = new StringBuffer()
                        } else if (sb.length() > 0 && currentOrg != null) {
                            currentOrg.add(sb.toString())
                            sb = new StringBuffer()
                        }

                    }
                    sb.append(line).append("\n")
                    lineCount++
                } else {
                    String ent = sb.toString()
                    if (ent.startsWith(projectName) && ent.indexOf("Copyright ") != -1) {
                        copyright = ent
                    }
                    if (currentOrg == null) {
                        entries.add(ent)
                    } else {
                        currentOrg.add(ent)
                    }
                    sb = new StringBuffer()
                    lineCount = 0
                    currentOrg = null
                }
            }

            line = reader.readLine()
        }
        if (sb.length() > 0) {
            if (currentOrg == null) {
                entries.add(sb.toString())
            } else {
                currentOrg.add(sb.toString())
            }
        }
    }

    boolean hasTransformedResource() {
        return true
    }

    void modifyOutputStream(ZipOutputStream os) {
        os.putNextEntry(new ZipEntry(NOTICE_PATH))

        Writer pow
        if (StringUtils.isNotEmpty(encoding)) {
            pow = new OutputStreamWriter(os, encoding)
        } else {
            pow = new OutputStreamWriter(os)
        }
        PrintWriter writer = new PrintWriter(pow)

        int count = 0
        for (String line : entries) {
            ++count
            if (line.equals(copyright) && count != 2) {
                continue
            }

            if (count == 2 && copyright != null) {
                writer.print(copyright)
                writer.print('\n')
            } else {
                writer.print(line)
                writer.print('\n')
            }
            if (count == 3) {
                //do org stuff
                for (Map.Entry<String, Set<String>> entry : organizationEntries.entrySet()) {
                    writer.print(entry.getKey())
                    writer.print('\n')
                    for (String l : entry.getValue()) {
                        writer.print(l)
                    }
                    writer.print('\n')
                }
            }
        }

        writer.flush()

        entries.clear()
    }
}
