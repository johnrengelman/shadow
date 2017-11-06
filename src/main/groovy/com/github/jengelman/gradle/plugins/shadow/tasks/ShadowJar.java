package com.github.jengelman.gradle.plugins.shadow.tasks;

import com.github.jengelman.gradle.plugins.shadow.ShadowStats;
import com.github.jengelman.gradle.plugins.shadow.internal.*;
import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator;
import com.github.jengelman.gradle.plugins.shadow.relocation.SimpleRelocator;
import com.github.jengelman.gradle.plugins.shadow.transformers.AppendingTransformer;
import com.github.jengelman.gradle.plugins.shadow.transformers.GroovyExtensionModuleTransformer;
import com.github.jengelman.gradle.plugins.shadow.transformers.ServiceFileTransformer;
import com.github.jengelman.gradle.plugins.shadow.transformers.Transformer;
import groovy.lang.MetaClass;
import org.apache.tools.zip.ZipEntry;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.gradle.api.Action;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.copy.CopyAction;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.util.PatternSet;

import java.util.ArrayList;
import java.util.List;

public class ShadowJar extends Jar implements ShadowSpec {

    private List<Transformer> transformers;
    private List<Action<ZipEntry>> zipEntryActions;
    private List<Relocator> relocators;
    private List<Configuration> configurations;
    private DependencyFilter dependencyFilter;

    private final ShadowStats shadowStats = new ShadowStats();
    private final GradleVersionUtil versionUtil;

    public ShadowJar() {
        super();
        versionUtil = new GradleVersionUtil(getProject().getGradle().getGradleVersion());
        dependencyFilter = new DefaultDependencyFilter(getProject());
        setManifest(new DefaultInheritManifest(getServices().get(FileResolver.class)));
        transformers = new ArrayList<>();
        zipEntryActions = new ArrayList<>();
        relocators = new ArrayList<>();
        configurations = new ArrayList<>();
    }

    @Override
    public ShadowStats getStats() {
        return shadowStats;
    }

    @Override
    public InheritManifest getManifest() {
        return (InheritManifest) super.getManifest();
    }

    @Override
    protected CopyAction createCopyAction() {
        DocumentationRegistry documentationRegistry = getServices().get(DocumentationRegistry.class);
        return new ShadowCopyAction(getArchivePath(), getInternalCompressor(), documentationRegistry,
                this.getMetadataCharset(), transformers, relocators, getRootPatternSet(), shadowStats,
                versionUtil);
    }

    protected ZipCompressor getInternalCompressor() {
        return versionUtil.getInternalCompressor(getEntryCompression(), this, zipEntryActions);
    }

    @TaskAction
    protected void copy() {
        from(getIncludedDependencies());
        super.copy();
        getLogger().info(shadowStats.toString());
    }

    @InputFiles @Optional
    public FileCollection getIncludedDependencies() {
        return new DependencyFileCollection(dependencyFilter, configurations);
    }

    /**
     * Utility method for assisting between changes in Gradle 1.12 and 2.x.
     *
     * @return this
     */
    protected PatternSet getRootPatternSet() {
        return versionUtil.getRootPatternSet(getMainSpec());
    }

    /**
     * Configure inclusion/exclusion of module & project dependencies into uber jar.
     *
     * @param c the configuration of the filter
     * @return this
     */
    public ShadowJar dependencies(Action<DependencyFilter> c) {
        if (c != null) {
            c.execute(dependencyFilter);
        }
        return this;
    }

    public ShadowJar entryAction(Action<ZipEntry> action) {
        zipEntryActions.add(action);
        return this;
    }

    /**
     * Add a Transformer instance for modifying JAR resources and configure.
     *
     * @param clazz the transformer to add. Must have a no-arg constructor
     * @return this
     */
    public ShadowJar transform(Class<? extends Transformer> clazz) throws InstantiationException, IllegalAccessException {
        return transform(clazz, null);
    }

    /**
     * Add a Transformer instance for modifying JAR resources and configure.
     *
     * @param clazz the transformer class to add. Must have no-arg constructor
     * @param c the configuration for the transformer
     * @return this
     */
    public <T extends Transformer> ShadowJar transform(Class<T> clazz, Action<T> c) throws InstantiationException, IllegalAccessException {
        T transformer = clazz.newInstance();
        if (c != null) {
            c.execute(transformer);
        }
        transformers.add(transformer);
        return this;
    }

    /**
     * Add a preconfigured transformer instance.
     *
     * @param transformer the transformer instance to add
     * @return this
     */
    public ShadowJar transform(Transformer transformer) {
        transformers.add(transformer);
        return this;
    }

    /**
     * Act on all zipEntries being added to the final jar.
     *
     * Every {@link ZipEntry} to be added to the final jar will be presented
     * to the {@code zipEntryAction}, which can inspect and modify it as desired.
     * @param zipEntryAction the action instance to add
     * @return this
     */
    public ShadowSpec modifyZipEntries(Action<ZipEntry> zipEntryAction) {
        zipEntryActions.add(zipEntryAction);
        return this;
    }

    private static final long DOS_TIMESTAMP_ZERO = 315558000000L;
    /**
     * Zero out timestamps on zip entries to Jan 1st 1980. (DOS epoch)
     *
     * Why would you want to do this? The timestamps usually are just set to the
     * time the jar was built. However, those timestamps are repeated across every
     * file inside the jar, which scatters byte-level changes throughout the
     * shadow jar, which breaks any efforts to calculate efficient deltas between
     * shadow jar builds (e.g. rsync). With static timestamps though, rsync just works.
     *
     * This is generally safe to turn on because nothing cares about internal timestamps, but it's off by default.
     * @return this
     */
    public ShadowJar zeroZipEntryTimestamps() {
        zipEntryActions.add(new Action<ZipEntry>() {
            @Override
            public void execute(ZipEntry zipEntry) {
                zipEntry.setTime(DOS_TIMESTAMP_ZERO);
            }
        });
        return this;
    }

