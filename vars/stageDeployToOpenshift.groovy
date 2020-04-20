def call(def context, Map config = [:]) {
    echo "'stageDeployToOpenshift' is deprecated, please use " +
        "'odsComponentStageRolloutOpenShiftDeployment' instead."
    odsComponentStageRolloutOpenShiftDeployment(context, config)
}

return this
