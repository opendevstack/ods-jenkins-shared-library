package org.ods.orchestration

import org.ods.orchestration.util.Project

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
            echo "**** STARTING stage ${STAGE_NAME} ****"
            def stageStartTime = System.currentTimeMillis()
            try {
                this.run()
            } catch (e) {
                // Check for random null references which occur after a Jenkins restart
                if (ServiceRegistry.instance == null || ServiceRegistry.instance.get(PipelineSteps) == null) {
                    e = new IllegalStateException("Error: invalid references have been detected for critical pipeline services. Most likely, your Jenkins instance has been recycled. Please re-run the pipeline!").initCause(e)
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
            echo "**** ENDED stage ${STAGE_NAME} (time: ${System.currentTimeMillis() - stageStartTime}ms) ****"
        }
    }
}
