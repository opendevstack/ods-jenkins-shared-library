package org.ods.component

import org.ods.util.ILogger

abstract class Stage {

    protected def script
    protected def context
    protected Map config
    protected ILogger logger

    public final String STAGE_NAME = 'NOT SET'

    protected Stage(def script, IContext context, Map config, ILogger logger) {
        this.script = script
        this.context = context
        this.config = config
        this.logger = logger
    }

    def execute() {
        script.withStage(stageLabel(), context, logger) {
            return this.run()
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
