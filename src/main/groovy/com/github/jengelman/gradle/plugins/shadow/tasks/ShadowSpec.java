package com.github.jengelman.gradle.plugins.shadow.tasks;

import com.github.jengelman.gradle.plugins.shadow.ShadowStats;
import com.github.jengelman.gradle.plugins.shadow.internal.DependencyFilter;
import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator;
import com.github.jengelman.gradle.plugins.shadow.relocation.SimpleRelocator;
import com.github.jengelman.gradle.plugins.shadow.transformers.ServiceFileTransformer;
import com.github.jengelman.gradle.plugins.shadow.transformers.Transformer;
import org.apache.tools.zip.ZipEntry;
import org.gradle.api.Action;
import org.gradle.api.file.CopySpec;

interface ShadowSpec extends CopySpec {

    ShadowSpec dependencies(Action<DependencyFilter> configure);

    ShadowSpec transform(Class<? extends Transformer> clazz) throws InstantiationException, IllegalAccessException;

    <T extends Transformer> ShadowSpec transform(Class<T> clazz, Action<T> configure) throws InstantiationException, IllegalAccessException;

    ShadowSpec transform(Transformer transformer);

    ShadowSpec modifyZipEntries(Action<ZipEntry> zipEntryAction);

    ShadowSpec zeroZipEntryTimestamps();

    ShadowSpec mergeServiceFiles();

    ShadowSpec mergeServiceFiles(String rootPath);

    ShadowSpec mergeServiceFiles(Action<ServiceFileTransformer> configureClosure);

    ShadowSpec mergeGroovyExtensionModules();

    ShadowSpec append(String resourcePath);

    ShadowSpec relocate(String pattern, String destination);

    ShadowSpec relocate(String pattern, String destination, Action<SimpleRelocator> configure);

    ShadowSpec relocate(Relocator relocator);

    ShadowSpec relocate(Class<? extends Relocator> clazz) throws InstantiationException, IllegalAccessException;

    <R extends Relocator> ShadowSpec relocate(Class<R> clazz, Action<R> configure) throws InstantiationException, IllegalAccessException;

    ShadowStats getStats();
}
