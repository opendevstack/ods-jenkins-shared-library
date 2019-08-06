package org.ods

class OdsPipeline implements Serializable {

  def script
  Context context
  Logger logger

  // script is the context of the Jenkinsfile. That means that things like "sh"
  // need to be called on script.
  // config is a map of config properties to customise the behaviour.
  def OdsPipeline(script, config, Logger logger) {
    this.script = script
    this.context = new OdsContext(script, config, logger)
    this.logger = logger
  }

  // Main entry point.
  def execute(Closure stages) {
    logger.info "***** Starting ODS Pipeline *****"
    prepareForMultiRepoBuild()

    def cl = {
      try {
        script.wrap([$class: 'AnsiColorBuildWrapper', 'colorMapName': 'XTerm']) {
          if (context.getLocalCheckoutEnabled()) {
            script.checkout script.scm
          }
          script.stage('Prepare') {
            context.assemble()
          }
          def autoCloneEnabled = !!context.cloneSourceEnv
          if (autoCloneEnabled) {
            createOpenShiftEnvironment(context)
          }
        }
      } catch (err) {
        updateBuildStatus('FAILURE')
        setBitbucketBuildStatus('FAILED')
        notifyNotGreen()
        throw err
      }
    }
    if (context.getLocalCheckoutEnabled()) {
      logger.info "***** Continuing on node 'master' *****"
      script.node('master', cl)
    } else {
      cl()
    }

	def nodeStartTime = System.currentTimeMillis();
    def msgBasedOn = ''
    if (context.image) {
      msgBasedOn = " based on image '${context.image}'"
    }
    logger.info "***** Continuing on node '${context.podLabel}'${msgBasedOn} *****"
    script.podTemplate(
      label: context.podLabel,
      cloud: 'openshift',
      containers: context.podContainers,
      volumes: context.podVolumes,
      serviceAccount: context.podServiceAccount
    ) {
      script.node(context.podLabel) {
        logger.info "Node ('${context.image}') startup took: " + (System.currentTimeMillis() - startTime) + " ms"
        try {
          setBitbucketBuildStatus('INPROGRESS')
          script.wrap([$class: 'AnsiColorBuildWrapper', 'colorMapName': 'XTerm']) {
            checkoutWithGit(isSlaveNodeGitLFSenabled())
            if (context.getDisplayNameUpdateEnabled()) {
              script.currentBuild.displayName = "#${context.tagversion}"
            }

            if (context.getCiSkipEnabled() && context.ciSkip){
              logger.info 'Skipping build due to [ci skip] in the commit message ...'
              updateBuildStatus('NOT_BUILT')
              setBitbucketBuildStatus('SUCCESSFUL')
              return
            }

            stages(context)
          }
          updateBuildStatus('SUCCESS')
          setBitbucketBuildStatus('SUCCESSFUL')
          logger.info "***** Finished ODS Pipeline *****"
        } catch (err) {
          updateBuildStatus('FAILURE')
          setBitbucketBuildStatus('FAILED')
          if (context.notifyNotGreen) {
            notifyNotGreen()
          }
          throw err
        } finally {
          // in case called from outside
          return this
        }
      }
    }
  }

  def prepareForMultiRepoBuild() {
    if (!!script.env.MULTI_REPO_BUILD) {
      logger.info '***** Multi Repo Build detected *****'
      context.bitbucketNotificationEnabled = false
      context.localCheckoutEnabled = false
      context.displayNameUpdateEnabled = false
      context.ciSkipEnabled = false
      context.notifyNotGreen = false
      def buildEnv = script.env.MULTI_REPO_ENV
      if (buildEnv) {
        context.environment = buildEnv
        context.cloneSourceEnv = null
      } else {
        logger.error("Variable MULTI_REPO_ENV must not be null!")
        // Using exception because error step would skip post steps
        throw new RuntimeException("Variable MULTI_REPO_ENV must not be null!")
      }
    }
  }

