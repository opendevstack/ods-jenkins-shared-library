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

    logger.info "***** Continuing on node 'master' *****"
    script.node('master') {
      try {
        script.wrap([$class: 'AnsiColorBuildWrapper', 'colorMapName': 'XTerm']) {
          script.checkout script.scm
          script.stage('Prepare') {
            context.assemble()
          }
          def autoCloneEnabled = !!context.cloneSourceEnv
          if (autoCloneEnabled) {
            createOpenShiftEnvironment(context)
          }
        }
      } catch (err) {
        script.currentBuild.result = 'FAILURE'
        setBitbucketBuildStatus('FAILED')
        if (context.notifyNotGreen) {
          notifyNotGreen()
        }
        throw err
      }
    }

    def msgBasedOn = ''
    if (context.image) {
      msgBasedOn = " based on image '${context.image}'"
    }
    logger.info "***** Continuing on node '${context.podLabel}'${msgBasedOn} *****"
    script.podTemplate(
      label: context.podLabel,
      cloud: 'openshift',
      containers: context.podContainers,
      volumes: context.podVolumes
    ) {
      script.node(context.podLabel) {
        try {
          setBitbucketBuildStatus('INPROGRESS')
          script.wrap([$class: 'AnsiColorBuildWrapper', 'colorMapName': 'XTerm']) {
            script.checkout script.scm
            script.currentBuild.displayName = "#${context.tagversion}"
            stages(context)
          }
          script.currentBuild.result = 'SUCCESS'
          setBitbucketBuildStatus('SUCCESSFUL')
        } catch (err) {
          script.currentBuild.result = 'FAILURE'
          setBitbucketBuildStatus('FAILED')
          if (context.notifyNotGreen) {
            notifyNotGreen()
          }
          throw err
        }
      }
    }

    logger.info "***** Finished ODS Pipeline *****"
  }

  private void setBitbucketBuildStatus(String state) {
    logger.info "Setting BitBucket build status to ${state} ..."
    def buildName = "${context.jobName}-${context.tagversion}"
    try {
      script.withCredentials([script.usernameColonPassword(credentialsId: context.credentialsId, variable: 'USERPASS')]) {
        script.sh """curl \\
          --fail \\
          --silent \\
          --user ${script.USERPASS} \\
          --request POST \\
          --header \"Content-Type: application/json\" \\
          --data '{\"state\":\"${state}\",\"key\":\"${buildName}\",\"name\":\"${buildName}\",\"url\":\"${context.buildUrl}\"}' \\
          https://${context.bitbucketHost}/rest/build-status/1.0/commits/${context.gitCommit}
        """
      }
    } catch (err) {
      logger.info "Could not set BitBucket build status to ${state}"
    }
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
        def userPass = script.USERPASS.replace('@', '%40')
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
      returnStdout: true, script: "oc projects | grep '^\\s*${projectId}-' | wc -l"
    ).trim().toInteger() >= limit
  }

  private boolean environmentExists(String name) {
    def statusCode = script.sh(
      script:"oc project ${name} &> /dev/null",
      returnStatus: true
    )
    return statusCode == 0
  }
}
