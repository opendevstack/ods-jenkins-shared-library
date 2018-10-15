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

    if (!context.responsible) {
      script.currentBuild.result = 'ABORTED'
      logger.info "***** Skipping ODS Pipeline *****"
      return
    }

    logger.info "***** Continuing on node '${context.podLabel}' based on image '${context.image}' *****"
    script.podTemplate(
      label: context.podLabel,
      cloud: 'openshift',
      containers: [
        script.containerTemplate(
          name: 'jnlp',
          image: context.image,
          workingDir: '/tmp',
          alwaysPullImage: context.podAlwaysPullImage,
          args: '${computer.jnlpmac} ${computer.name}'
        )
      ],
      volumes: context.podVolumes
    ) {
      script.node(context.podLabel) {
        try {
          setBitbucketBuildStatus('INPROGRESS')
          script.wrap([$class: 'AnsiColorBuildWrapper', 'colorMapName': 'XTerm']) {
            script.git url: context.gitUrl, branch: context.gitBranch, credentialsId: context.credentialsId
            script.currentBuild.displayName = "${context.shortBranchName}/#${context.tagversion}"
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
}
