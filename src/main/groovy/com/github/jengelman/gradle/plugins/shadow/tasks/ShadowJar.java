package com.github.jengelman.gradle.plugins.shadow.tasks;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;

import javax.annotation.Nonnull;

import com.github.jengelman.gradle.plugins.shadow.ShadowStats;
import com.github.jengelman.gradle.plugins.shadow.internal.DefaultDependencyFilter;
import com.github.jengelman.gradle.plugins.shadow.internal.DependencyFilter;
import com.github.jengelman.gradle.plugins.shadow.internal.GradleVersionUtil;
import com.github.jengelman.gradle.plugins.shadow.internal.MinimizeDependencyFilter;
import com.github.jengelman.gradle.plugins.shadow.internal.RelocationUtil;
import com.github.jengelman.gradle.plugins.shadow.internal.UnusedTracker;
import com.github.jengelman.gradle.plugins.shadow.internal.ZipCompressor;
import com.github.jengelman.gradle.plugins.shadow.relocation.CacheableRelocator;
import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator;
import com.github.jengelman.gradle.plugins.shadow.relocation.SimpleRelocator;
import com.github.jengelman.gradle.plugins.shadow.transformers.AppendingTransformer;
import com.github.jengelman.gradle.plugins.shadow.transformers.CacheableTransformer;
import com.github.jengelman.gradle.plugins.shadow.transformers.GroovyExtensionModuleTransformer;
import com.github.jengelman.gradle.plugins.shadow.transformers.ServiceFileTransformer;
import com.github.jengelman.gradle.plugins.shadow.transformers.StandardFilesMergeTransformer;
import com.github.jengelman.gradle.plugins.shadow.transformers.Transformer;
import org.gradle.api.Action;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DuplicatesStrategy;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.copy.CopyAction;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.util.PatternSet;

@CacheableTask
public class ShadowJar extends Jar implements ShadowSpec {

    private List<Transformer> transformers;
    private List<Relocator> relocators;
    private List<FileCollection> configurations;
    private transient DependencyFilter dependencyFilter;
    private boolean enableRelocation;
    private String relocationPrefix = "shadow";
    private boolean minimizeJar;
    private final transient DependencyFilter dependencyFilterForMinimize;
    private FileCollection toMinimize;
    private FileCollection apiJars;
    private FileCollection sourceSetsClassesDirs;

    private final ShadowStats shadowStats = new ShadowStats();

    private final ConfigurableFileCollection includedDependencies = getProject().files(new Callable<FileCollection>() {

        @Override
        public FileCollection call() {
            return dependencyFilter.resolve(configurations);
        }
    });

    public ShadowJar() {
        super();
        setDuplicatesStrategy(DuplicatesStrategy.INCLUDE); //shadow filters out files later. This was the default behavior in  Gradle < 6.x
        dependencyFilter = new DefaultDependencyFilter(getProject());
        dependencyFilterForMinimize = new MinimizeDependencyFilter(getProject());
        setManifest(new DefaultInheritManifest(getProject(), getServices().get(FileResolver.class)));
        /*
         Add as default the StandardFilesMergeTransformer, remove it with "removeDefaultTransformers()".
         This is added by default, because otherwise:
         a) In projects with many dependencies the user gets flooded with information about duplicated entries
            like "META-INF/notice.txt", "META-INF/license.txt"...
         b) Important licensing information written in META-INF/license.txt and other files may be lost.
         c) Helpful information written in readme files may be lost.
         d) The merging of plain text files is safe, there is no important logic to follow. Not like MANIFEST.MF,
            property files, xml files, etc. Merged HTML may not look that good, but it works.
         */
        transformers = new ArrayList<>(Collections.singletonList(new StandardFilesMergeTransformer()));
        relocators = new ArrayList<>();
        configurations = new ArrayList<>();

        this.getInputs().property("minimize", (Callable<Boolean>) () -> minimizeJar);
        this.getOutputs().doNotCacheIf("Has one or more transforms or relocators that are not cacheable", task -> {
            for (Transformer transformer : transformers) {
                if (!isCacheableTransform(transformer.getClass())) {
                    return true;
                }
            }
            for (Relocator relocator : relocators) {
                if (!isCacheableRelocator(relocator.getClass())) {
                    return true;
                }
            }
            return false;
        });
    }

