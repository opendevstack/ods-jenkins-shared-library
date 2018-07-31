package org.ods

class OdsPipeline implements Serializable {

  def script
  def config
  Logger logger

  // script is the context of the Jenkinsfile. That means that things like "sh"
  // need to be called on script.
  // config is a map of config properties to customise the behaviour.
  def OdsPipeline(script, config, Logger logger) {
    this.script = script
    this.config = config
    this.logger = logger
  }

  // Main entry point.
  def execute(Closure stages) {
    logger.verbose "***** Starting ODS Pipeline *****"

    logger.verbose "***** Continuing on node 'master' *****"
    script.node('master') {
      try {
        script.wrap([$class: 'AnsiColorBuildWrapper', 'colorMapName': 'XTerm']) {
          checkout()
          script.stage('Prepare') {
            assembleConfig()
          }
          if (config.updateBranch && config.testProjectBranch != config.gitBranch) {
            script.stage('Update Branch') {
              config.branchUpdated = updateBranch()
            }
          }
        }
      } catch (err) {
        script.currentBuild.result = 'FAILURE'
        if (config.notifyNotGreen) {
          notifyNotGreen()
        }
        throw err
      }
    }

    if (config.updateBranch && config.branchUpdated) {
      script.currentBuild.result = 'ABORTED'
      logger.verbose "***** Skipping ODS Pipeline *****"
      return
    }

    def podLabel = "pod-${simpleHash(config.image)}"
    logger.verbose "***** Continuing on node '${podLabel}' based on image '${config.image}' *****"
    script.podTemplate(
      label: podLabel,
      cloud: 'openshift',
      containers: [
        script.containerTemplate(
          name: 'jnlp',
          image: config.image,
          workingDir: '/tmp',
          alwaysPullImage: true,
          args: '${computer.jnlpmac} ${computer.name}'
        )
      ],
      volumes: config.podVolumes
    ) {
      script.node(podLabel) {
        try {
          script.wrap([$class: 'AnsiColorBuildWrapper', 'colorMapName': 'XTerm']) {
            checkoutBranch(config.gitBranch)
            script.currentBuild.displayName = "${config.shortBranchName}/#${config.tagversion}"
            stages(config)
          }
          script.currentBuild.result = 'SUCCESS'
        } catch (err) {
          script.currentBuild.result = 'FAILURE'
          if (config.notifyNotGreen) {
            notifyNotGreen()
          }
          throw err
        }
      }
    }

    logger.verbose "***** Finished ODS Pipeline *****"
  }

  // We cannot use java.security.MessageDigest.getInstance("SHA-256")
  // nor hashCode() due to sandbox restrictions ...
  private int simpleHash(str) {
    int hash = 7;
    for (int i = 0; i < str.length(); i++) {
      hash = hash*31 + str.charAt(i);
    }
    return hash
  }

  private void checkout() {
    script.checkout script.scm
  }

