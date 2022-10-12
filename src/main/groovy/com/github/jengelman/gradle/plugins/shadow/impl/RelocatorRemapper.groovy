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

package com.github.jengelman.gradle.plugins.shadow.impl

import com.github.jengelman.gradle.plugins.shadow.ShadowStats
import com.github.jengelman.gradle.plugins.shadow.relocation.RelocateClassContext
import com.github.jengelman.gradle.plugins.shadow.relocation.RelocatePathContext
import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowCopyAction.RelativeArchivePath
import groovy.util.logging.Slf4j
import org.objectweb.asm.commons.Remapper

import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Modified from org.apache.maven.plugins.shade.DefaultShader.java#RelocatorRemapper
 *
 * @author John Engelman
 */
@Slf4j
class RelocatorRemapper extends Remapper {

    private final Pattern classPattern = Pattern.compile("(\\[*)?L(.+)")

    List<Relocator> relocators
    ShadowStats stats
    public String currentFilePath

    RelocatorRemapper(List<Relocator> relocators, ShadowStats stats) {
        this.relocators = relocators
        this.stats = stats
    }

    boolean hasRelocators() {
        return !relocators.empty
    }

    Object mapValue(Object object) {
        if (object instanceof String) {
            String name = (String) object
            String value = name

            String prefix = ""
            String suffix = ""

            Matcher m = classPattern.matcher(name)
            if (m.matches()) {
                prefix = m.group(1) + "L"
                suffix = ""
                name = m.group(2)
            }

            for (Relocator r : relocators)  {
                boolean canRelocate = r.canRelocateSourceFile(currentFilePath)
                if (!canRelocate) {
                    String info = String.format("skipped %s as src path %s excluded", r, currentFilePath)
                    log.debug(info)
                    continue
                }
                if (r.canRelocateClass(name)) {
                    RelocateClassContext classContext = RelocateClassContext.builder().className(name).stats(stats).build()
                    value = prefix + r.relocateClass(classContext) + suffix
                    break
                } else if (r.canRelocatePath(name)) {
                    RelocatePathContext pathContext = RelocatePathContext.builder().path(name).stats(stats).build()
                    value = prefix + r.relocatePath(pathContext) + suffix
                    break
                }
            }

            return value
        }

        return super.mapValue(object)
    }

    String map(String name) {
        String value = name

        String prefix = ""
        String suffix = ""

        Matcher m = classPattern.matcher(name)
        if (m.matches()) {
            prefix = m.group(1) + "L"
            suffix = ""
            name = m.group(2)
        }

        for (Relocator r : relocators) {
            boolean canRelocate = r.canRelocateSourceFile(currentFilePath)
            if (!canRelocate) {
                String info = String.format("skipped %s as src path %s excluded", r, currentFilePath)
                log.debug(info)
                continue
            }

            if (r.canRelocatePath(name)) {
                RelocatePathContext pathContext = RelocatePathContext.builder().path(name).stats(stats).build()
                value = prefix + r.relocatePath(pathContext) + suffix
                break
            }
        }

        return value
    }

    String mapPath(String path) {
        map(path.substring(0, path.indexOf('.')))
    }

    String mapPath(RelativeArchivePath path) {
        mapPath(path.pathString)
    }

}
