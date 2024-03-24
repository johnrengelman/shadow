package com.github.jengelman.gradle.plugins.shadow

import groovy.util.logging.Slf4j
import org.gradle.api.GradleException

@Slf4j
class ShadowStats {

    long totalTime
    long jarStartTime
    long jarEndTime
    int jarCount = 1
    boolean processingJar
    Map<String, String> relocations = [:]

    void relocate(String src, String dst) {
        relocations[src] = dst
    }

    String getRelocationString() {
        def maxLength = relocations.keySet().collect { it.length() }.max()
        relocations.collect { k, v -> "${k} ${separator(k, maxLength)} ${v}"}.sort().join("\n")
    }

    static String separator(String key, int max) {
        return "→"
    }

    void startJar() {
        if (processingJar) throw new GradleException("Can only time one entry at a time")
        processingJar = true
        jarStartTime = System.currentTimeMillis()
    }

    void finishJar() {
        if (processingJar) {
            jarEndTime = System.currentTimeMillis()
            jarCount++
            totalTime += jarTiming
            processingJar = false
        }
    }

    void printStats() {
        println this
    }

    long getJarTiming() {
        jarEndTime - jarStartTime
    }

    double getTotalTimeSecs() {
        totalTime / 1000
    }

    double getAverageTimePerJar() {
        totalTime / jarCount
    }

    double getAverageTimeSecsPerJar() {
        averageTimePerJar / 1000
    }
    
    String toString() {
        StringBuilder sb = new StringBuilder()
        sb.append "*******************\n"
        sb.append "GRADLE SHADOW STATS\n"
        sb.append "\n"
        sb.append "Total Jars: $jarCount (includes project)\n"
        sb.append "Total Time: ${totalTimeSecs}s [${totalTime}ms]\n"
        sb.append "Average Time/Jar: ${averageTimeSecsPerJar}s [${averageTimePerJar}ms]\n"
        sb.append "*******************"
    }

    Map<String, String> getBuildScanData() {
        [
                dependencies: jarCount,
                relocations : relocationString,
        ] as Map<String, String>
    }
}
