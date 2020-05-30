import org.ods.util.ILogger

def call(String stageLabel, def context, ILogger logger, Closure stageClosure) {
    logger.infoClocked("${stageLabel}-${context.componentId}",
        "**** STARTING stage '${stageLabel}' for component '${context.componentId}' " +
        "branch '${context.gitBranch}' ****")
    def stageStartTime = System.currentTimeMillis()
    try {
        return result = stage(stageLabel) { stageClosure() }
    } finally {
        logger.infoClocked("${stageLabel}-${context.componentId}",
            "**** ENDED stage '${stageLabel}' for component '${context.componentId}' " +
            "branch '${context.gitBranch}' ****")
    }
}

return this
