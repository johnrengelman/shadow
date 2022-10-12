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

package com.github.jengelman.gradle.plugins.shadow.relocation

import com.github.jengelman.gradle.plugins.shadow.ShadowStats
import junit.framework.TestCase

/**
 * Test for {@link SimpleRelocator}.
 *
 * @author Benjamin Bentmann
 * @version $Id: SimpleRelocatorTest.java 1342979 2012-05-26 22:05:45Z bimargulies $
 *
 * Modified from org.apache.maven.plugins.shade.relocation.SimpleRelocatorTest.java
 * 
 * Modifications
 * @author John Engelman
 */
class SimpleRelocatorTest extends TestCase {

    ShadowStats stats

    @Override
    protected void setUp() {
      stats = new ShadowStats()
    }

    void testIsExcludedSource() {
        SimpleRelocator relocator

        println("hello")
        relocator = new SimpleRelocator("org.foo", null, null, null)
        relocator.excludeSource "org/apache/iceberg/spark/parquet"
        relocator.excludeSource "org/apache/spark/sql/execution/datasources/parquet"
        println(relocator)

        assertEquals(false, relocator.canRelocateSourceFile("org/apache/iceberg/spark/parquet/SparkNativeParquet.class"))
        assertEquals(false, relocator.canRelocateSourceFile("org/apache/iceberg/spark/parquet/SparkNativeParquet\$.class"))
        assertEquals(false, relocator.canRelocateSourceFile("org/apache/spark/sql/execution/datasources/parquet/v1.class"))
        assertEquals(true, relocator.canRelocateSourceFile("org/foo/Class.class"))
    }

    void testCanRelocatePath() {
        SimpleRelocator relocator

        relocator = new SimpleRelocator("org.foo", null, null, null)
        assertEquals(true, relocator.canRelocatePath("org/foo/Class"))
        assertEquals(true, relocator.canRelocatePath("org/foo/Class.class"))
        assertEquals(true, relocator.canRelocatePath("org/foo/bar/Class"))
        assertEquals(true, relocator.canRelocatePath("org/foo/bar/Class.class"))
        assertEquals(false, relocator.canRelocatePath("com/foo/bar/Class"))
        assertEquals(false, relocator.canRelocatePath("com/foo/bar/Class.class"))
        assertEquals(false, relocator.canRelocatePath("org/Foo/Class"))
        assertEquals(false, relocator.canRelocatePath("org/Foo/Class.class"))

        // Verify paths starting with '/'
        assertEquals(false, relocator.canRelocatePath("/org/Foo/Class"))
        assertEquals(false, relocator.canRelocatePath("/org/Foo/Class.class"))

        relocator = new SimpleRelocator("org.foo", null, null, Arrays.asList(
                [ "org.foo.Excluded", "org.foo.public.*", "org.foo.recurse.**", "org.foo.Public*Stuff" ] as String[]))
        assertEquals(true, relocator.canRelocatePath("org/foo/Class"))
        assertEquals(true, relocator.canRelocatePath("org/foo/Class.class"))
        assertEquals(true, relocator.canRelocatePath("org/foo/excluded"))
        assertEquals(false, relocator.canRelocatePath("org/foo/Excluded"))
        assertEquals(false, relocator.canRelocatePath("org/foo/Excluded.class"))
        assertEquals(false, relocator.canRelocatePath("org/foo/public"))
        assertEquals(false, relocator.canRelocatePath("org/foo/public/Class"))
        assertEquals(false, relocator.canRelocatePath("org/foo/public/Class.class"))
        assertEquals(false, relocator.canRelocatePath("org/foo/public/sub"))
        assertEquals(true, relocator.canRelocatePath("org/foo/public/sub/Class"))
        assertEquals(true, relocator.canRelocatePath("org/foo/publicRELOC/Class"))
        assertEquals(true, relocator.canRelocatePath("org/foo/PrivateStuff"))
        assertEquals(true, relocator.canRelocatePath("org/foo/PrivateStuff.class"))
        assertEquals(false, relocator.canRelocatePath("org/foo/PublicStuff"))
        assertEquals(false, relocator.canRelocatePath("org/foo/PublicStuff.class"))
        assertEquals(false, relocator.canRelocatePath("org/foo/PublicUtilStuff"))
        assertEquals(false, relocator.canRelocatePath("org/foo/PublicUtilStuff.class"))
        assertEquals(false, relocator.canRelocatePath("org/foo/recurse"))
        assertEquals(false, relocator.canRelocatePath("org/foo/recurse/Class"))
        assertEquals(false, relocator.canRelocatePath("org/foo/recurse/Class.class"))
        assertEquals(false, relocator.canRelocatePath("org/foo/recurse/sub"))
        assertEquals(false, relocator.canRelocatePath("org/foo/recurse/sub/Class"))
        assertEquals(false, relocator.canRelocatePath("org/foo/recurse/sub/Class.class"))

        // Verify edge cases
        relocator = new SimpleRelocator("org.f", null, null, null)
        assertEquals(false, relocator.canRelocatePath(""))       // Empty path
        assertEquals(false, relocator.canRelocatePath(".class")) // only .class
        assertEquals(false, relocator.canRelocatePath("te"))     // shorter than path pattern
        assertEquals(false, relocator.canRelocatePath("test"))   // shorter than path pattern with /
        assertEquals(true, relocator.canRelocatePath("org/f"))   // equal to path pattern
        assertEquals(true, relocator.canRelocatePath("/org/f"))  // equal to path pattern with /
    }
  