  // Assemble config
  private void assembleConfig() {
    // Pre-Validation
    if (!config.projectId) {
      logger.error "Param 'projectId' is required"
    }
    if (!config.componentId) {
      logger.error "Param 'componentId' is required"
    }
    if (!config.image) {
      logger.error "Param 'image' is required"
    }

    // Environment variables
    config.jobName = script.env.JOB_NAME
    config.buildNumber = script.env.BUILD_NUMBER
    config.nexusHost = script.env.NEXUS_HOST
    config.nexusUsername = script.env.NEXUS_USERNAME
    config.nexusPassword = script.env.NEXUS_PASSWORD
    config.branchName = script.env.BRANCH_NAME // may be empty
    config.openshiftHost = script.env.OPENSHIFT_API_URL
    config.bitbucketHost = script.env.BITBUCKET_HOST

    // ENV Validation
    if (!config.jobName) {
      logger.error 'JOB_NAME is required, but not set (usually provided by Jenkins)'
    }
    if (!config.buildNumber) {
      logger.error 'BUILD_NUMBER is required, but not set (usually provided by Jenkins)'
    }
    if (!config.nexusHost) {
      logger.error 'NEXUS_HOST is required, but not set'
    }
    if (!config.nexusUsername) {
      logger.error 'NEXUS_USERNAME is required, but not set'
    }
    if (!config.nexusPassword) {
      logger.error 'NEXUS_PASSWORD is required, but not set'
    }
    if (!config.openshiftHost) {
      logger.error 'OPENSHIFT_API_URL is required, but not set'
    }
    if (!config.bitbucketHost) {
      logger.error 'BITBUCKET_HOST is required, but not set'
    }

    // Derived config
    config.openshiftProjectId = "${config.projectId}-cd"
    config.credentialsId = config.openshiftProjectId + '-cd-user-with-password'

    // Defaults
    config.autoCreateEnvironment = config.autoCreateEnvironment ?: false
    config.updateBranch = config.updateBranch ?: false
    if (!config.containsKey('notifyNotGreen')) {
      config.notifyNotGreen = true
    }
    if (!config.containsKey('environmentLimit')) {
      config.environmentLimit = 5
    }
    if (!config.containsKey('openshiftBuildTimeout')) {
      config.openshiftBuildTimeout = 15
    }
    if (!config.groupId) {
      config.groupId = "org.opendevstack.${config.projectId}"
    }
    if (!config.testProjectBranch) {
      config.testProjectBranch = "master"
    }
    if (!config.podVolumes) {
      config.podVolumes = []
    }

    // Post-Validation
    if (config.autoCreateEnvironment && !config.branchName) {
      logger.error 'Aborting! autoCreateEnvironment=true and branchName=null. ' +
                   'You will need to configure a "Bitbucket Team/Project" item.'
    }

    // Git related config
    config.gitUrl = getGitUrl()

    // BRANCH_NAME is only given for "Bitbucket Team/Project" items. If
    // autoCreateEnvironment is disabled, we need to determine the Git branch to
    // build from the last push. In that case, we also skip the pipeline if
    // it is triggered but not responsible.
    if (config.autoCreateEnvironment) {
      // For "Bitbucket Team/Project" items, the initial checkout is already
      // of the branch we want to build for, so just get HEAD now.
      config.gitCommit = getGitCommit()
      if (config.branchName.startsWith("PR-")){
        config.gitBranch = getBranchOfPullRequest()
        config.jobName = config.branchName
      } else {
        config.gitBranch = config.branchName
        config.jobName = extractBranchCode(config.branchName)
      }
    } else {
      config.gitBranch = determineBranchToBuild()
      checkoutBranch(config.gitBranch)
      config.gitCommit = getGitCommit()
      def isTestProjectBuild = config.jobName.endsWith("-test")
      if (!isResponsible(config.gitBranch, isTestProjectBuild)) {
        def previousBuild = script.currentBuild.getPreviousBuild()
        if (previousBuild) {
          logger.verbose "Setting status of previous build"
          script.currentBuild.result = previousBuild.getResult()
        } else {
          logger.verbose "No previous build, setting status ABORTED"
          script.currentBuild.result = 'ABORTED'
        }
        script.currentBuild.displayName = "${config.buildNumber}/skipping-not-responsible"
        logger.error "This job: ${config.jobName} is not responsible for building: ${config.gitBranch}"
      }
    }

    config.shortBranchName = extractShortBranchName(config.gitBranch)
    config.tagversion = "${config.buildNumber}-${config.gitCommit.take(8)}"
    config.environment = determineEnvironment(config)
    if (config.environment) {
      config.targetProject = "${config.projectId}-${config.environment}"
    }

    logger.verbose "assembled config: ${config}"
  }

  private String getGitUrl() {
    return script.sh(
      returnStdout: true, script: 'git config --get remote.origin.url'
    ).trim().replace('https://bitbucket', 'https://cd_user@bitbucket')
  }

  private String getGitCommit() {
    return script.sh(
      returnStdout: true, script: 'git rev-parse HEAD'
    ).trim()
  }

  private String checkoutBranch(String branchName) {
    script.git url: config.gitUrl, branch: branchName, credentialsId: config.credentialsId
  }

  private String determineEnvironment(config) {
    if (config.testProjectBranch == config.gitBranch) {
      return "test"
    } else if ("develop".equals(config.gitBranch) || !config.autoCreateEnvironment) {
      return "dev"
    } else {
      def tokens = extractBranchCode(config.gitBranch).tokenize("-")
      def projectId = tokens[0]
      if (!projectId || !projectId.toLowerCase().equals(config.projectId.toLowerCase())) {
        logger.echo "No project ID found in branch name => no environment to deploy to"
        return ""
      }
      def code = tokens[1]
      if (code) {
        return code.toLowerCase() + "-dev"
      } else {
        logger.echo "No branch code extracted => no environment to deploy to"
        return ""
      }
    }
  }

