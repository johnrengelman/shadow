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
import junit.framework.TestCase

import org.xmlunit.diff.Diff
import org.xmlunit.builder.DiffBuilder
import junit.framework.Assert
import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
import org.codehaus.plexus.util.IOUtil

/**
 * Test for {@link ComponentsXmlResourceTransformer}.
 *
 * @author Brett Porter
 * @version $Id: ComponentsXmlResourceTransformerTest.java 1379994 2012-09-02 15:22:49Z hboutemy $
 *
 * Modified from org.apache.maven.plugins.shade.resource.ComponentsXmlResourceTransformerTest.java
 */
class ComponentsXmlResourceTransformerTest extends TestCase {
    private ComponentsXmlResourceTransformer transformer
    private ShadowStats stats

    void setUp() {
        this.transformer = new ComponentsXmlResourceTransformer()
        stats = new ShadowStats()
    }

    void testConfigurationMerging() {

        formatXml = true
        diffBuilder.normalizeWhitespace()

        transformer.transform(
                TransformerContext.builder()
                        .path("components-1.xml")
                        .is(getClass().getResourceAsStream("/components-1.xml"))
                        .relocators(Collections.<Relocator> emptyList())
                        .stats(stats)
                        .build())
        transformer.transform(
                TransformerContext.builder()
                        .path("components-1.xml")
                        .is(getClass().getResourceAsStream("/components-2.xml"))
                        .relocators(Collections.<Relocator> emptyList())
                        .stats(stats)
                        .build())
        assertEquals( IOUtil.toString( getClass().getResourceAsStream( "/components-expected.xml" ), "UTF-8" ),
                IOUtil.toString( transformer.getTransformedResource(), "UTF-8" ) )
    }
}