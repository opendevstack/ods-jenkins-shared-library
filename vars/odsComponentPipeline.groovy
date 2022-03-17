import org.ods.component.Pipeline
import org.ods.services.ServiceRegistry
import org.ods.util.Logger

def call(Map config, Closure body) {
    def debug = env.DEBUG
    if (debug != null) {
        config.debug = debug
    }

    config.debug = !!config.debug

    def logger = new Logger(this, config.debug)
    def pipeline = new Pipeline(this, logger)

    pipeline.execute(config, body)

}

return this