   void testCanRelocatePathWithRegex() {
        SimpleRelocator relocator

        // Include with Regex
        relocator = new SimpleRelocator("org.foo", null, Collections.singletonList("%regex[org/foo/R(\\\$.*)?\$]"), null)
        assertEquals(true, relocator.canRelocatePath("org/foo/R.class"))
        assertEquals(true, relocator.canRelocatePath("org/foo/R\$string.class"))
        assertEquals(true, relocator.canRelocatePath("org/foo/R\$layout.class"))
        assertEquals(false, relocator.canRelocatePath("org/foo/Recording/R.class"))
        assertEquals(false, relocator.canRelocatePath("org/foo/Recording.class"))
        assertEquals(false, relocator.canRelocatePath("org/foo/bar/R\$string.class"))
        assertEquals(false, relocator.canRelocatePath("org/R.class"))
        assertEquals(false, relocator.canRelocatePath("org/R\$string.class"))

        // Exclude with Regex
        relocator = new SimpleRelocator("org.foo", null, null, null)
        relocator.exclude("%regex[org/foo/.*Factory[0-9].*]")
        assertEquals(true, relocator.canRelocatePath("org/foo/Factory.class"))
        assertEquals(true, relocator.canRelocatePath("org/foo/FooFactoryMain.class"))
        assertEquals(true, relocator.canRelocatePath("org/foo/BarFactory.class"))
        assertEquals(false, relocator.canRelocatePath("org/foo/Factory0.class"))
        assertEquals(false, relocator.canRelocatePath("org/foo/FooFactory1Main.class"))
        assertEquals(false, relocator.canRelocatePath("org/foo/BarFactory2.class"))

        // Include with Regex and normal pattern
        relocator = new SimpleRelocator("org.foo", null,
                Arrays.asList("%regex[org/foo/.*Factory[0-9].*]", "org.foo.public.*"), null)
        assertEquals(true, relocator.canRelocatePath("org/foo/Factory1.class"))
        assertEquals(true, relocator.canRelocatePath("org/foo/public/Bar.class"))
        assertEquals(false, relocator.canRelocatePath("org/foo/Factory.class"))
        assertEquals(false, relocator.canRelocatePath("org/foo/R.class"))
   }