    public ShadowJar minimize() {
        minimizeJar = true;
        return this;
    }

    public ShadowJar minimize(Action<DependencyFilter> c) {
        minimize();
        if (c != null) {
            c.execute(dependencyFilterForMinimize);
        }
        return this;
    }

    @Override
    @Internal
    public ShadowStats getStats() {
        return shadowStats;
    }

    @Override
    public InheritManifest getManifest() {
        return (InheritManifest) super.getManifest();
    }

    @Override
    @Nonnull
    protected CopyAction createCopyAction() {
        DocumentationRegistry documentationRegistry = getServices().get(DocumentationRegistry.class);
        final UnusedTracker unusedTracker = minimizeJar ? UnusedTracker.forProject(getApiJars(),
                getSourceSetsClassesDirs().getFiles(), getToMinimize()) : null;
        return new ShadowCopyAction(getArchiveFile().get().getAsFile(), getInternalCompressor(), documentationRegistry,
                this.getMetadataCharset(), transformers, relocators, getRootPatternSet(), shadowStats,
                isPreserveFileTimestamps(), minimizeJar, unusedTracker);
    }

    @Classpath
    FileCollection getToMinimize() {
        if (toMinimize == null) {
            toMinimize = minimizeJar
                    ? dependencyFilterForMinimize.resolve(configurations).minus(getApiJars())
                    : getProject().getObjects().fileCollection();
        }
        return toMinimize;
    }

    @Classpath
    FileCollection getApiJars() {
        if (apiJars == null) {
            apiJars = minimizeJar
                    ? UnusedTracker.getApiJarsFromProject(getProject())
                    : getProject().getObjects().fileCollection();
        }
        return apiJars;
    }

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    FileCollection getSourceSetsClassesDirs() {
        if (sourceSetsClassesDirs == null) {
            ConfigurableFileCollection allClassesDirs = getProject().getObjects().fileCollection();
            if (minimizeJar) {
                for (SourceSet sourceSet : getProject().getExtensions().getByType(SourceSetContainer.class)) {
                    FileCollection classesDirs = sourceSet.getOutput().getClassesDirs();
                    allClassesDirs.from(classesDirs);
                }
            }
            sourceSetsClassesDirs = allClassesDirs.filter(File::isDirectory);
        }
        return sourceSetsClassesDirs;
    }

    @Internal
    protected ZipCompressor getInternalCompressor() {
        return GradleVersionUtil.getInternalCompressor(getEntryCompression(), this);
    }

    @TaskAction
    protected void copy() {
        if (enableRelocation) {
            RelocationUtil.configureRelocation(this, relocationPrefix);
        }
        from(getIncludedDependencies());
        super.copy();
        getLogger().info(shadowStats.toString());
    }

    @Classpath
    public FileCollection getIncludedDependencies() {
        return includedDependencies;
    }

    /**
     * Utility method for assisting between changes in Gradle 1.12 and 2.x.
     *
     * @return this
     */
    @Internal
    protected PatternSet getRootPatternSet() {
        return GradleVersionUtil.getRootPatternSet(getMainSpec());
    }

