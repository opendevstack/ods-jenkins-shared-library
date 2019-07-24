package org.ods.util

@Grab('org.yaml:snakeyaml:1.24')

import org.yaml.snakeyaml.Yaml

class PipelineUtil {

    static final String PROJECT_METADATA_FILE_NAME = "metadata.yml"

    protected def steps

    PipelineUtil(def steps) {
        this.steps = steps
    }

    void archiveArtifact(String path, byte[] data) {
        def file = null

        if (!path.startsWith(this.steps.WORKSPACE)) {
            throw new IllegalArgumentException("Error: unable to archive artifact. 'path' must be inside the Jenkins workspace.")
        }

        try {
            // Write the artifact data to file
            file = new File(path).setBytes(data)

            // Compute the relative path inside the Jenkins workspace
            def workspacePath = new File(this.steps.WORKSPACE).toURI().relativize(new File(path).toURI()).getPath()

            // Archive the artifact (requires a relative path inside the Jenkins workspace)
            this.steps.archiveArtifacts artifacts: workspacePath
        } finally {
            if (file && file.exists()) {
                file.delete()
            }
        }
    }

    Map readProjectMetadata() {
        def file = new File("${this.steps.WORKSPACE}/${PROJECT_METADATA_FILE_NAME}")
        if (!file.exists()) {
            throw new RuntimeException("Error: unable to load project metadata. File ${PROJECT_METADATA_FILE_NAME} does not exist.")
        }

        return new Yaml().load(file.text)
    }
}
