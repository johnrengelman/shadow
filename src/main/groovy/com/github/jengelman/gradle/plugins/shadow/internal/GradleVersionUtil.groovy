package com.github.jengelman.gradle.plugins.shadow.internal

import org.apache.tools.zip.ZipEntry
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.Action
import org.gradle.api.internal.file.copy.CopySpecInternal
import org.gradle.api.tasks.WorkResult
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.bundling.ZipEntryCompression
import org.gradle.api.tasks.util.PatternSet
import org.gradle.util.GradleVersion

class GradleVersionUtil {

    private final GradleVersion version

    GradleVersionUtil(String version) {
        this.version = GradleVersion.version(version)
    }

    PatternSet getRootPatternSet(CopySpecInternal mainSpec) {
        return mainSpec.buildRootResolver().getPatternSet()
    }

    ZipCompressor getInternalCompressor(ZipEntryCompression entryCompression, Jar jar, List<Action<ZipEntry>> actions) {
        switch (entryCompression) {
            case ZipEntryCompression.DEFLATED:
                return new DefaultZipCompressor(jar.zip64, ZipOutputStream.DEFLATED, actions);
            case ZipEntryCompression.STORED:
                return new DefaultZipCompressor(jar.zip64, ZipOutputStream.STORED, actions);
            default:
                throw new IllegalArgumentException(String.format("Unknown Compression type %s", entryCompression));
        }
    }

    @SuppressWarnings("deprecation")
    WorkResult getWorkResult(boolean didWork) {
        if (version < GradleVersion.version("4.2")) {
            return new org.gradle.api.internal.tasks.SimpleWorkResult(didWork)
        } else {
            org.gradle.api.tasks.WorkResults.didWork(didWork)
        }
    }
}
