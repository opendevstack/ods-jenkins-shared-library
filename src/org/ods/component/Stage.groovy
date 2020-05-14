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
        script.withStage(stageLabel(), context) {
            this.run()
        }
    }

    abstract protected run()

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
