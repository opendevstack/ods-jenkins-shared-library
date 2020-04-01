import org.ods.component.Logger
import org.ods.component.Pipeline

def call(Map config, Closure body) {

  def debug = env.DEBUG
  if (debug != null && config.debug == null) {
    config.debug = debug
  }

  def logger = new Logger(this, debug)
  def pipeline = new Pipeline(this, logger)

  pipeline.execute(config, body)
}

return this
