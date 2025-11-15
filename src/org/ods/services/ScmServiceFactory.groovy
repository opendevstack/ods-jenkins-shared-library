package org.ods.services

import org.ods.util.ILogger

// SCM_PROVIDER=bitbucket ( forgejo / bitbucket)
class ScmServiceFactory {
  static IScmService newFromEnv(def script, def env, String project, String passwordCredentialsId, ILogger logger) {
    def provider = (env.SCM_PROVIDER ?: 'bitbucket').toLowerCase()
    switch (provider) {
      case 'forgejo':
          return new ScmForgejoService(script, env.FORGEJO_URL, project, passwordCredentialsId, logger)
      default:
            def c = ScmBitbucketService.readConfigFromEnv(env)
            return new ScmBitbucketService(script, c.bitbucketUrl, project, passwordCredentialsId, logger)
    }
  }
}
