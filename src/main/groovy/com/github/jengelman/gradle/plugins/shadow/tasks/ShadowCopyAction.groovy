package com.github.jengelman.gradle.plugins.shadow.tasks

import com.github.jengelman.gradle.plugins.shadow.ShadowStats
import com.github.jengelman.gradle.plugins.shadow.impl.RelocatorRemapper
import com.github.jengelman.gradle.plugins.shadow.internal.GradleVersionUtil
import com.github.jengelman.gradle.plugins.shadow.internal.UnusedTracker
import com.github.jengelman.gradle.plugins.shadow.internal.ZipCompressor
import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
import com.github.jengelman.gradle.plugins.shadow.transformers.Transformer
import com.github.jengelman.gradle.plugins.shadow.transformers.TransformerContext
import groovy.util.logging.Slf4j
import org.apache.commons.io.FilenameUtils
import org.apache.commons.io.IOUtils
import org.apache.tools.zip.UnixStat
import org.apache.tools.zip.Zip64RequiredException
import org.apache.tools.zip.ZipEntry
import org.apache.tools.zip.ZipFile
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.UncheckedIOException
import org.gradle.api.file.FileCopyDetails
import org.gradle.api.file.FileTreeElement
import org.gradle.api.file.RelativePath
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.internal.file.CopyActionProcessingStreamAction
import org.gradle.api.internal.file.DefaultFileTreeElement
import org.gradle.api.internal.file.copy.CopyAction
import org.gradle.api.internal.file.copy.CopyActionProcessingStream
import org.gradle.api.internal.file.copy.FileCopyDetailsInternal
import org.gradle.api.specs.Spec
import org.gradle.api.tasks.WorkResult
import org.gradle.api.tasks.WorkResults
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.util.PatternSet
import org.gradle.internal.UncheckedException
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.commons.ClassRemapper

import java.util.zip.ZipException

@Slf4j
class ShadowCopyAction implements CopyAction {
    static final long CONSTANT_TIME_FOR_ZIP_ENTRIES = (new GregorianCalendar(1980, 1, 1, 0, 0, 0)).getTimeInMillis()

    private final File zipFile
    private final ZipCompressor compressor
    private final DocumentationRegistry documentationRegistry
    private final List<Transformer> transformers
    private final List<Relocator> relocators
    private final PatternSet patternSet
    private final ShadowStats stats
    private final String encoding
    private final GradleVersionUtil versionUtil
    private final boolean preserveFileTimestamps
    private final boolean minimizeJar
    private final UnusedTracker unusedTracker

    ShadowCopyAction(File zipFile, ZipCompressor compressor, DocumentationRegistry documentationRegistry,
                            String encoding, List<Transformer> transformers, List<Relocator> relocators,
                            PatternSet patternSet, ShadowStats stats, GradleVersionUtil util,
                            boolean preserveFileTimestamps, boolean minimizeJar, UnusedTracker unusedTracker) {

        this.zipFile = zipFile
        this.compressor = compressor
        this.documentationRegistry = documentationRegistry
        this.transformers = transformers
        this.relocators = relocators
        this.patternSet = patternSet
        this.stats = stats
        this.encoding = encoding
        this.versionUtil = util
        this.preserveFileTimestamps = preserveFileTimestamps
        this.minimizeJar = minimizeJar
        this.unusedTracker = unusedTracker
    }

    @Override
    WorkResult execute(CopyActionProcessingStream stream) {
        Set<String> unusedClasses
        if (minimizeJar) {
            stream.process(new BaseStreamAction() {
                @Override
                void visitFile(FileCopyDetails fileDetails) {
                    // All project sources are already present, we just need
                    // to deal with JAR dependencies.
                    if (isArchive(fileDetails)) {
                        unusedTracker.addDependency(fileDetails.file)
                    }
                }
            })
            unusedClasses = unusedTracker.findUnused()
        } else {
            unusedClasses = Collections.emptySet()
        }

        ZipOutputStream zipOutStr

        try {
            zipOutStr = compressor.createArchiveOutputStream(zipFile)
        } catch (Exception e) {
            throw new GradleException("Could not create ZIP '${zipFile.toString()}'", e)
        }

        try {
            withResource(zipOutStr, new Action<ZipOutputStream>() {
                void execute(ZipOutputStream outputStream) {
                    try {
                        stream.process(new StreamAction(outputStream, encoding, transformers, relocators, patternSet,
                                unusedClasses, stats))
                        processTransformers(outputStream)
                    } catch (Exception e) {
                        log.error('ex', e)
                        //TODO this should not be rethrown
                        throw e
                    }
                }
            })
        } catch (UncheckedIOException e) {
            if (e.cause instanceof Zip64RequiredException) {
                throw new Zip64RequiredException(
                        String.format("%s\n\nTo build this archive, please enable the zip64 extension.\nSee: %s",
                                e.cause.message, documentationRegistry.getDslRefForProperty(Zip, "zip64"))
                )
            }
        }
        return WorkResults.didWork(true)
    }

