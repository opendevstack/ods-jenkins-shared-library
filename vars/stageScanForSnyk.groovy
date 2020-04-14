def call(def context, def snykAuthenticationCode, def buildFile, def organisation) {
  echo "'stageScanForSnyk' has been replaced with 'odsComponentStageScanWithSnyk', please use that instead."
  odsComponentStageScanWithSnyk(
    context,
    [
      snykAuthenticationCode: snykAuthenticationCode,
      buildFile: buildFile,
      organisation: organisation
    ]
  )
}

return this

