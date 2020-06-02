import org.ods.util.ILogger

def call(String stageLabel, def context, ILogger logger, Closure stageClosure) {
    logger.infoClocked("${stageLabel}-${context.componentId}",
        "**** STARTING stage '${stageLabel}' for component '${context.componentId}' " +
        "branch '${context.gitBranch}' ****")
    try {
        def result
        stage(stageLabel) { result = stageClosure() }
        return result
    } finally {
        logger.infoClocked("${stageLabel}-${context.componentId}",
            "**** ENDED stage '${stageLabel}' for component '${context.componentId}' " +
            "branch '${context.gitBranch}' ****")
    }
}

return this
