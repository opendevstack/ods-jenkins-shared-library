package org.ods.component

import org.ods.util.ILogger

abstract class Stage {

    protected def script
    protected def context
    protected Map config
    protected ILogger logger

    protected Stage(def script, IContext context, Map config, ILogger logger) {
        this.script = script
        this.context = context
        this.config = config
        this.logger = logger
    }

    def execute() {
        setConfiguredBranches()
        if (!isEligibleBranch(config.branches, context.gitBranch)) {
            logger.info "Skipping stage '${stageLabel()}' for branch '${context.gitBranch}'"
            return
        }
        script.withStage(stageLabel(), context, logger) {
            return this.run()
        }
    }

    abstract protected run()

    protected String stageLabel() {
        STAGE_NAME
    }

    protected setConfiguredBranches() {
        if (!config.containsKey('branches')) {
            if (config.containsKey('branch')) {
                config.branches = config.branch.split(',')
            } else {
                config.branches = ['*']
            }
        }
    }

    protected boolean isEligibleBranch(def eligibleBranches, String branch) {
        // Check if any branch is allowed
        if (eligibleBranches.contains('*')) {
            return true
        }
        // Check if prefix (e.g. "release/") is allowed
        if (eligibleBranches.any { it.endsWith('/') && branch.startsWith(it) }) {
            return true
        }
        // Check if specific branch is allowed
        return eligibleBranches.contains(branch)
    }

}
