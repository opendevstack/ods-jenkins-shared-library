import org.ods.OdsLogger
import org.ods.OdsPipeline

def call(Map config, Closure body) {

  def debug = env.DEBUG
  if (debug != null && config.debug == null) {
    config.debug = debug
  }

  def logger = new OdsLogger(this, debug)
  OdsPipeline pipeline = new OdsPipeline(this, logger)

  pipeline.execute(config, body)
}

return this
