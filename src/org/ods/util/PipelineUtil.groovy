package org.ods.util

@Grab(group="com.konghq", module="unirest-java", version="2.3.08", classifier="standalone")
@Grab('net.lingala.zip4j:zip4j:2.1.1')
@Grab('org.yaml:snakeyaml:1.24')

import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters

import org.apache.http.client.utils.URIBuilder
import org.yaml.snakeyaml.Yaml

class PipelineUtil {

    static final String ARTIFACTS_BASE_DIR = "artifacts"

    protected def script

    PipelineUtil(def script) {
        this.script = script
    }

    void archiveArtifact(String path, byte[] data) {
        if (!path?.trim()) {
            throw new IllegalArgumentException("Error: unable to archive artifact. 'path' is undefined.")
        }

        if (!path.startsWith(this.script.env.WORKSPACE)) {
            throw new IllegalArgumentException("Error: unable to archive artifact. 'path' must be inside the Jenkins workspace: ${path}")
        }

        def file = null

        try {
            // Write the artifact data to file
            file = new File(path).setBytes(data)

            // Compute the relative path inside the Jenkins workspace
            def workspacePath = new File(this.script.env.WORKSPACE).toURI().relativize(new File(path).toURI()).getPath()

            // Archive the artifact (requires a relative path inside the Jenkins workspace)
            this.script.archiveArtifacts(workspacePath)
        } finally {
            if (file && file.exists()) {
                file.delete()
            }
        }
    }

    protected File createDirectory(String path) {
        if (!path?.trim()) {
            throw new IllegalArgumentException("Error: unable to create directory. 'path' is undefined.")
        }

        def dir = new File(path)
        dir.mkdirs()
        return dir
    }

    byte[] createZipArtifact(String name, Map<String, byte[]> files) {
        if (!name?.trim()) {
            throw new IllegalArgumentException("Error: unable to create Zip artifact. 'name' is undefined.")
        }

        if (files == null) {
            throw new IllegalArgumentException("Error: unable to create Zip artifact. 'files' is undefined.")
        }

        def path = "${this.script.env.WORKSPACE}/${ARTIFACTS_BASE_DIR}/${name}"
        def result = this.createZipFile(path, files)
        this.archiveArtifact(path, result)
        return result
    }

    byte[] createZipFile(String path, Map<String, byte[]> files) {
        if (!path?.trim()) {
            throw new IllegalArgumentException("Error: unable to create Zip file. 'path' is undefined.")
        }

        if (files == null) {
            throw new IllegalArgumentException("Error: unable to create Zip file. 'files' is undefined.")
        }

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

    URI getGitURL(String path = this.script.env.WORKSPACE, String remote = "origin") {
        if (!path?.trim()) {
            throw new IllegalArgumentException("Error: unable to get Git URL. 'path' is undefined.")
        }

        if (!path.startsWith(this.script.env.WORKSPACE)) {
            throw new IllegalArgumentException("Error: unable to get Git URL. 'path' must be inside the Jenkins workspace: ${path}")
        }

        if (!remote?.trim()) {
            throw new IllegalArgumentException("Error: unable to get Git URL. 'remote' is undefined.")
        }

        def result = null

        this.script.dir(path) {
            result = this.script.sh(
                label : "Get Git URL for repository at path '${path}' and origin '${remote}'",
                script: "git config --get remote.${remote}.url",
                returnStdout: true
            ).trim()
        }

        return new URIBuilder(result).build()
    }

    def loadGroovySourceFile(String path) {
        if (!path?.trim()) {
            throw new IllegalArgumentException("Error: unable to load Groovy source file. 'path' is undefined.")
        }

        def file = new File(path)
        if (!file.exists()) {
            throw new IllegalArgumentException("Error: unable to load Groovy source file. Path ${path} does not exist.")
        }

        return this.script.load(path)
    }
}
