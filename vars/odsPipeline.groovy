/*
 * Available config options:
 *
 * projectId - required - Name of initiative (e.g. 'psp' or 'brass')
 * componentId - required - Name of component (e.g. 'be-gateway' or 'fe-android')
 * image - required - Docker image to use for slave container
 * verbose - optional - Turn on/off verbose output (default: false)
 * autoCreateEnvironment - optional - Allows to have one OC project per branch
 *                                    (but needs setup of a "Bitbucket
 *                                     Team/Project" item and the dev/test
 *                                     pipelines should be disabled)
 * environmentLimit - optional - Defaults to 5
 * openshiftBuildTimeout - optional - Defaults to 15 minutes
 * admins - optional - Comma separated list of admins for the cloned OC projects
 * notifyNotGreen - optional - Turn on/off email notifications (default: true)
 * testProjectBranch - optional - Defaults to master
 * podVolumes - optional - PVC to mount in the slave
 */

import org.ods.OdsLogger
import org.ods.OdsPipeline

def call(Map config, Closure body) {
  config.verbose = config.verbose ?: (config.debug ?: false)
  def logger = new OdsLogger(this, config.verbose)
  def bp = new OdsPipeline(this, config, logger)
  return bp.execute(body)
}

return this
