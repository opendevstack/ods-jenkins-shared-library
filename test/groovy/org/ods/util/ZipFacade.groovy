package org.ods.util

import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import org.springframework.stereotype.Service
import org.springframework.util.FileSystemUtils

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

@Service
class ZipFacade {

    static final String ARTIFACTS_BASE_DIR = 'artifacts'

    String createZipFileFromFiles(String tmpFolder, String name, Map<String, String> files) {
        String path = "${tmpFolder}/${ARTIFACTS_BASE_DIR}/${name}".toString()
        Files.createDirectories(Paths.get(path).parent)

        def zipFile = new ZipFile(path)
        files.each { fileName, filePath ->
            def params = new ZipParameters()
            params.setFileNameInZip(fileName)
            InputStream is
            try {
                is = new FileInputStream(Paths.get(filePath).toFile())
                zipFile.addStream(is, params)
            } finally {
                if(is != null) {
                    try {
                        is.close()
                    } catch(e) {
                        
                    }
                }
            }
        }

        return path
    }

    void extractZipArchive(Path zipArchive, Path targetDir) {
        cleanTargetFolder(targetDir)
        new ZipFile(zipArchive.toFile()).extractAll(targetDir.toString())
    }

    private void cleanTargetFolder(Path targetDir) {
        FileSystemUtils.deleteRecursively(targetDir.toFile())
        targetDir.toFile().mkdirs()
    }

}
