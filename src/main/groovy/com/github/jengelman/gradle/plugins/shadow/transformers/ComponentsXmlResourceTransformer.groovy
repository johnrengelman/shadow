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
import org.apache.tools.zip.ZipEntry
import org.apache.tools.zip.ZipOutputStream
import org.codehaus.plexus.util.IOUtil
import org.codehaus.plexus.util.xml.XmlStreamReader
import org.codehaus.plexus.util.xml.XmlStreamWriter
import org.codehaus.plexus.util.xml.Xpp3Dom
import org.codehaus.plexus.util.xml.Xpp3DomBuilder
import org.codehaus.plexus.util.xml.Xpp3DomWriter
import org.gradle.api.file.FileTreeElement

/**
 * A resource processor that aggregates plexus <code>components.xml</code> files.
 * <p>
 * Modified from org.apache.maven.plugins.shade.resource.ComponentsXmlResourceTransformer.java
 *
 * @author John Engelman
 */
class ComponentsXmlResourceTransformer implements Transformer {
    private Map<String, Xpp3Dom> components = new LinkedHashMap<String, Xpp3Dom>()

    public static final String COMPONENTS_XML_PATH = "META-INF/plexus/components.xml"

    boolean canTransformResource(FileTreeElement element) {
        def path = element.relativePath.pathString
        return COMPONENTS_XML_PATH == path
    }

    void transform(TransformerContext context) {
        Xpp3Dom newDom

        try {
            BufferedInputStream bis = new BufferedInputStream(context.is) {
                void close()
                throws IOException {
                    // leave ZIP open
                }
            }

            Reader reader = new XmlStreamReader(bis)

            newDom = Xpp3DomBuilder.build(reader)
        }
        catch (Exception e) {
            throw (IOException) new IOException("Error parsing components.xml in " + context.is).initCause(e)
        }

        // Only try to merge in components if there are some elements in the component-set
        if (newDom.getChild("components") == null) {
            return
        }

        Xpp3Dom[] children = newDom.getChild("components").getChildren("component")

        for (int i = 0; i < children.length; i++) {
            Xpp3Dom component = children[i]

            String role = getValue(component, "role")
            role = getRelocatedClass(role, context)
            setValue(component, "role", role)

            String roleHint = getValue(component, "role-hint")

            String impl = getValue(component, "implementation")
            impl = getRelocatedClass(impl, context)
            setValue(component, "implementation", impl)

            String key = role + ':' + roleHint
            if (components.containsKey(key)) {
                // TODO: use the tools in Plexus to merge these properly. For now, I just need an all-or-nothing
                // configuration carry over

                Xpp3Dom dom = components.get(key)
                if (dom.getChild("configuration") != null) {
                    component.addChild(dom.getChild("configuration"))
                }
            }

            Xpp3Dom requirements = component.getChild("requirements")
            if (requirements != null && requirements.getChildCount() > 0) {
                for (int r = requirements.getChildCount() - 1; r >= 0; r--) {
                    Xpp3Dom requirement = requirements.getChild(r)

                    String requiredRole = getValue(requirement, "role")
                    requiredRole = getRelocatedClass(requiredRole, context)
                    setValue(requirement, "role", requiredRole)
                }
            }

            components.put(key, component)
        }
    }

    void modifyOutputStream(ZipOutputStream os, boolean preserveFileTimestamps) {
        byte[] data = getTransformedResource()

        ZipEntry entry = new ZipEntry(COMPONENTS_XML_PATH)
        entry.time = TransformerContext.getEntryTimestamp(preserveFileTimestamps, entry.time)

        os.putNextEntry(entry)

        IOUtil.copy(data, os)

        components.clear()
    }

    boolean hasTransformedResource() {
        return !components.isEmpty()
    }

    private byte[] getTransformedResource()
            throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(1024 * 4)

        try (Writer writer = new XmlStreamWriter(baos)) {
            Xpp3Dom dom = new Xpp3Dom("component-set")

            Xpp3Dom componentDom = new Xpp3Dom("components")

            dom.addChild(componentDom)

            for (Xpp3Dom component : components.values()) {
                componentDom.addChild(component)
            }

            Xpp3DomWriter.write(writer, dom)
        }

        return baos.toByteArray()
    }

    private static String getRelocatedClass(String className, TransformerContext context) {
        List<Relocator> relocators = context.relocators
        ShadowStats stats = context.stats
        if (className != null && className.length() > 0 && relocators != null) {
            for (Relocator relocator : relocators) {
                if (relocator.canRelocateClass(className)) {
                    RelocateClassContext relocateClassContext = new RelocateClassContext(className, stats)
                    return relocator.relocateClass(relocateClassContext)
                }
            }
        }

        return className
    }

    private static String getValue(Xpp3Dom dom, String element) {
        Xpp3Dom child = dom.getChild(element)

        return (child != null && child.getValue() != null) ? child.getValue() : ""
    }

    private static void setValue(Xpp3Dom dom, String element, String value) {
        Xpp3Dom child = dom.getChild(element)

        if (child == null || value == null || value.length() <= 0) {
            return
        }

        child.setValue(value)
    }

}
