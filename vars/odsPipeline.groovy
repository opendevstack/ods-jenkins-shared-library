def call(Map config, Closure body) {
    echo "'odsPipeline' has been replaced with 'odsComponentPipeline', please use that instead."
    odsComponentPipeline(config, body)
}

return this