  private void setBitbucketBuildStatus(String state) {
    if (!context.getBitbucketNotificationEnabled()) {
      return
    }
    if (!context.jobName || !context.tagversion || !context.credentialsId || !context.buildUrl || !context.bitbucketHost || !context.gitCommit) {
      logger.info "Cannot set BitBucket build status to ${state} because required data is missing!"
      return
    }

    logger.info "Setting BitBucket build status to ${state} ..."
    def buildName = "${context.jobName}-${context.tagversion}"
    def maxAttempts = 3
    def retries = 0
    while(retries++ < maxAttempts) {
      try {
        script.withCredentials([script.usernameColonPassword(credentialsId: context.credentialsId, variable: 'USERPASS')]) {
          script.sh """curl \\
            --fail \\
            --silent \\
            --user ${script.USERPASS.replace('$', '\'$\'')} \\
            --request POST \\
            --header \"Content-Type: application/json\" \\
            --data '{\"state\":\"${state}\",\"key\":\"${buildName}\",\"name\":\"${buildName}\",\"url\":\"${context.buildUrl}\"}' \\
            https://${context.bitbucketHost}/rest/build-status/1.0/commits/${context.gitCommit}
          """
        }
        return
      } catch(err) {
        logger.info "Could not set BitBucket build status to ${state} due to ${err}"
      }
    }
  }

  private void notifyNotGreen() {
    if (context.notifyNotGreen) {
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

  def createOpenShiftEnvironment(def context) {
    script.stage('Create Openshift Environment') {
      if (!context.environment) {
        logger.info 'Skipping for empty environment ...'
        return
      }

      def assumedEnvironments = context.branchToEnvironmentMapping.values()
      if (assumedEnvironments.contains(context.environment)) {
        logger.info 'Skipping for ${context.environment} environment ...'
        return
      }

      if (environmentExists(context.targetProject)) {
        logger.info 'Environment exists already ...'
        return
      }

      if (tooManyEnvironments(context.projectId, context.environmentLimit)) {
        logger.error "Cannot create OC project " +
          "as there are already ${context.environmentLimit} OC projects! " +
          "Please clean up and run the pipeline again."
      }

      logger.info 'Environment does not exist yet. Creating now ...'
      script.withCredentials([script.usernameColonPassword(credentialsId: context.credentialsId, variable: 'USERPASS')]) {
        def userPass = script.USERPASS.replace('@', '%40').replace('$', '\'$\'')
        def cloneProjectScriptUrl = "https://${context.bitbucketHost}/projects/opendevstack/repos/ods-project-quickstarters/raw/ocp-templates/scripts/clone-project.sh?at=refs%2Fheads%2Fproduction"
        script.sh(script: "curl --fail -s --user ${userPass} -G '${cloneProjectScriptUrl}' -d raw -o clone-project.sh")
        def debugMode = ""
        if (context.debug) {
          debugMode = "--debug"
        }
        script.sh(script: "sh clone-project.sh -o ${context.openshiftHost} -b ${context.bitbucketHost} -c ${userPass} -p ${context.projectId} -s ${context.cloneSourceEnv} -t ${context.environment} ${debugMode}")
        logger.info 'Environment created!'
      }
    }
  }

  private boolean tooManyEnvironments(String projectId, Integer limit) {
    script.sh(
      returnStdout: true, script: "oc projects | grep '^\\s*${projectId}-' | wc -l", label : "check ocp environment maximum"
    ).trim().toInteger() >= limit
  }

  def updateBuildStatus(String status) {
    if (context.displayNameUpdateEnabled) {
      script.currentBuild.result = status
    }
  }

  private boolean environmentExists(String name) {
    def statusCode = script.sh(
      script:"oc project ${name} &> /dev/null",
      label : "check if OCP environment exists",
      returnStatus: true
    )
    return statusCode == 0
  }

  private void checkoutWithGit(boolean lfsEnabled){
    // FIXME: override gitBranch in context to fix demo
    def gitBranch = "master" // context.gitBranch

    def gitParams = [$class                           : 'GitSCM',
                     branches                         : [[name: 'refs/heads/' + gitBranch]],
                     doGenerateSubmoduleConfigurations: false,
                     submoduleCfg                     : [],
                     userRemoteConfigs                : [
                             [credentialsId: context.credentialsId,
                              url          : context.gitUrl]
                     ]
    ]
    if (lfsEnabled) {
      gitParams.extensions = [
              [$class: 'GitLFSPull']
      ]
    }
    script.checkout(gitParams)
  }

  private boolean isSlaveNodeGitLFSenabled(){
    def statusCode = script.sh(
      script:"git lfs &> /dev/null",
      label : "check if slave is GIT lfs enabled",
      returnStatus: true
    )
    return statusCode == 0
  }

}
