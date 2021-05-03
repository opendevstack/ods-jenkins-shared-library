package org.ods.quickstarter

import org.ods.orchestration.service.JiraService

class CreateOpenShiftResourcesStage extends Stage {

    protected String STAGE_NAME = 'Create OpenShift resources'

    CreateOpenShiftResourcesStage(def script, IContext context, Map config = [:]) {
        super(script, context, config)
        if (!config.directory) {
            config.directory = "${context.sourceDir}/openshift"
        }
        if (!config.envFile) {
            config.envFile = "${context.sourceDir}/ocp.env"
        }
        if (!config.selector) {
            config.selector = "app=${context.projectId}-${context.componentId}"
        }
    }

    def run() {
        def leadUser = 'project-admin'
        ['dev', 'test'].each { env ->
            def namespace = "${context.projectId}-${env}"

            def tailorParams = [
                '--upsert-only',
                '--ignore-unknown-parameters',
                "--selector ${config.selector}",
                "--param=PROJECT=${context.projectId}",
                "--param=COMPONENT=${context.componentId}",
                "--param=ENV=${env}",
                "--param=DOCKER_REGISTRY=${context.dockerRegistry}",
                "--param=GIT_URL=${context.gitUrlHttp}",
                "--param=ODS_GIT_REF=${context.odsGitRef}",
                "--param=OWNER=${leadUser}",
            ]

            if (script.fileExists(config.envFile)) {
                tailorParams << "--param-file ${script.env.WORKSPACE}/${config.envFile}"
            }

            script.dir(config.directory) {
                script.sh(
                    script: "tailor --non-interactive -n ${namespace} apply ${tailorParams.join(' ')}",
                    label: "Create component ${context.componentId} in namespace ${namespace}"
                )
            }
        }
    }

}