    private void processTransformers(ZipOutputStream stream) {
        transformers.each { Transformer transformer ->
            if (transformer.hasTransformedResource()) {
                transformer.modifyOutputStream(stream, preserveFileTimestamps)
            }
        }
    }

    private long getArchiveTimeFor(long timestamp) {
        return preserveFileTimestamps ? timestamp : CONSTANT_TIME_FOR_ZIP_ENTRIES
    }

    private ZipEntry setArchiveTimes(ZipEntry zipEntry) {
        if (!preserveFileTimestamps) {
            zipEntry.setTime(CONSTANT_TIME_FOR_ZIP_ENTRIES)
        }
        return zipEntry
    }

    private static <T extends Closeable> void withResource(T resource, Action<? super T> action) {
        try {
            action.execute(resource)
        } catch(Throwable t) {
            try {
                resource.close()
            } catch (IOException e) {
                // Ignored
            }
            throw UncheckedException.throwAsUncheckedException(t)
        }

        try {
            resource.close()
        } catch (IOException e) {
            throw new UncheckedIOException(e)
        }
    }

    abstract class BaseStreamAction implements CopyActionProcessingStreamAction {
        protected boolean isArchive(FileCopyDetails fileDetails) {
            return fileDetails.relativePath.pathString.endsWith('.jar')
        }

        protected boolean isClass(FileCopyDetails fileDetails) {
            return FilenameUtils.getExtension(fileDetails.path) == 'class'
        }

        @Override
        void processFile(FileCopyDetailsInternal details) {
            if (details.directory) {
                visitDir(details)
            } else {
                visitFile(details)
            }
        }

        protected void visitDir(FileCopyDetails dirDetails) {}

        protected abstract void visitFile(FileCopyDetails fileDetails)
    }

    private class StreamAction extends BaseStreamAction {

        private final ZipOutputStream zipOutStr
        private final List<Transformer> transformers
        private final List<Relocator> relocators
        private final RelocatorRemapper remapper
        private final PatternSet patternSet
        private final Set<String> unused
        private final ShadowStats stats

        private Set<String> visitedFiles = new HashSet<String>()

        StreamAction(ZipOutputStream zipOutStr, String encoding, List<Transformer> transformers,
                            List<Relocator> relocators, PatternSet patternSet, Set<String> unused,
                            ShadowStats stats) {
            this.zipOutStr = zipOutStr
            this.transformers = transformers
            this.relocators = relocators
            this.remapper = new RelocatorRemapper(relocators, stats)
            this.patternSet = patternSet
            this.unused = unused
            this.stats = stats
            if(encoding != null) {
                this.zipOutStr.setEncoding(encoding)
            }
        }

        private boolean recordVisit(RelativePath path) {
            return visitedFiles.add(path.pathString)
        }

        @Override
        void visitFile(FileCopyDetails fileDetails) {
            if (!isArchive(fileDetails)) {
                try {
                    boolean isClass = isClass(fileDetails)
                    if (!remapper.hasRelocators() || !isClass) {
                        if (!isTransformable(fileDetails)) {
                            String mappedPath = remapper.map(fileDetails.relativePath.pathString)
                            ZipEntry archiveEntry = new ZipEntry(mappedPath)
                            archiveEntry.setTime(getArchiveTimeFor(fileDetails.lastModified))
                            archiveEntry.unixMode = (UnixStat.FILE_FLAG | fileDetails.mode)
                            zipOutStr.putNextEntry(archiveEntry)
                            fileDetails.copyTo(zipOutStr)
                            zipOutStr.closeEntry()
                        } else {
                            transform(fileDetails)
                        }
                    } else if (isClass && !isUnused(fileDetails.path)) {
                        remapClass(fileDetails)
                    }
                    recordVisit(fileDetails.relativePath)
                } catch (Exception e) {
                    throw new GradleException(String.format("Could not add %s to ZIP '%s'.", fileDetails, zipFile), e)
                }
            } else {
                processArchive(fileDetails)
            }
        }

