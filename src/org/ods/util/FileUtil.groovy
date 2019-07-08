package org.ods.util

class FileUtil {
    static File createTempFile(String baseDir, String prefix, String suffix, byte[] data) {
        def tmpFile = null

        try {
            // Create a directory in the workspace
            def dir = new File(baseDir)
            dir.mkdirs()

            // Create a temporary file containing data
            tmpFile = File.createTempFile("${prefix}-", "-${suffix}", dir) << data
        } catch (Throwable e) {
            throw e

            if (tmpFile) {
                tmpFile.delete()
            }
        }

        return tmpFile
    }
}
