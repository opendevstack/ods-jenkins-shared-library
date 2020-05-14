def call(String stageLabel, def context, Closure stageClosure) {
    echo "**** STARTING stage '${stageLabel}' for component '${context.componentId}' branch '${context.gitBranch}' ****"
    def result = stage(stageLabel) { stageClosure() }
    echo "**** ENDED stage '${stageLabel}' for component '${context.componentId}' branch '${context.gitBranch}' ****"
    return result
}

return this