        private void processArchive(FileCopyDetails fileDetails) {
            stats.startJar()
            ZipFile archive = new ZipFile(fileDetails.file)
            try {
                List<ArchiveFileTreeElement> archiveElements = archive.entries.collect {
                    new ArchiveFileTreeElement(new RelativeArchivePath(it))
                }
                Spec<FileTreeElement> patternSpec = patternSet.getAsSpec()
                List<ArchiveFileTreeElement> filteredArchiveElements = archiveElements.findAll { ArchiveFileTreeElement archiveElement ->
                    patternSpec.isSatisfiedBy(archiveElement.asFileTreeElement())
                }
                filteredArchiveElements.each { ArchiveFileTreeElement archiveElement ->
                    if (archiveElement.relativePath.file) {
                        visitArchiveFile(archiveElement, archive)
                    }
                }
            } finally {
                archive.close()
            }
            stats.finishJar()
        }

        private void visitArchiveDirectory(RelativeArchivePath archiveDir) {
            if (recordVisit(archiveDir)) {
                zipOutStr.putNextEntry(archiveDir.entry)
                zipOutStr.closeEntry()
            }
        }

        private void visitArchiveFile(ArchiveFileTreeElement archiveFile, ZipFile archive) {
            def archiveFilePath = archiveFile.relativePath
            if (archiveFile.classFile || !isTransformable(archiveFile)) {
                if (recordVisit(archiveFilePath) && !isUnused(archiveFilePath.entry.name)) {
                    if (!remapper.hasRelocators() || !archiveFile.classFile) {
                        copyArchiveEntry(archiveFilePath, archive)
                    } else {
                        remapClass(archiveFilePath, archive)
                    }
                }
            } else {
                transform(archiveFile, archive)
            }
        }

        private void addParentDirectories(RelativeArchivePath file) {
            if (file) {
                addParentDirectories(file.parent)
                if (!file.file) {
                    visitArchiveDirectory(file)
                }
            }
        }

        private boolean isUnused(String classPath) {
            final String className = FilenameUtils.removeExtension(classPath)
                    .replace('/' as char, '.' as char)
            final boolean result = unused.contains(className)
            if (result) {
                log.debug("Dropping unused class: $className")
            }
            return result
        }

        private void remapClass(RelativeArchivePath file, ZipFile archive) {
            if (file.classFile) {
                ZipEntry zipEntry = setArchiveTimes(new ZipEntry(remapper.mapPath(file) + '.class'))
                addParentDirectories(new RelativeArchivePath(zipEntry))
                remapClass(archive.getInputStream(file.entry), file.pathString, file.entry.time)
            }
        }

        private void remapClass(FileCopyDetails fileCopyDetails) {
            if (FilenameUtils.getExtension(fileCopyDetails.name) == 'class') {
                InputStream is = fileCopyDetails.file.newInputStream()
                try {
                    remapClass(is, fileCopyDetails.path, fileCopyDetails.lastModified)
                } finally {
                    is.close()
                }
            }
        }

        /**
         * Applies remapping to the given class with the specified relocation path. The remapped class is then written
         * to the zip file. <code>classInputStream</code> is closed automatically to prevent future file leaks.
         * See #364 and #408.
         */
        private void remapClass(InputStream classInputStream, String path, long lastModified) {
            InputStream is = classInputStream
            ClassReader cr = new ClassReader(is)

            // We don't pass the ClassReader here. This forces the ClassWriter to rebuild the constant pool.
            // Copying the original constant pool should be avoided because it would keep references
            // to the original class names. This is not a problem at runtime (because these entries in the
            // constant pool are never used), but confuses some tools such as Felix' maven-bundle-plugin
            // that use the constant pool to determine the dependencies of a class.
            ClassWriter cw = new ClassWriter(0)

            remapper.currentFilePath = path
            ClassVisitor cv = new ClassRemapper(cw, remapper)

            try {
                cr.accept(cv, ClassReader.EXPAND_FRAMES)
            } catch (Throwable ise) {
                throw new GradleException("Error in ASM processing class " + path, ise)
            } finally {
                is.close()
            }

            byte[] renamedClass = cw.toByteArray()

            // Temporarily remove the multi-release prefix.
            String multiReleasePrefix = path.find("^META-INF/versions/\\d+/") ?: ""
            path = path.replace(multiReleasePrefix, "")
            String mappedName = multiReleasePrefix + remapper.mapPath(path)

            InputStream bis = new ByteArrayInputStream(renamedClass)
            try {
                // Now we put it back on so the class file is written out with the right extension.
                ZipEntry archiveEntry = new ZipEntry(mappedName + ".class")
                archiveEntry.setTime(getArchiveTimeFor(lastModified))
                zipOutStr.putNextEntry(archiveEntry)
                IOUtils.copyLarge(bis, zipOutStr)
                zipOutStr.closeEntry()
            } catch (ZipException e) {
                log.warn("We have a duplicate " + mappedName + " in source project")
            } finally {
                bis.close()
            }
        }

