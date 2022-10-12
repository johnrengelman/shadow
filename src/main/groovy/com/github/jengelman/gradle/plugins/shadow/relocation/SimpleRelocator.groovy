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

import org.codehaus.plexus.util.SelectorUtils
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional

import java.util.regex.Pattern

/**
 * Modified from org.apache.maven.plugins.shade.relocation.SimpleRelocator.java
 *
 * @author Jason van Zyl
 * @author Mauro Talevi
 * @author John Engelman
 */
@CacheableRelocator
class SimpleRelocator implements Relocator {

    private final String pattern

    private final String pathPattern

    private final String shadedPattern

    private final String shadedPathPattern

    private final Set<String> includes

    private final Set<String> excludes

    private final Set<String> excludeSources
    
    private final boolean rawString

    SimpleRelocator() {

    }

    SimpleRelocator(String patt, String shadedPattern, List<String> includes, List<String> excludes) {
        this(patt, shadedPattern, includes, excludes, [], false)
    }

    SimpleRelocator(String patt, String shadedPattern, List<String> includes, List<String> excludes,
                    List<String> excludeSources, boolean rawString) {
        this.rawString = rawString

        if (rawString) {
            this.pathPattern = patt
            this.shadedPathPattern = shadedPattern

            this.pattern = null // not used for raw string relocator
            this.shadedPattern = null // not used for raw string relocator
        } else {
            if (patt == null) {
                this.pattern = ""
                this.pathPattern = ""
            } else {
                this.pattern = patt.replace('/', '.')
                this.pathPattern = patt.replace('.', '/')
            }

            if (shadedPattern != null) {
                this.shadedPattern = shadedPattern.replace('/', '.')
                this.shadedPathPattern = shadedPattern.replace('.', '/')
            } else {
                this.shadedPattern = "hidden." + this.pattern
                this.shadedPathPattern = "hidden/" + this.pathPattern
            }
        }

        this.includes = normalizePatterns(includes)
        this.excludes = normalizePatterns(excludes)
        this.excludeSources = normalizePatterns(excludeSources)
    }

    SimpleRelocator include(String pattern) {
        this.includes.addAll normalizePatterns([pattern])
        return this
    }

    SimpleRelocator exclude(String pattern) {
        this.excludes.addAll normalizePatterns([pattern])
        return this
    }

    SimpleRelocator excludeSource(String pattern) {
        this.excludeSources.addAll normalizePatterns([pattern])
        return this
    }

    private static Set<String> normalizePatterns(Collection<String> patterns) {
        Set<String> normalized = null

        if (patterns != null && !patterns.isEmpty()) {
            normalized = new LinkedHashSet<String>()

            for (String pattern : patterns) {
                // Regex patterns don't need to be normalized and stay as is
                if (pattern.startsWith(SelectorUtils.REGEX_HANDLER_PREFIX)) {
                    normalized.add(pattern)
                    continue
                }

                String classPattern = pattern.replace('.', '/')

                normalized.add(classPattern)

                if (classPattern.endsWith("/*")) {
                    String packagePattern = classPattern.substring(0, classPattern.lastIndexOf('/'))
                    normalized.add(packagePattern)
                }
            }
        }

        return normalized ?: []
    }

    private boolean isIncluded(String path) {
        if (includes != null && !includes.isEmpty()) {
            for (String include : includes) {
                if (SelectorUtils.matchPath(include, path, '/', true)) {
                    return true
                }
            }
            return false
        }
        return true
    }

    private boolean isExcluded(String path) {
        if (excludes != null && !excludes.isEmpty()) {
            for (String exclude : excludes) {
                if (SelectorUtils.matchPath(exclude, path, '/', true)) {
                    return true
                }
            }
        }
        return false
    }

    private boolean isExcludedSource(String srcPath) {
        if (srcPath != null && excludeSources != null && !excludeSources.isEmpty()) {
            for (String excludeSource : excludeSources) {
                if (srcPath.startsWith(excludeSource)) {
                    return true
                }
            }
        }
        return false
    }

    boolean canRelocatePath(String path) {
        if (rawString) {
            return Pattern.compile(pathPattern).matcher(path).find()
        }

        // If string is too short - no need to perform expensive string operations
        if (path.length() < pathPattern.length()) {
            return false
        }

        if (path.endsWith(".class")) {
            // Safeguard against strings containing only ".class"
            if (path.length() == 6) {
                return false
            }
            path = path.substring(0, path.length() - 6)
        }

        // Allow for annoying option of an extra / on the front of a path. See MSHADE-119 comes from getClass().getResource("/a/b/c.properties").
        boolean pathStartsWithPattern =
                path.charAt(0) == '/' ? path.startsWith(pathPattern, 1) : path.startsWith(pathPattern)
        if (pathStartsWithPattern) {
            return isIncluded(path) && !isExcluded(path)
        }
        return false
    }

    boolean canRelocateClass(String className) {
        return !rawString &&
                className.indexOf('/') < 0 &&
                canRelocatePath(className.replace('.', '/'))
    }

    boolean canRelocateSourceFile(String sourceFilePath) {
        return !isExcludedSource(sourceFilePath)
    }

    String relocatePath(RelocatePathContext context) {
        String path = context.path
        context.stats.relocate(pathPattern, shadedPathPattern)
        if (rawString) {
            return path.replaceAll(pathPattern, shadedPathPattern)
        } else {
            return path.replaceFirst(pathPattern, shadedPathPattern)
        }
    }

    String relocateClass(RelocateClassContext context) {
        String clazz = context.className
        context.stats.relocate(pathPattern, shadedPathPattern)
        return clazz.replaceFirst(pattern, shadedPattern)
    }

    String applyToSourceContent(String sourceContent) {
        if (rawString) {
            return sourceContent
        } else {
            return sourceContent.replaceAll("\\b" + pattern, shadedPattern)
        }
    }

    @Input
    @Optional
    String getPattern() {
        return pattern
    }

    @Input
    String getPathPattern() {
        return pathPattern
    }

    @Input
    @Optional
    String getShadedPattern() {
        return shadedPattern
    }

    @Input
    String getShadedPathPattern() {
        return shadedPathPattern
    }

    @Input
    Set<String> getIncludes() {
        return includes
    }

    @Input
    Set<String> getExcludes() {
        return excludes
    }

    @Input
    boolean getRawString() {
        return rawString
    }

    @Input
    Set<String> getExcludeSources() {
        return excludeSources
    }

    @Override
    public String toString() {
        return String.format("Relocate[%s->%s][%s]", pattern, shadedPattern, excludeSources)
    }
}
