package com.github.jengelman.gradle.plugins.shadow.docs.extractor

import com.github.jengelman.gradle.plugins.shadow.docs.internal.snippets.TestCodeSnippet
import com.github.jengelman.gradle.plugins.shadow.docs.internal.snippets.executer.ExceptionTransformer
import com.github.jengelman.gradle.plugins.shadow.docs.internal.snippets.executer.SnippetExecuter
import groovy.ant.FileNameFinder

import java.util.regex.Pattern

class ManualSnippetExtractor {

    static List<TestCodeSnippet> extract(File root, String cssClass, SnippetExecuter executer) {
        List<TestCodeSnippet> snippets = []

        def snippetBlockPattern = Pattern.compile(/(?ims)```$cssClass\n(.*?)\n```/)
        def filenames = new FileNameFinder().getFileNames(root.absolutePath, "**/*.md")

        filenames.each { filename ->
            def file = new File(filename)
            addSnippets(snippets, file, snippetBlockPattern, executer)
        }

        snippets
    }

    private static void addSnippets(List<TestCodeSnippet> snippets, File file, Pattern snippetBlockPattern, SnippetExecuter executer) {
        def source = file.text
        String testName = file.parentFile.name + "/" +file.name
        Map<Integer, String> snippetsByLine = findSnippetsByLine(source, snippetBlockPattern)

        snippetsByLine.each { lineNumber, snippet ->
            snippets << createSnippet(testName, file, lineNumber, snippet, executer)
        }
    }

    private static List<String> findSnippetBlocks(String code, Pattern snippetTagPattern) {
        List<String> tags = []
        code.eachMatch(snippetTagPattern) { matches ->
            tags.add(matches[0])
        }
        tags
    }

    private static Map<Integer, String> findSnippetsByLine(String source, Pattern snippetTagPattern) {
        List<String> snippetBlocks = findSnippetBlocks(source, snippetTagPattern)
        Map snippetBlocksByLine = [:]

        int codeIndex = 0
        snippetBlocks.each { block ->
            codeIndex = source.indexOf(block, codeIndex)
            def lineNumber = source.substring(0, codeIndex).readLines().size() + 2
            snippetBlocksByLine.put(lineNumber, extractSnippetFromBlock(block))
            codeIndex += block.size()
        }

        snippetBlocksByLine
    }

    private static String extractSnippetFromBlock(String tag) {
        tag.substring(tag.indexOf("\n") + 1, tag.lastIndexOf("\n"))
    }

    private static TestCodeSnippet createSnippet(String sourceClassName, File sourceFile, int lineNumber, String snippet, SnippetExecuter executer) {
        new TestCodeSnippet(snippet, sourceClassName, sourceClassName + ":$lineNumber", executer, new ExceptionTransformer(sourceClassName, sourceFile.name, lineNumber))
    }

}