        private void copyArchiveEntry(RelativeArchivePath archiveFile, ZipFile archive) {
            String mappedPath = remapper.map(archiveFile.entry.name)
            ZipEntry entry = new ZipEntry(mappedPath)
            entry.setTime(getArchiveTimeFor(archiveFile.entry.time))
            RelativeArchivePath mappedFile = new RelativeArchivePath(entry)
            addParentDirectories(mappedFile)
            zipOutStr.putNextEntry(mappedFile.entry)
            InputStream is = archive.getInputStream(archiveFile.entry)
            try {
                IOUtils.copyLarge(is, zipOutStr)
            } finally {
                is.close()
            }
            zipOutStr.closeEntry()
        }

        @Override
        protected void visitDir(FileCopyDetails dirDetails) {
            try {
                // Trailing slash in name indicates that entry is a directory
                String path = dirDetails.relativePath.pathString + '/'
                ZipEntry archiveEntry = new ZipEntry(path)
                archiveEntry.setTime(getArchiveTimeFor(dirDetails.lastModified))
                archiveEntry.unixMode = (UnixStat.DIR_FLAG | dirDetails.mode)
                zipOutStr.putNextEntry(archiveEntry)
                zipOutStr.closeEntry()
                recordVisit(dirDetails.relativePath)
            } catch (Exception e) {
                throw new GradleException(String.format("Could not add %s to ZIP '%s'.", dirDetails, zipFile), e)
            }
        }

        private void transform(ArchiveFileTreeElement element, ZipFile archive) {
            transformAndClose(element, archive.getInputStream(element.relativePath.entry))
        }

        private void transform(FileCopyDetails details) {
            transformAndClose(details, details.file.newInputStream())
        }

        private void transformAndClose(FileTreeElement element, InputStream is) {
            try {
                String mappedPath = remapper.map(element.relativePath.pathString)
                transformers.find { it.canTransformResource(element) }.transform(
                        TransformerContext.builder()
                                .path(mappedPath)
                                .is(is)
                                .relocators(relocators)
                                .stats(stats)
                                .build()
                )
            } finally {
                is.close()
            }
        }

        private boolean isTransformable(FileTreeElement element) {
            return transformers.any { it.canTransformResource(element) }
        }

    }

    class RelativeArchivePath extends RelativePath {

        ZipEntry entry

        RelativeArchivePath(ZipEntry entry) {
            super(!entry.directory, entry.name.split('/'))
            this.entry = entry
        }

        boolean isClassFile() {
            return lastName.endsWith('.class')
        }

        RelativeArchivePath getParent() {
            if (!segments || segments.length == 1) {
                return null
            } else {
                //Parent is always a directory so add / to the end of the path
                String path = segments[0..-2].join('/') + '/'
                return new RelativeArchivePath(setArchiveTimes(new ZipEntry(path)))
            }
        }
    }

    class ArchiveFileTreeElement implements FileTreeElement {

        private final RelativeArchivePath archivePath

        ArchiveFileTreeElement(RelativeArchivePath archivePath) {
            this.archivePath = archivePath
        }

        boolean isClassFile() {
            return archivePath.classFile
        }

        @Override
        File getFile() {
            return null
        }

        @Override
        boolean isDirectory() {
            return archivePath.entry.directory
        }

        @Override
        long getLastModified() {
            return archivePath.entry.lastModifiedDate.time
        }

        @Override
        long getSize() {
            return archivePath.entry.size
        }

        @Override
        InputStream open() {
            return null
        }

        @Override
        void copyTo(OutputStream outputStream) {

        }

        @Override
        boolean copyTo(File file) {
            return false
        }

        @Override
        String getName() {
            return archivePath.pathString
        }

        @Override
        String getPath() {
            return archivePath.lastName
        }

        @Override
        RelativeArchivePath getRelativePath() {
            return archivePath
        }

        @Override
        int getMode() {
            return archivePath.entry.unixMode
        }

        FileTreeElement asFileTreeElement() {
            return new DefaultFileTreeElement(null, new RelativePath(!isDirectory(), archivePath.segments), null, null)
        }
    }
}
