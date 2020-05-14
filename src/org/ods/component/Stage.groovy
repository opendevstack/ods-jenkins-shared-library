package org.ods.component

abstract class Stage {

    protected def script
    protected def context
    protected Map config

    public final String STAGE_NAME = 'NOT SET'

    protected Stage(def script, IContext context, Map config) {
        this.script = script
        this.context = context
        this.config = config
    }

    def execute() {
        startingStageMessage()
        def result = script.stage(stageLabel()) {
            this.run()
        }
        endedStageMessage()
        result
    }

    abstract protected run()

    protected void startingStageMessage() {
        script.echo "**** STARTING stage '${stageLabel()}' " +
            "for component '${context.componentId}' branch '${context.gitBranch}' ****"
    }

    protected void endedStageMessage() {
        script.echo "**** ENDED stage '${stageLabel()}' " +
            "for component '${context.componentId}' branch '${context.gitBranch}' ****"
    }

    protected String stageLabel() {
        STAGE_NAME
    }

    protected boolean isEligibleBranch(def eligibleBranches, String branch) {
        // Check if any branch is allowed
        if (eligibleBranches.contains('*')) {
            return true
        }
        // Check if prefix (e.g. "release/") is allowed
        eligibleBranches.each { eligibleBranch ->
            if (eligibleBranch.endsWith('/') && branch.startsWith(eligibleBranch)) {
                return true
            }
        }
        // Check if specific branch is allowed
        return eligibleBranches.contains(branch)
    }
}
