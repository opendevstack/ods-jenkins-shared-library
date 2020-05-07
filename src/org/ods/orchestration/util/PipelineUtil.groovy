package org.ods.orchestration.util

@Grab('net.lingala.zip4j:zip4j:2.1.1')
@Grab('org.yaml:snakeyaml:1.24')

import com.cloudbees.groovy.cps.NonCPS

import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters

import org.ods.orchestration.util.IPipelineSteps
import org.ods.orchestration.util.Project

@SuppressWarnings('JavaIoPackageAccess')
class PipelineUtil {

    static final String ARTIFACTS_BASE_DIR = "artifacts"
    static final String SONARQUBE_BASE_DIR = "sonarqube"
    static final String XUNIT_DOCUMENTS_BASE_DIR = "xunit"

    protected Project project
    protected IPipelineSteps steps
    protected GitUtil git

    PipelineUtil(Project project, IPipelineSteps steps, GitUtil git) {
        this.project = project
        this.steps = steps
        this.git = git
    }

    void archiveArtifact(String path, byte[] data) {
        if (!path?.trim()) {
            throw new IllegalArgumentException(
                "Error: unable to archive artifact. 'path' is undefined."
            )
        }

        if (!path.startsWith(this.steps.env.WORKSPACE)) {
            throw new IllegalArgumentException(
                "Error: unable to archive artifact. 'path' must be inside the Jenkins workspace: ${path}"
            )
        }

        def file = null

        try {
            // Write the artifact data to file
            file = new File(path).setBytes(data)

            // Compute the relative path inside the Jenkins workspace
            def workspacePath = new File(this.steps.env.WORKSPACE).toURI().relativize(new File(path).toURI()).getPath()

            // Archive the artifact (requires a relative path inside the Jenkins workspace)
            this.steps.archiveArtifacts(workspacePath)
        } finally {
            if (file && file.exists()) {
                file.delete()
            }
        }
    }

    @NonCPS
    protected File createDirectory(String path) {
        if (!path?.trim()) {
            throw new IllegalArgumentException("Error: unable to create directory. 'path' is undefined.")
        }

        def dir = new File(path)
        dir.mkdirs()
        return dir
    }

    byte[] createZipArtifact(String name, Map<String, byte[]> files, boolean archive = true) {
        if (!name?.trim()) {
            throw new IllegalArgumentException("Error: unable to create Zip artifact. 'name' is undefined.")
        }

        if (files == null) {
            throw new IllegalArgumentException("Error: unable to create Zip artifact. 'files' is undefined.")
        }

        def path = "${this.steps.env.WORKSPACE}/${ARTIFACTS_BASE_DIR}/${name}".toString()
        def result = this.createZipFile(path, files)
        if (archive) {
            this.archiveArtifact(path, result)
        }

        return result
    }
    
    void createAndStashArtifact(String name, byte[] file) {
        if (!name?.trim()) {
            throw new IllegalArgumentException("Error: unable to stash artifact. 'name' is undefined.")
        }
  
        if (file == null) {
            throw new IllegalArgumentException("Error: unable to stash artifact. 'files' is undefined.")
        }
  
        def path = "${this.steps.env.WORKSPACE}/${ARTIFACTS_BASE_DIR}/${name}".toString()
  
        // Create parent directory if needed
        this.createDirectory(new File(path).getParent())
        new File(path) << (file)
        
        this.steps.dir(new File(path).getParent()) {
            this.steps.stash(name)
        }
    }

    @NonCPS
    byte[] createZipFile(String path, Map<String, byte[]> files) {
        if (!path?.trim()) {
            throw new IllegalArgumentException("Error: unable to create Zip file. 'path' is undefined.")
        }

        if (files == null) {
            throw new IllegalArgumentException("Error: unable to create Zip file. 'files' is undefined.")
        }

        // Create parent directory if needed
        this.createDirectory(new File(path).getParent())

        // Create the Zip file
        def zipFile = new ZipFile(path)
        files.each { filePath, fileData ->
            def params = new ZipParameters()
            params.setFileNameInZip(filePath)
            zipFile.addStream(new ByteArrayInputStream(fileData), params)
        }

        return new File(path).getBytes()
    }

    void executeBlockAndFailBuild(Closure block) {
        try {
            block()
        } catch (e) {
            this.failBuild(e.message)
            hudson.Functions.printThrowable(e)
            throw e
        }
    }

    void failBuild(String message) {
        this.steps.currentBuild.result = "FAILURE"
        this.steps.echo(message)
    }

    void warnBuild(String message) {
        this.steps.currentBuild.result = "UNSTABLE"
        this.steps.echo(message)
    }

    def loadGroovySourceFile(String path) {
        if (!path?.trim()) {
            throw new IllegalArgumentException("Error: unable to load Groovy source file. 'path' is undefined.")
        }

        def file = new File(path)
        if (!file.exists()) {
            throw new IllegalArgumentException("Error: unable to load Groovy source file. Path ${path} does not exist.")
        }

        return this.steps.load(path)
    }
}
