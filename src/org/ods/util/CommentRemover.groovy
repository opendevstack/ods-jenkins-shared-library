package org.ods.util

class CommentRemover {
    /**
     * Removes all commented code from the given string.
     * Handles // single-line, /* block , and multi-line comments.
     */
    static String removeCommentedCode(String content) {
        if (!content) {
            return content
        }
        // Remove block comments (including multi-line)
        def noBlockComments = content.replaceAll(/(?s)\/\*.*?\*\//, "")
        // Remove single-line comments (// ...), but keep code before //
        def noSingleLineComments = noBlockComments.readLines().collect { line ->
            def idx = line.indexOf('//')
            if (idx >= 0) {
                return line.substring(0, idx)
            }
            return line
        }.join('\n')
        // Remove lines that are now empty or whitespace only
        def result = noSingleLineComments.readLines().findAll { it.trim() }.join('\n')
        return result
    }

}
