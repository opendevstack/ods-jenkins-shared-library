package org.ods.quickstarter

class RenderSonarPropertiesStage extends Stage {

    protected String STAGE_NAME = 'Create sonar-project.properties'

    RenderSonarPropertiesStage(def script, IContext context, Map config = [:]) {
        super(script, context, config)
        if (!config.source) {
            config.source = 'sonar-project.properties.template'
        }
        if (!config.target) {
            config.target = 'sonar-project.properties'
        }
    }

    def run() {
        def source = "${context.sourceDir}/${config.source}"
        def target = "${context.targetDir}/${config.target}"
        script.sh(
            script: """
            sed 's|@project_id@|${context.projectId}|g; s|@component_id@|${context.componentId}|g' ${source} > ${target}
            """,
            label: "Render '${config.source}' to '${config.target}'"
        )
    }

}