    /**
     * Configure inclusion/exclusion of module and project dependencies into uber jar.
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

    /**
     * Add a Transformer instance for modifying JAR resources and configure.
     *
     * @param clazz the transformer to add. Must have a no-arg constructor
     * @return this
     */
    public ShadowJar transform(Class<? extends Transformer> clazz) throws InstantiationException, IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        return transform(clazz, null);
    }

    /**
     * Add a Transformer instance for modifying JAR resources and configure.
     *
     * @param clazz the transformer class to add. Must have no-arg constructor
     * @param c the configuration for the transformer
     * @return this
     */
    public <T extends Transformer> ShadowJar transform(Class<T> clazz, Action<T> c)
            throws InstantiationException, IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        T transformer = clazz.getDeclaredConstructor().newInstance();
        addTransform(transformer, c);
        return this;
    }

    /**
     * Removes all default transformers.
     * <br>Right now only {@link StandardFilesMergeTransformer} is added as default transformer, this method removes
     * it.
     *
     * @return this
     */
    public ShadowJar removeDefaultTransformers() {
        final java.util.Optional<Transformer> standardFilesMergeTransformer = transformers.stream() //
                .filter(StandardFilesMergeTransformer.class::isInstance) //
                .findAny();
        standardFilesMergeTransformer.ifPresent(transformer -> transformers.remove(transformer));
        return this;
    }

    private boolean isCacheableTransform(Class<? extends Transformer> clazz) {
        return clazz.isAnnotationPresent(CacheableTransformer.class);
    }

    /**
     * Add a preconfigured transformer instance.
     *
     * @param transformer the transformer instance to add
     * @return this
     */
    public ShadowJar transform(Transformer transformer) {
        addTransform(transformer, null);
        return this;
    }

    private <T extends Transformer> void addTransform(T transformer, Action<T> c) {
        if (c != null) {
            c.execute(transformer);
        }

        transformers.add(transformer);
    }

    /**
     * Syntactic sugar for merging service files in JARs.
     *
     * @return this
     */
    public ShadowJar mergeServiceFiles() {
        try {
            transform(ServiceFileTransformer.class);
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException |
                 InstantiationException ignored) {
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
            transform(ServiceFileTransformer.class, serviceFileTransformer -> serviceFileTransformer.setPath(rootPath));
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException |
                 InstantiationException ignored) {
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
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException |
                 InstantiationException ignored) {
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
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException |
                 InstantiationException ignored) {
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
            transform(AppendingTransformer.class, transformer -> transformer.setResource(resourcePath));
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException |
                 InstantiationException ignored) {
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
        SimpleRelocator relocator = new SimpleRelocator(pattern, destination, new ArrayList<>(), new ArrayList<>());
        addRelocator(relocator, configure);
        return this;
    }

    /**
     * Add a relocator instance.
     *
     * @param relocator the relocator instance to add
     * @return this
     */
    public ShadowJar relocate(Relocator relocator) {
        addRelocator(relocator, null);
        return this;
    }

    /**
     * Add a relocator of the provided class.
     *
     * @param relocatorClass the relocator class to add. Must have a no-arg constructor.
     * @return this
     */
    public ShadowJar relocate(Class<? extends Relocator> relocatorClass)
            throws InstantiationException, IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        return relocate(relocatorClass, null);
    }

    private <R extends Relocator> void addRelocator(R relocator, Action<R> configure) {
        if (configure != null) {
            configure.execute(relocator);
        }

        relocators.add(relocator);
    }

    /**
     * Add a relocator of the provided class and configure.
     *
     * @param relocatorClass the relocator class to add. Must have a no-arg constructor
     * @param configure the configuration for the relocator
     * @return this
     */
    public <R extends Relocator> ShadowJar relocate(Class<R> relocatorClass, Action<R> configure)
            throws InstantiationException, IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        R relocator = relocatorClass.getDeclaredConstructor().newInstance();
        addRelocator(relocator, configure);
        return this;
    }

    private boolean isCacheableRelocator(Class<? extends Relocator> relocatorClass) {
        return relocatorClass.isAnnotationPresent(CacheableRelocator.class);
    }

    @Nested
    public List<Transformer> getTransformers() {
        return this.transformers;
    }

    public void setTransformers(List<Transformer> transformers) {
        this.transformers = transformers;
    }

    @Nested
    public List<Relocator> getRelocators() {
        return this.relocators;
    }

    public void setRelocators(List<Relocator> relocators) {
        this.relocators = relocators;
    }

    @Classpath @Optional
    public List<FileCollection> getConfigurations() {
        return this.configurations;
    }

    public void setConfigurations(List<FileCollection> configurations) {
        this.configurations = configurations;
    }

    @Internal
    public DependencyFilter getDependencyFilter() {
        return this.dependencyFilter;
    }

    public void setDependencyFilter(DependencyFilter filter) {
        this.dependencyFilter = filter;
    }

    @Input
    public boolean isEnableRelocation() {
        return enableRelocation;
    }

    public void setEnableRelocation(boolean enableRelocation) {
        this.enableRelocation = enableRelocation;
    }

    @Input
    public String getRelocationPrefix() {
        return relocationPrefix;
    }

    public void setRelocationPrefix(String relocationPrefix) {
        this.relocationPrefix = relocationPrefix;
    }
}
