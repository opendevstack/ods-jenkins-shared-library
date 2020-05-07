package org.ods.quickstarter

class CopyFilesStage extends Stage {

    protected String STAGE_NAME = 'Copy files from quickstarter'

    CopyFilesStage(def script, IContext context, Map config = [:]) {
        super(script, context, config)
    }

    def run() {
        script.sh(
            script: "cp -rv ${context.sourceDir}/files/. ${context.targetDir}",
            label: "Copy files from '${context.sourceDir}/files' to '${context.targetDir}'"
        )
    }

}
