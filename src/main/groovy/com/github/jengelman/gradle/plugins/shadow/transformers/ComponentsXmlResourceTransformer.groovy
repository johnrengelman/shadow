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
import org.gradle.mvn3.org.codehaus.plexus.util.ReaderFactory
import org.gradle.mvn3.org.codehaus.plexus.util.WriterFactory
import org.gradle.mvn3.org.codehaus.plexus.util.xml.Xpp3Dom
import org.gradle.mvn3.org.codehaus.plexus.util.xml.Xpp3DomBuilder
import org.gradle.mvn3.org.codehaus.plexus.util.xml.Xpp3DomWriter

/**
 * A resource processor that aggregates plexus <code>components.xml</code> files.
 *
 * Modified from org.apache.maven.plugins.shade.resource.ComponentsXmlResourceTransformer.java
 *
 * Modifications
 * @author John Engelman
 */
class ComponentsXmlResourceTransformer implements Transformer {
    private Map<String, Xpp3Dom> components = new LinkedHashMap<String, Xpp3Dom>()

    static final String COMPONENTS_XML_PATH = "META-INF/plexus/components.xml"

    boolean canTransformResource(FileTreeElement element) {
        def path = element.relativePath.pathString
        return COMPONENTS_XML_PATH.equals(path)
    }

    void transform(String jarName, String path, InputStream is, List<Relocator> relocators) {
        Xpp3Dom newDom

        try {
            BufferedInputStream bis = new BufferedInputStream(is) {
                void close()
                throws IOException {
                    // leave ZIP open
                }
            }

            Reader reader = ReaderFactory.newXmlReader(bis)

            newDom = Xpp3DomBuilder.build(reader)
        }
        catch (Exception e) {
            throw (IOException) new IOException("Error parsing components.xml in " + is).initCause(e)
        }

        // Only try to merge in components if there are some elements in the component-set
        if (newDom.getChild("components") == null) {
            return
        }

        Xpp3Dom[] children = newDom.getChild("components").getChildren("component")

        for (int i = 0; i < children.length; i++) {
            Xpp3Dom component = children[i]

            String role = getValue(component, "role")
            role = getRelocatedClass(role, relocators)
            setValue(component, "role", role)

            String roleHint = getValue(component, "role-hint")

            String impl = getValue(component, "implementation")
            impl = getRelocatedClass(impl, relocators)
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
                    requiredRole = getRelocatedClass(requiredRole, relocators)
                    setValue(requirement, "role", requiredRole)
                }
            }

            components.put(key, component)
        }
    }

    void modifyOutputStream(ZipOutputStream os) {
        byte[] data = getTransformedResource()

        os.putNextEntry(new ZipEntry(COMPONENTS_XML_PATH))

        IOUtil.copy(data, os)

        components.clear()
    }

    boolean hasTransformedResource() {
        return !components.isEmpty()
    }

    byte[] getTransformedResource()
    throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(1024 * 4)

        Writer writer = WriterFactory.newXmlWriter(baos)
        try {
            Xpp3Dom dom = new Xpp3Dom("component-set")

            Xpp3Dom componentDom = new Xpp3Dom("components")

            dom.addChild(componentDom)

            for (Xpp3Dom component : components.values()) {
                componentDom.addChild(component)
            }

            Xpp3DomWriter.write(writer, dom)
        }
        finally {
            IOUtil.close(writer)
        }

        return baos.toByteArray()
    }

    private String getRelocatedClass(String className, List<Relocator> relocators) {
        if (className != null && className.length() > 0 && relocators != null) {
            for (Relocator relocator : relocators) {
                if (relocator.canRelocateClass(className)) {
                    return relocator.relocateClass(className)
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
