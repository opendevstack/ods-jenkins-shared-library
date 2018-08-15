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
    logger.verbose "***** Starting ODS Pipeline *****"

    logger.verbose "***** Continuing on node 'master' *****"
    script.node('master') {
      try {
        script.wrap([$class: 'AnsiColorBuildWrapper', 'colorMapName': 'XTerm']) {
          script.checkout script.scm
          script.stage('Prepare') {
            context.assemble()
          }
          if (context.shouldUpdateBranch()) {
            script.stage('Update Branch') {
              context.branchUpdated = updateBranch()
            }
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

    if (context.branchUpdated) {
      script.currentBuild.result = 'ABORTED'
      logger.verbose "***** Skipping ODS Pipeline *****"
      return
    }

    logger.verbose "***** Continuing on node '${context.podLabel}' based on image '${context.image}' *****"
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

    logger.verbose "***** Finished ODS Pipeline *****"
  }

  private boolean updateBranch() {
    def updated = false
    script.withCredentials([script.usernameColonPassword(credentialsId: context.credentialsId, variable: 'USERPASS')]) {
      def url = context.gitUrl.replace("cd_user", script.USERPASS)
      script.withEnv(["BRANCH_TO_BUILD=${context.gitBranch}", "BITBUCKET_URL=${url}"]) {
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

  private void setBitbucketBuildStatus(String state) {
    logger.verbose "Setting BitBucket build status to ${state} ..."
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
      logger.verbose "Could not set BitBucket build status to ${state}"
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
