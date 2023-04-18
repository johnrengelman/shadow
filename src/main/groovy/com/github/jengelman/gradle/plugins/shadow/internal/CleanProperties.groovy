package com.github.jengelman.gradle.plugins.shadow.internal

class CleanProperties extends Properties {

    private static class StripCommentsWithTimestampBufferedWriter extends BufferedWriter {

        private final int lengthOfExpectedTimestamp

        StripCommentsWithTimestampBufferedWriter(final Writer out) {
            super(out);

            lengthOfExpectedTimestamp = ("#" + new Date().toString()).length()
        }

        @Override
        void write(final String str) throws IOException {
            if (couldBeCommentWithTimestamp(str)) {
                return
            }

            super.write(str)
        }

        private boolean couldBeCommentWithTimestamp(final String str) {
            return str != null &&
                    str.startsWith("#") &&
                    str.length() == lengthOfExpectedTimestamp
        }
    }

    @Override
    void store(final Writer writer, final String comments) throws IOException
    {
        super.store(new StripCommentsWithTimestampBufferedWriter(writer), comments);
    }
}