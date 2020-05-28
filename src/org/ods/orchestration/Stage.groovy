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
            script.echo "**** STARTING orchestration stage ${STAGE_NAME} ****"
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

                script.echo("Error occured in the orchestration pipeline: ${e.message}")

                try {
                    project.reportPipelineStatus(e.message, true)
                } catch (reportError) {
                    script.echo("Error: unable to report pipeline status because of: ${reportError.message}.")
                    reportError.initCause(e)
                    throw reportError
                }

                throw e
            } finally {
                script.echo "**** ENDED orchestration stage ${STAGE_NAME} " +
                    "(time: ${System.currentTimeMillis() - stageStartTime}ms) ****"
            }
        }
    }

    protected def runOnAgentPod(Project project, boolean condition, Closure block) {
        if (condition) {
            def git = ServiceRegistry.instance.get(GitService)
            def stashStartTime = System.currentTimeMillis();
            script.dir(script.env.WORKSPACE) {
                script.stash(name: 'wholeWorkspace', includes: '**/*,**/.git', useDefaultExcludes: false)
            }
            script.echo "Stashing workspace took ${System.currentTimeMillis() - stashStartTime}ms"
            def bitbucketHost = script.env.BITBUCKET_HOST
            def podLabel = "mro-jenkins-agent-${script.env.BUILD_NUMBER}"
            script.echo "Starting orchestration pipeline slave pod '${podLabel}'"
            def nodeStartTime = System.currentTimeMillis();
            script.node(podLabel) {
                def slaveStartTime = System.currentTimeMillis() - nodeStartTime
                script.echo "Orchestration pipeline pod '${podLabel}' starttime: ${slaveStartTime}ms"
                git.configureUser()
                def unstashStartTime = System.currentTimeMillis();
                script.unstash("wholeWorkspace")
                script.echo "Unstashing workspace took ${System.currentTimeMillis() - unstashStartTime}ms"
                script.withCredentials(
                    [script.usernamePassword(
                        credentialsId: project.services.bitbucket.credentials.id,
                        usernameVariable: 'BITBUCKET_USER',
                        passwordVariable: 'BITBUCKET_PW'
                    )]
                ) {
                    def bbUser = URLEncoder.encode(script.env.BITBUCKET_USER, 'UTF-8')
                    def bbPwd = URLEncoder.encode(script.env.BITBUCKET_PW, 'UTF-8')
                    def urlWithCredentials = "https://${bbUser}:${bbPwd}@${bitbucketHost}"
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

    @SuppressWarnings('GStringAsMapKey')
    def executeInParallel (Closure block1, Closure block2) {
        Map executors = [
            "${STAGE_NAME}": {
                block1()
            },
            'orchestration': {
                block2()
            },
        ]
        executors.failFast = true
        script.parallel (executors)
    }
}
