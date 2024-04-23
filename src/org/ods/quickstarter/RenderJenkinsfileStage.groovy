package org.ods.quickstarter

class RenderJenkinsfileStage extends Stage {

    protected String STAGE_NAME = 'Create Jenkinsfile'

    RenderJenkinsfileStage(def script, IContext context, Map config = [:]) {
        super(script, context, config)
        if (!config.source) {
            config.source = 'Jenkinsfile.template'
        }
        if (!config.target) {
            config.target = 'Jenkinsfile'
        }
    }

    @SuppressWarnings('LineLength')
    def run() {
        def source = "${context.sourceDir}/${config.source}"
        def target = "${context.targetDir}/${config.target}"
        script.sh(
            script: """
            sed 's|@project_id@|${context.projectId}|g; s|@component_id@|${context.componentId}|g; s|@component_type@|${context.sourceDir}|g; s|@git_url_http@|${context.gitUrlHttp}|g; s|@ods_namespace@|${context.odsNamespace}|g; s|@ods_image_tag@|${context.odsImageTag}|g; s|@ods_git_ref@|${context.odsGitRef}|g; s|@agent_image_tag@|${context.agentImageTag}|g; s|@shared_library_ref@|${context.sharedLibraryRef}|g; s|@app_domain@|${context.appDomain}|g' ${source} > ${target}
            """,
            label: "Render '${config.source}' to '${config.target}'"
        )
    }

}