    /**
     * Syntactic sugar for merging service files in JARs.
     *
     * @return this
     */
    public ShadowJar mergeServiceFiles() {
        try {
            transform(ServiceFileTransformer.class);
        } catch (IllegalAccessException e) {
        } catch (InstantiationException e) {
        }
        return this;
    }

    /**
     * Syntactic sugar for merging service files in JARs.
     *
     * @return this
     */
    public ShadowJar mergeServiceFiles(final String rootPath) {
        try {
            transform(ServiceFileTransformer.class, new Action<ServiceFileTransformer>() {

                @Override
                public void execute(ServiceFileTransformer serviceFileTransformer) {
                    serviceFileTransformer.setPath(rootPath);
                }
            });
        } catch (IllegalAccessException e) {
        } catch (InstantiationException e) {
        }
        return this;
    }

    /**
     * Syntactic sugar for merging service files in JARs.
     *
     * @return this
     */
    public ShadowJar mergeServiceFiles(Action<ServiceFileTransformer> configureClosure) {
        try {
            transform(ServiceFileTransformer.class, configureClosure);
        } catch (IllegalAccessException e) {
        } catch (InstantiationException e) {
        }
        return this;
    }

    /**
     * Syntactic sugar for merging Groovy extension module descriptor files in JARs
     *
     * @return this
     */
    public ShadowJar mergeGroovyExtensionModules() {
        try {
            transform(GroovyExtensionModuleTransformer.class);
        } catch (IllegalAccessException e) {
        } catch (InstantiationException e) {
        }
        return this;
    }

    /**
     * Syntax sugar for merging service files in JARs
     *
     * @return this
     */
    public ShadowJar append(final String resourcePath) {
        try {
            transform(AppendingTransformer.class, new Action<AppendingTransformer>() {
                @Override
                public void execute(AppendingTransformer transformer) {
                    transformer.setResource(resourcePath);
                }
            });
        } catch (IllegalAccessException e) {
        } catch (InstantiationException e) {
        }
        return this;
    }

    /**
     * Add a class relocator that maps each class in the pattern to the provided destination.
     *
     * @param pattern the source pattern to relocate
     * @param destination the destination package
     * @return this
     */
    public ShadowJar relocate(String pattern, String destination) {
        return relocate(pattern, destination, null);
    }

    /**
     * Add a class relocator that maps each class in the pattern to the provided destination.
     *
     * @param pattern the source pattern to relocate
     * @param destination the destination package
     * @param configure the configuration of the relocator
     * @return this
     */
    public ShadowJar relocate(String pattern, String destination, Action<SimpleRelocator> configure) {
        SimpleRelocator relocator = new SimpleRelocator(pattern, destination, new ArrayList<String>(), new ArrayList<String>());
        if (configure != null) {
            configure.execute(relocator);
        }
        relocators.add(relocator);
        return this;
    }

    /**
     * Add a relocator instance.
     *
     * @param relocator the relocator instance to add
     * @return this
     */
    public ShadowJar relocate(Relocator relocator) {
        relocators.add(relocator);
        return this;
    }

    /**
     * Add a relocator of the provided class.
     *
     * @param relocatorClass the relocator class to add. Must have a no-arg constructor.
     * @return this
     */
    public ShadowJar relocate(Class<? extends Relocator> relocatorClass) throws InstantiationException, IllegalAccessException {
        return relocate(relocatorClass, null);
    }

    /**
     * Add a relocator of the provided class and configure.
     *
     * @param relocatorClass the relocator class to add. Must have a no-arg constructor
     * @param configure the configuration for the relocator
     * @return this
     */
    public <R extends Relocator> ShadowJar relocate(Class<R> relocatorClass, Action<R> configure) throws InstantiationException, IllegalAccessException {
        R relocator = relocatorClass.newInstance();
        if (configure != null) {
            configure.execute(relocator);
        }
        relocators.add(relocator);
        return this;
    }

    public List<Transformer> getTransformers() {
        return this.transformers;
    }

    public void setTransformers(List<Transformer> transformers) {
        this.transformers = transformers;
    }

    public List<Relocator> getRelocators() {
        return this.relocators;
    }

    public void setRelocators(List<Relocator> relocators) {
        this.relocators = relocators;
    }

    @InputFiles @Optional
    public List<Configuration> getConfigurations() {
        return this.configurations;
    }

    public void setConfigurations(List<Configuration> configurations) {
        this.configurations = configurations;
    }

    public DependencyFilter getDependencyFilter() {
        return this.dependencyFilter;
    }

    public void setDependencyFilter(DependencyFilter filter) {
        this.dependencyFilter = filter;
    }

    // This code is only to make IntelliJ happy.
    private transient MetaClass metaClass = InvokerHelper.getMetaClass(this.getClass());

    public Object getProperty(String property) {
        return this.getMetaClass().getProperty(this, property);
    }

    public void setProperty(String property, Object newValue) {
        this.getMetaClass().setProperty(this, property, newValue);
    }

    public Object invokeMethod(String name, Object args) {
        return this.getMetaClass().invokeMethod(this, name, args);
    }

    public MetaClass getMetaClass() {
        if(this.metaClass == null) {
            this.metaClass = InvokerHelper.getMetaClass(this.getClass());
        }

        return this.metaClass;
    }

    public void setMetaClass(MetaClass metaClass) {
        this.metaClass = metaClass;
    }
}