  private boolean updateBranch() {
    def updated = false
    script.withCredentials([script.usernameColonPassword(credentialsId: config.credentialsId, variable: 'USERPASS')]) {
      def url = (constructCredentialBitbucketURL(gitUrl, script.USERPASS))
      script.withEnv(["BRANCH_TO_BUILD=${config.gitBranch}", "BITBUCKET_URL=${url}"]) {
        script.sh '''
          git config user.name "Jenkins CD User"
          git config user.email "cd_user@opendevstack.org"
          git config credential.helper store
          echo ${BITBUCKET_URL} > ~/.git-credentials
          git checkout ${BRANCH_TO_BUILD}
          git merge origin/${BRANCH_TO_BUILD}
          '''
        def mergeResult = script.sh returnStdout: true, script: '''
          git merge --no-edit -m "Merging master to ${BRANCH_TO_BUILD}" origin/master
          '''
        if (!mergeResult.trim().contains("Already up-to-date.")) {
          script.sh 'git push origin ${BRANCH_TO_BUILD} 2>&1'
          logger.verbose "Branch was updated with master"
          updated = true
        } else {
          logger.verbose "Branch is up-to-date with master"
        }
      }
    }
    return updated
  }

  // Given a branch like "feature/HUGO-4-brown-bag-lunch", it extracts
  // "HUGO-4" from it.
  private String extractBranchCode(String branch) {
    if (config.testProjectBranch == branch) {
      branch
    } else if (branch.startsWith("feature/")) {
      def list = branch.drop("feature/".length()).tokenize("-")
      "${list[0]}-${list[1]}"
    } else if (branch.startsWith("bugfix/")) {
      def list = branch.drop("bugfix/".length()).tokenize("-")
      "${list[0]}-${list[1]}"
    } else if (branch.startsWith("hotfix/")) {
      def list = branch.drop("hotfix/".length()).tokenize("-")
      "${list[0]}-${list[1]}"
    } else if (branch.startsWith("release/")) {
      def list = branch.drop("release/".length()).tokenize("-")
      "${list[0]}-${list[1]}"
    } else {
      branch
    }
  }

  // Given a branch like "feature/HUGO-4-brown-bag-lunch", it extracts
  // "HUGO-4-brown-bag-lunch" from it.
  private String extractShortBranchName(String branch) {
    if (config.testProjectBranch == branch) {
        branch
    } else if (branch.startsWith("feature/")) {
        branch.drop("feature/".length())
    } else if (branch.startsWith("bugfix/")) {
        branch.drop("bugfix/".length())
    } else if (branch.startsWith("hotfix/")) {
        branch.drop("hotfix/".length())
    } else if (branch.startsWith("release/")) {
        branch.drop("release/".length())
    } else {
        branch
    }
}

  // For pull requests, the branch name environment variable is not the actual
  // git branch, which we need.
  private String getBranchOfPullRequest() {
    def branch
    script.withCredentials([script.usernameColonPassword(credentialsId: config.credentialsId, variable: 'USERPASS')]) {
      def url = constructCredentialBitbucketURL(config.gitUrl, script.USERPASS)
      branch = script.sh(
        returnStdout: true,
        script: "git ls-remote -q --heads ${url} | grep '${config.gitCommit}' | awk '{print \$2}'"
      ).trim().drop("refs/heads/".length())
    }
    return branch
  }

  // If BRANCH_NAME is not given, we need to figure out the branch from the last
  // commit to the repository.
  private String determineBranchToBuild() {
    script.withCredentials([script.usernameColonPassword(credentialsId: config.credentialsId, variable: 'USERPASS')]) {
      def url = constructCredentialBitbucketURL(getGitUrl(), script.USERPASS)
      script.withEnv(["BITBUCKET_URL=${url}"]) {
        return script.sh(returnStdout: true, script: '''
          git config user.name "Jenkins CD User"
          git config user.email "cd_user@opendevstack.org"
          git config credential.helper store
          echo ${BITBUCKET_URL} > ~/.git-credentials
          git fetch
          git for-each-ref --sort=-committerdate refs/remotes/origin | cut -c69- | head -1
        ''').trim()
      }
    }
  }

  // If BRANCH_NAME is not given, we need to check whether the pipeline is
  // responsible for building this commit at all.
  private boolean isResponsible(String branch, boolean testProjectBuild) {
    if (config.testProjectBranch == branch && testProjectBuild) {
      true
    } else if (branch.startsWith("feature/")
            || branch.startsWith("hotfix/")
            || branch.startsWith("bugfix/")
            || branch.startsWith("release/")
    ) {
      !testProjectBuild
    } else {
      false
    }
  }

  private String constructCredentialBitbucketURL(String url, String userPass) {
      return url.replace("cd_user", userPass)
  }

  private void notifyNotGreen() {
    script.emailext(
      body: '${script.DEFAULT_CONTENT}', mimeType: 'text/html',
      replyTo: '$script.DEFAULT_REPLYTO', subject: '${script.DEFAULT_SUBJECT}',
      to: script.emailextrecipients([
        [$class: 'CulpritsRecipientProvider'],
        [$class: 'DevelopersRecipientProvider'],
        [$class: 'RequesterRecipientProvider'],
        [$class: 'UpstreamComitterRecipientProvider']
      ])
    )
  }
}
