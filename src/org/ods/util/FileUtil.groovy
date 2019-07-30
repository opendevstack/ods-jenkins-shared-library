package org.ods.util

@Grab(group="net.lingala.zip4j", module="zip4j", version="2.1.1")

import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters

class FileUtil {
    static File createDirectory(String path) {
        def dir = new File(path)
        dir.mkdirs()
        return dir
    }

    static File createTempFile(String baseDir, String prefix, String suffix, byte[] data) {
        def tmpFile = null

        try {
            // Create a temporary file containing data
            tmpFile = File.createTempFile(
                "${prefix}-",
                "-${suffix}",
                createDirectory(baseDir)
            ) << data
        } finally {
            if (tmpFile && tmpFile.exists()) {
                tmpFile.delete()
            }
        }

        return tmpFile
    }

    static byte[] createZipFile(String path, Map<String, byte[]> files) {
        // Create parent directory if needed
        createDirectory(new File(path).getParent())

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
