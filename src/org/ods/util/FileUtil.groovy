package org.ods.util

@Grab(group="net.lingala.zip4j", module="zip4j", version="2.1.1")

import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters

class FileUtil {
    static File createTempFile(String baseDir, String prefix, String suffix, byte[] data) {
        def tmpFile = null

        try {
            // Create a directory in the workspace
            def dir = new File(baseDir)
            dir.mkdirs()

            // Create a temporary file containing data
            tmpFile = File.createTempFile("${prefix}-", "-${suffix}", dir) << data
        } finally {
            if (tmpFile && tmpFile.exists()) {
                tmpFile.delete()
            }
        }

        return tmpFile
    }

    static byte[] createZipFile(String path, Map<String, byte[]> files) {
        // Create parent directory if needed
        def baseDir = new File(path).getParentFile()
        if (!baseDir.exists()) {
            baseDir.mkdirs()
        }

        // Create the Zip file
        def zipFile = new ZipFile(path)
        files.each { filePath, fileData ->
            def params = new ZipParameters()
            params.setFileNameInZip(filePath)
            zipFile.addStream(new ByteArrayInputStream(fileData), params)
        }

        return new File(path).getBytes()
    }
}
