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
            this.run()
            echo "**** ENDED stage ${STAGE_NAME} (time: ${System.currentTimeMillis() - stageStartTime}ms) ****"
        }
    }
}
