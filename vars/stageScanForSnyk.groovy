def call(def context, def snykAuthenticationCode, def buildFile, def organisation) {
  echo "'stageScanForSnyk' has been replaced with 'odsComponentScanWithSnyk', please use that instead."
  odsComponentScanWithSnyk(
    context,
    [
      snykAuthenticationCode: snykAuthenticationCode,
      buildFile: buildFile,
      organisation: organisation
    ]
  )
}

return this

