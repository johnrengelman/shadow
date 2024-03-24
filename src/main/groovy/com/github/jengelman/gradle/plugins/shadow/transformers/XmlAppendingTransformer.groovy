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
import org.jdom2.Attribute
import org.jdom2.Content
import org.jdom2.Document
import org.jdom2.Element
import org.jdom2.JDOMException
import org.jdom2.input.SAXBuilder
import org.jdom2.input.sax.XMLReaders
import org.jdom2.output.Format
import org.jdom2.output.XMLOutputter
import org.xml.sax.EntityResolver
import org.xml.sax.InputSource
import org.xml.sax.SAXException

/**
 * Appends multiple occurrences of some XML file.
 * <p>
 * Modified from org.apache.maven.plugins.shade.resource.XmlAppendingTransformer.java
 *
 * @author John Engelman
 */
@CacheableTransformer
class XmlAppendingTransformer implements Transformer {
    public static final String XSI_NS = "http://www.w3.org/2001/XMLSchema-instance"

    @Input
    boolean ignoreDtd = true

    @Optional
    @Input
    String resource

    private Document doc

    boolean canTransformResource(FileTreeElement element) {
        def path = element.relativePath.pathString
        if (resource != null && resource.equalsIgnoreCase(path)) {
            return true
        }

        return false
    }

    void transform(TransformerContext context) {
        Document r
        try {
            SAXBuilder builder = new SAXBuilder(XMLReaders.NONVALIDATING)
            builder.setExpandEntities(false)
            if (ignoreDtd) {
                builder.setEntityResolver(new EntityResolver() {
                    InputSource resolveEntity(String publicId, String systemId)
                    throws SAXException, IOException {
                        return new InputSource(new StringReader(""))
                    }
                })
            }
            r = builder.build(context.is)
        }
        catch (JDOMException e) {
            throw new RuntimeException("Error processing resource " + resource + ": " + e.getMessage(), e)
        }

        if (doc == null) {
            doc = r
        } else {
            Element root = r.getRootElement()

            root.attributes.each { Attribute a ->

                Element mergedEl = doc.getRootElement()
                Attribute mergedAtt = mergedEl.getAttribute(a.getName(), a.getNamespace())
                if (mergedAtt == null) {
                    mergedEl.setAttribute(a)
                }
            }

            root.children.each { Content n ->
                doc.getRootElement().addContent(n.clone())
            }
        }
    }

    boolean hasTransformedResource() {
        return doc != null
    }

    void modifyOutputStream(ZipOutputStream os, boolean preserveFileTimestamps) {
        ZipEntry entry = new ZipEntry(resource)
        entry.time = TransformerContext.getEntryTimestamp(preserveFileTimestamps, entry.time)
        os.putNextEntry(entry)
        new XMLOutputter(Format.getPrettyFormat()).output(doc, os)

        doc = null
    }
}
