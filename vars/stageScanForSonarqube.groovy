def call(def context, def requireQualityGatePass = false) {
  echo "'stageScanForSonarqube' has been replaced with 'odsComponentStageScanWithSonar', please use that instead."
  odsComponentStageScanWithSonar(context, [requireQualityGatePass: requireQualityGatePass])
}

return this
