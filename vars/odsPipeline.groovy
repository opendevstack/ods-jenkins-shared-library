import org.ods.OdsLogger
import org.ods.OdsPipeline

def call(Map config, Closure body) {
  def debug = env.DEBUG
  if (debug != null && config.debug == null) {
    config.debug = debug
  }
  def logger = new OdsLogger(this, debug)
  def bp = new OdsPipeline(this, config, logger)
  return bp.execute(body)
}

return this
