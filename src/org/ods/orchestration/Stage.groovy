package org.ods.orchestration

import org.ods.services.ServiceRegistry
import org.ods.orchestration.util.Project
import org.ods.services.GitService
import org.ods.util.PipelineSteps

class Stage {

    protected def script
    protected Project project
    protected List<Set<Map>> repos

    public final String STAGE_NAME = 'NOT SET'

    Stage(def script, Project project, List<Set<Map>> repos) {
        this.script = script
        this.project = project
        this.repos = repos
    }

    def execute() {
        script.stage(STAGE_NAME) {
            script.echo "**** STARTING stage ${STAGE_NAME} ****"
            def stageStartTime = System.currentTimeMillis()
            try {
                return this.run()
            } catch (e) {
                // Check for random null references which occur after a Jenkins restart
                if (ServiceRegistry.instance == null || ServiceRegistry.instance.get(PipelineSteps) == null) {
                    e = new IllegalStateException(
                        "Error: invalid references have been detected for critical pipeline services. " +
                        "Most likely, your Jenkins instance has been recycled. Please re-run the pipeline!"
                    ).initCause(e)
                }

                script.echo(e.message)

                try {
                    project.reportPipelineStatus(e.message, true)
                } catch (reportError) {
                    script.echo("Error: unable to report pipeline status because of: ${reportError.message}.")
                    reportError.initCause(e)
                    throw reportError
                }

                throw e
            }
            script.echo "**** ENDED stage ${STAGE_NAME} (time: ${System.currentTimeMillis() - stageStartTime}ms) ****"
        }
    }

    protected def runOnAgentPod(Project project, boolean condition, Closure block) {
        if (condition) {
            def git = ServiceRegistry.instance.get(GitService)
            script.dir(script.env.WORKSPACE) {
                script.stash(name: 'wholeWorkspace', includes: '**/*,**/.git', useDefaultExcludes: false)
            }

            def bitbucketHost = script.env.BITBUCKET_HOST
            def podLabel = "mro-jenkins-agent-${script.env.BUILD_NUMBER}"
            script.echo "Starting orchestration pipeline slave pod '${podLabel}'"
            def nodeStartTime = System.currentTimeMillis();
            script.node(podLabel) {
                def slaveStartTime = System.currentTimeMillis() - nodeStartTime
                script.echo "Orchestration pipeline pod '${podLabel}' starttime: ${slaveStartTime}ms"
                git.configureUser()
                script.unstash("wholeWorkspace")
                script.withCredentials(
                    [script.usernamePassword(
                        credentialsId: project.services.bitbucket.credentials.id,
                        usernameVariable: 'BITBUCKET_USER',
                        passwordVariable: 'BITBUCKET_PW'
                    )]
                ) {
                    def urlWithCredentials = "https://${script.BITBUCKET_USER}:${script.BITBUCKET_PW}@${bitbucketHost}"
                    script.writeFile(
                        file: "${script.env.HOME}/.git-credentials",
                        text: urlWithCredentials
                    )
                    script.sh(
                        script: "git config --global credential.helper store",
                        label: "setup credential helper"
                    )
                }
                block()
            }
        } else {
            block()
        }
    }

}
