def call(def context, def requireQualityGatePass = false) {
    echo "'stageScanForSonarqube' is deprecated, please use " +
        "'odsComponentStageScanWithSonar' instead."
    odsComponentStageScanWithSonar(context, [requireQualityGatePass: requireQualityGatePass])
}

return this
