def call(def context, Map config = [:]) {
  echo "'stageDeployToOpenshift' has been replaced with 'odsComponentStageRolloutOpenShiftDeployment', please use that instead."
  return odsComponentStageRolloutOpenShiftDeployment(context, config)
}

return this
