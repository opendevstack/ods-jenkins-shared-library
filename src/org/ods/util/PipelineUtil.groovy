package org.ods.util

@Grab('net.lingala.zip4j:zip4j:2.1.1')
@Grab('org.yaml:snakeyaml:1.24')

import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters

import org.yaml.snakeyaml.Yaml

class PipelineUtil {

    static final String ARTIFACTS_BASE_DIR = "artifacts"
    static final String PROJECT_METADATA_FILE_NAME = "metadata.yml"

    protected def script

    PipelineUtil(def script) {
        this.script = script
    }

    void archiveArtifact(String path, byte[] data) {
        def file = null

        if (!path.startsWith(this.script.WORKSPACE)) {
            throw new IllegalArgumentException("Error: unable to archive artifact. 'path' must be inside the Jenkins workspace.")
        }

        try {
            // Write the artifact data to file
            file = new File(path).setBytes(data)

            // Compute the relative path inside the Jenkins workspace
            def workspacePath = new File(this.script.WORKSPACE).toURI().relativize(new File(path).toURI()).getPath()

            // Archive the artifact (requires a relative path inside the Jenkins workspace)
            this.script.archiveArtifacts artifacts: workspacePath
        } finally {
            if (file && file.exists()) {
                file.delete()
            }
        }
    }

    File createDirectory(String path) {
        def dir = new File(path)
        dir.mkdirs()
        return dir
    }

    File createTempFile(String baseDir, String prefix, String suffix, byte[] data) {
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

    byte[] createZipArtifact(String name, Map<String, byte[]> contents) {
        def path = "${this.script.WORKSPACE}/${ARTIFACTS_BASE_DIR}/${name}"

        def result = this.createZipFile(path, contents)
        this.archiveArtifact(path, result)
        return result
    }

    byte[] createZipFile(String path, Map<String, byte[]> files) {
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

    def loadGroovySourceFile(String path) {
        def file = new File(path)
        if (!file.exists()) {
            throw new RuntimeException("Error: unable to load Groovy source file. Path ${path} does not exist.")
        }

        return this.script.load(path)
    }

    Map loadProjectMetadata() {
        def file = new File("${this.script.WORKSPACE}/${PROJECT_METADATA_FILE_NAME}")
        if (!file.exists()) {
            throw new RuntimeException("Error: unable to load project meta data. File ${PROJECT_METADATA_FILE_NAME} does not exist.")
        }

        def result = new Yaml().load(file.text)

        // Check for existence of required attribute 'id'
        if (result.id == null || !result.id.trim()) {
            throw new RuntimeException("Error: unable to process project meta data. Required attribute 'id' is undefined.")
        }

        // Check for existence of required attribute 'repositories'
        if (!result.repositories) {
            throw new RuntimeException("Error: unable to process project meta data. Required attribute 'repositories' is undefined.")
        }

        // Check for existence of required attribute 'repositories[i].id'
        repos.eachWithIndex { repo, index ->
            if (repo.id == null || !repo.id.trim()) {
                throw new RuntimeException("Error: unable to process project meta data. Required attribute 'repositories[${index}].id' is undefined.")
            }
        }

        return result
    }
}
