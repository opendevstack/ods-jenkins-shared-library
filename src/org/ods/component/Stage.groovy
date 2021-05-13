package org.ods.component

import org.ods.util.ILogger
import org.ods.util.IPipelineSteps
import org.ods.util.PipelineSteps

abstract class Stage {

    protected def script
    protected IContext context
    protected Map<String, Object> config
    protected ILogger logger
    protected final IPipelineSteps steps

    protected Stage(def script, IContext context, ILogger logger) {
        this.script = script
        this.context = context
        this.logger = logger
        this.steps = new PipelineSteps(script)
    }

    def execute() {
        // "options" needs to be defined in the concrete stage class
        setConfiguredBranches(options)
        if (!isEligibleBranch(options.branches, context.gitBranch)) {
            logger.info(
                "Skipping stage '${stageLabel()}' for branch '${context.gitBranch}' " +
                "as it is not covered by: ${options.branches.collect { "'${it}'" } join(', ')}."
            )
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

    protected setConfiguredBranches(def opt) {
        if (opt.branches == null) {
            if (opt.branch != null) {
                opt.branches = opt.branch.split(',')
            } else {
                opt.branches = ['*']
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
