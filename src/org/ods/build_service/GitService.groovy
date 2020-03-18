package org.ods.build_service

class GitService {

  private def script

  GitService(script) {
    this.script = script
  }

  /** Looks in commit message for string '[ci skip]', '[ciskip]', '[ci-skip]' and '[ci_skip]'. */
  boolean isCiSkipInCommitMessage() {
    return script.sh(
        returnStdout: true, script: 'git show --pretty=%s%b -s',
        label: 'check skip CI?'
    ).toLowerCase().replaceAll("[\\s\\-\\_]", "").contains('[ciskip]')
  }

  void checkoutWithGit(String gitCommit, String credentialsId, String gitUrl) {
    def gitParams = [$class                           : 'GitSCM',
                     branches                         : [[name: gitCommit]],
                     doGenerateSubmoduleConfigurations: false,
                     submoduleCfg                     : [],
                     userRemoteConfigs                : [
                         [credentialsId: credentialsId,
                          url          : gitUrl]
                     ]
    ]
    if (isSlaveNodeGitLfsEnabled()) {
      gitParams.extensions = [
          [$class: 'GitLFSPull']
      ]
    }
    script.checkout(gitParams)
  }

  private boolean isSlaveNodeGitLfsEnabled() {
    def statusCode = script.sh(
        script: "git lfs &> /dev/null",
        label: "check if slave is GIT lfs enabled",
        returnStatus: true
    )
    return statusCode == 0
  }
}
