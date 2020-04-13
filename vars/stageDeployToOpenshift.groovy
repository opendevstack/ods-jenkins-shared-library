def call(def context, Map config = [:]) {
  echo "'stageDeployToOpenshift' has been replaced with 'odsComponentStageRolloutOpenShiftDeployment', please use that instead."
  odsComponentStageRolloutOpenShiftDeployment(context, config)
}

return this
