/*
 * Available config options:
 *
 * projectId - required - Name of initiative (e.g. "foo").
 * componentId - required - Name of component (e.g. "be-gateway" or "fe-android").
 * image - required - Docker image to use for slave container.
 * verbose - optional - Turn on/off verbose output (defaults to "false").
 * openshiftBuildTimeout - optional - Defaults to 15 minutes.
 * notifyNotGreen - optional - Turn on/off email notifications (defaults to "true").
 * podVolumes - optional - Persistent volume claim to mount in the slave.
 * podAlwaysPullImage - optional - Defaults to "true".
 * productionBranch - optional - Defaults to "master".
 * productionEnvironment - optional - Defaults to "prod".
 * developmentBranch - optional - Defaults to "develop".
 * developmentEnvironment - optional - Defaults to "dev".
 * defaultReviewEnvironment - optional - Defaults to "review".
 * defaultHotfixEnvironment - optional - Defaults to "hotfix".
 * defaultReleaseEnvironment - optional - Defaults to "release".
 * autoCreateReviewEnvironment - optional - Allows to have one OCP project per
 *                                          review branch (but needs setup of a
 *                                          "Bitbucket Team/Project" item and
 *                                          webhooks to trigger builds should be
 *                                          disabled). Defaults to "false".
 * autoCreateHotfixEnvironment - optional - Allows to have one OCP project per
 *                                          hotfix branch (but needs setup of a
 *                                          "Bitbucket Team/Project" item and
 *                                          webhooks to trigger builds should be
 *                                          disabled) Defaults to "false".
 * autoCreateReleaseEnvironment - optional - Allows to have one OCP project per
 *                                           release branch (but needs setup of
 *                                           a "Bitbucket Team/Project" item and
 *                                           webhooks to trigger builds should be
 *                                           disabled) Defaults to "false".
 * environmentLimit - optional - Defaults to "5".
 * admins - optional - Comma separated list of admins for the cloned OC projects
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
