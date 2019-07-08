package org.ods.util

@Grab('org.yaml:snakeyaml:1.24')

import org.yaml.snakeyaml.Yaml

class PipelineUtil {

    static final String PROJECT_METADATA_FILE_NAME = "metadata.yml"

    protected def steps

    PipelineUtil(def steps) {
        this.steps = steps
    }

    void archiveArtifact(String dirname, String prefix, String suffix, byte[] data) {
        def tmpFile = null

        try {
            tmpFile = FileUtil.createTempFile("${this.steps.WORKSPACE}/${dirname}", prefix, suffix, data)
            this.steps.archiveArtifacts artifacts: "${dirname}/${tmpFile.name}"
        } finally {
            if (tmpFile != null && tmpFile.exists()) {
                tmpFile.delete()
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
