import org.ods.OdsContext
import org.ods.OdsLogger
import org.ods.OdsPipeline
import org.ods.service.GitService
import org.ods.service.ServiceRegistry
import org.ods.service.OpenShiftService

def call(Map config, Closure body) {
  def debug = env.DEBUG
  if (debug != null && config.debug == null) {
    config.debug = debug
  }

  def logger = new OdsLogger(this, debug)
  def context = new OdsContext(this, config, logger)
  def registry = ServiceRegistry.instance
  registry.add(GitService, new GitService(this, context))
  def gitService = registry.get(GitService)
  registry.add(OpenShiftService, new OpenShiftService(this, context))
  def pipeline = new OdsPipeline(this, context, logger, gitService)
  registry.add(OdsPipeline, pipeline)

  return pipeline.execute(body)
}

return this