    void testCanRelocateClass() {
        SimpleRelocator relocator

        relocator = new SimpleRelocator("org.foo", null, null, null)
        assertEquals(true, relocator.canRelocateClass("org.foo.Class"))
        assertEquals(true, relocator.canRelocateClass("org.foo.bar.Class"))
        assertEquals(false, relocator.canRelocateClass("com.foo.bar.Class"))
        assertEquals(false, relocator.canRelocateClass("org.Foo.Class"))

        relocator = new SimpleRelocator("org.foo", null, null, Arrays.asList(
                [ "org.foo.Excluded", "org.foo.public.*", "org.foo.recurse.**", "org.foo.Public*Stuff" ] as String[]))
        assertEquals(true, relocator.canRelocateClass("org.foo.Class"))
        assertEquals(true, relocator.canRelocateClass("org.foo.excluded"))
        assertEquals(false, relocator.canRelocateClass("org.foo.Excluded"))
        assertEquals(false, relocator.canRelocateClass("org.foo.public"))
        assertEquals(false, relocator.canRelocateClass("org.foo.public.Class"))
        assertEquals(false, relocator.canRelocateClass("org.foo.public.sub"))
        assertEquals(true, relocator.canRelocateClass("org.foo.public.sub.Class"))
        assertEquals(true, relocator.canRelocateClass("org.foo.publicRELOC.Class"))
        assertEquals(true, relocator.canRelocateClass("org.foo.PrivateStuff"))
        assertEquals(false, relocator.canRelocateClass("org.foo.PublicStuff"))
        assertEquals(false, relocator.canRelocateClass("org.foo.PublicUtilStuff"))
        assertEquals(false, relocator.canRelocateClass("org.foo.recurse"))
        assertEquals(false, relocator.canRelocateClass("org.foo.recurse.Class"))
        assertEquals(false, relocator.canRelocateClass("org.foo.recurse.sub"))
        assertEquals(false, relocator.canRelocateClass("org.foo.recurse.sub.Class"))
    }

    void testCanRelocateRawString() {
        SimpleRelocator relocator

        relocator = new SimpleRelocator("org/foo", null, null, null, true)
        assertEquals(true, relocator.canRelocatePath("(I)org/foo/bar/Class"))

        relocator = new SimpleRelocator("^META-INF/org.foo.xml\$", null, null, null, true)
        assertEquals(true, relocator.canRelocatePath("META-INF/org.foo.xml"))
    }

    //MSHADE-119, make sure that the easy part of this works.
    void testCanRelocateAbsClassPath() {
        SimpleRelocator relocator = new SimpleRelocator("org.apache.velocity", "org.apache.momentum", null, null)
        assertEquals("/org/apache/momentum/mass.properties", relocator.relocatePath(pathContext("/org/apache/velocity/mass.properties")))

    }

    void testRelocatePath() {
        SimpleRelocator relocator

        relocator = new SimpleRelocator("org.foo", null, null, null)
        assertEquals("hidden/org/foo/bar/Class.class", relocator.relocatePath(pathContext("org/foo/bar/Class.class")))

        relocator = new SimpleRelocator("org.foo", "private.stuff", null, null)
        assertEquals("private/stuff/bar/Class.class", relocator.relocatePath(pathContext("org/foo/bar/Class.class")))
    }

    void testRelocateClass() {
        SimpleRelocator relocator

        relocator = new SimpleRelocator("org.foo", null, null, null)
        assertEquals("hidden.org.foo.bar.Class", relocator.relocateClass(classContext("org.foo.bar.Class")))

        relocator = new SimpleRelocator("org.foo", "private.stuff", null, null)
        assertEquals("private.stuff.bar.Class", relocator.relocateClass(classContext("org.foo.bar.Class")))
    }

    void testRelocateRawString() {
        SimpleRelocator relocator

        relocator = new SimpleRelocator("Lorg/foo", "Lhidden/org/foo", null, null, true)
        assertEquals("(I)Lhidden/org/foo/bar/Class", relocator.relocatePath(pathContext("(I)Lorg/foo/bar/Class")))

        relocator = new SimpleRelocator("^META-INF/org.foo.xml\$", "META-INF/hidden.org.foo.xml", null, null, true)
        assertEquals("META-INF/hidden.org.foo.xml", relocator.relocatePath(pathContext("META-INF/org.foo.xml")))
    }
    
    protected RelocatePathContext pathContext(String path) {
        return RelocatePathContext.builder().path(path).stats(stats).build()
    }

    protected RelocateClassContext classContext(String className) {
        return RelocateClassContext.builder().className(className).stats(stats).build()
    }
}
