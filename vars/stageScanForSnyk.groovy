def call(def context, def snykAuthenticationCode, def buildFile, def organisation) {
    echo "'stageScanForSnyk' is deprecated, please use " +
         "'odsComponentStageScanWithSnyk' instead."
    odsComponentStageScanWithSnyk(
        context,
        [
            snykAuthenticationCode: snykAuthenticationCode,
            buildFile: buildFile,
            organisation: organisation,
        ]
    )
}

return this

