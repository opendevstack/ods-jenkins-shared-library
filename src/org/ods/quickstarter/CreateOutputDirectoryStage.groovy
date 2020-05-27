package org.ods.quickstarter

class CreateOutputDirectoryStage extends Stage {

    protected String STAGE_NAME = 'Initialize output directory'

    CreateOutputDirectoryStage(def script, IContext context, Map config = [:]) {
        super(script, context, config)
    }

    def run() {
        if (script.fileExists(context.targetDir)) {
            script.error "Target directory '${context.targetDir}' must not exist yet"
        }
        script.sh(
            script: "mkdir -p ${context.targetDir}",
            label: "Create directory '${context.targetDir}'"
        )
    }

}
