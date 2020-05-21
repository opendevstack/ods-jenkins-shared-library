def call(String stageLabel, def context, Closure stageClosure) {
    echo "**** STARTING stage '${stageLabel}' for component '${context.componentId}' " +
        "branch '${context.gitBranch}' ****"
    def stageStartTime = System.currentTimeMillis()
    try {
        return result = stage(stageLabel) { stageClosure() }
    } finally {
        echo "**** ENDED stage '${stageLabel}' for component '${context.componentId}' " +
            "branch '${context.gitBranch}' (time: ${System.currentTimeMillis() - stageStartTime}ms) ****"
    }
}

return this
