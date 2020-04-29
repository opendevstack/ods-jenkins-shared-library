import org.ods.quickstarter.Pipeline

def call(Map config, Closure body) {
    def pipeline = new Pipeline(this, config)
    pipeline.execute(body)
}

return this
