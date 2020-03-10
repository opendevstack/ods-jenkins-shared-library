/*
 * Available config options:
 *
 * projectId - required - Name of initiative (e.g. "foo").
 * componentId - required - Name of component (e.g. "be-gateway" or "fe-android").
 * image - required - Docker image to use for slave container.
 * branchToEnvironmentMapping - required - Map of branches to OCP environments.
 *                                         Branches can be either an exact name
 *                                         (e.g. "master"), a prefix (e.g.
 *                                         "feature/") or every branch ("*").
 * autoCloneEnvironmentsFromSourceMapping - optional - Map of environments to
 *                                                     source (e.g. if you want
 *                                                     to clone "feature" envs
 *                                                     from the "prod" env).
 * debug - optional - Turn on/off debug output (defaults to "false").
 * openshiftBuildTimeout - optional - Defaults to 15 minutes.
 * notifyNotGreen - optional - Turn on/off email notifications (defaults to "true").
 * podVolumes - optional - Persistent volume claim to mount in the slave.
 * podAlwaysPullImage - optional - Defaults to "true".
 * sonarQubeBranch - optional - Defaults to "master". It is possible to set it
 *                              to "*" to run SonarQube checks on all branches.
 * dependencyCheckBranch - optional - Defaults to "master". It is possible to
 *                                    set it to "*" to run OWASP dependency
 *                                    checks on all branches.
 * environmentLimit - optional - Defaults to "5".
 */

import org.ods.OdsLogger
import org.ods.OdsContext
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
