package org.ods

import hudson.Functions

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
    logger.info "***** Starting ODS Pipeline (${context.componentId})*****"
    if (!!script.env.MULTI_REPO_BUILD) {
      setupForMultiRepoBuild()
    }

    boolean skipCi = false
    def cl = {
      try {
        script.wrap([$class: 'AnsiColorBuildWrapper', 'colorMapName': 'XTerm']) {
          if (context.getLocalCheckoutEnabled()) {
            script.checkout script.scm
          }
          script.stage('odsPipeline start') {
            context.assemble()
          }
          def autoCloneEnabled = !!context.cloneSourceEnv
          if (autoCloneEnabled) {
            createOpenShiftEnvironment(context)
          }

          skipCi = isCiSkip()
          if (skipCi) {
            logger.info 'Skipping build due to [ci skip] in the commit message ...'
            updateBuildStatus('NOT_BUILT')
            setBitbucketBuildStatus('SUCCESSFUL')
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

    if (!skipCi) {
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
          try {
            setBitbucketBuildStatus('INPROGRESS')
            script.wrap([$class: 'AnsiColorBuildWrapper', 'colorMapName': 'XTerm']) {
              checkoutWithGit(isSlaveNodeGitLFSenabled())
              if (context.getDisplayNameUpdateEnabled()) {
                script.currentBuild.displayName = "#${context.tagversion}"
              }

              stages(context)
            }

            script.stage('odsPipeline finished') {
              stashTestResults()
              updateBuildStatus('SUCCESS')
              setBitbucketBuildStatus('SUCCESSFUL')
              logger.info "***** Finished ODS Pipeline for ${context.componentId} *****"
            }
            return this
          } catch (err) {
            script.stage('odsPipeline error') {
              logger.info "***** Finished ODS Pipeline for  ${context.componentId} (with error) *****"
              try {
                hudson.Functions.printThrowable(err)
              } catch (e) {}
              updateBuildStatus('FAILURE')
              setBitbucketBuildStatus('FAILED')
              if (context.notifyNotGreen) {
                notifyNotGreen()
              }
              if (!!script.env.MULTI_REPO_BUILD) {
                // this is the case on a parallel node to be interupted
                if (err instanceof org.jenkinsci.plugins.workflow.steps.FlowInterruptedException) {
                  throw err
                } else {
                  context.addArtifactURI('failedStage', script.env.STAGE_NAME)
                  stashTestResults(true)
                  return this
                }
              } else {
                throw err
              }
            }
          }
        }
      }
    }
  }

  def setupForMultiRepoBuild() {
    logger.info '***** Multi Repo Build detected *****'
    context.bitbucketNotificationEnabled = false
    context.localCheckoutEnabled = false
    context.displayNameUpdateEnabled = false
    context.ciSkipEnabled = false
    context.notifyNotGreen = false
    def buildEnv = script.env.MULTI_REPO_ENV
    if (buildEnv) {
      context.environment = buildEnv
      logger.debug("Setting target env ${context.environment} on ${context.projectId}")
      def sourceCloneEnv = script.env.SOURCE_CLONE_ENV
      if (sourceCloneEnv != null && sourceCloneEnv.toString().trim().length() > 0) {
        logger.info("Environment cloning enabled, source environment: ${sourceCloneEnv}")
        context.cloneSourceEnv = sourceCloneEnv
      } else {
        logger.info("Environment cloning not enabled")
        context.cloneSourceEnv = null
      }
      def debug = script.env.DEBUG
      if (debug != null && context.debug == null) {
        logger.debug("Setting ${debug} on ${context.projectId}")
        context.debug = debug
      }
    } else {
      logger.error("Variable MULTI_REPO_ENV must not be null!")
      // Using exception because error step would skip post steps
      throw new RuntimeException("Variable MULTI_REPO_ENV must not be null!")
    }
  }

  private void stashTestResults(def hasFailed = false) {
    def testLocation = "build/test-results/test"

    logger.info "Stashing testResults (${context.componentId == null ? 'empty' : context.componentId}): Override config: ${context.testResults}, defaultlocation: ${testLocation}, same? ${(context.getTestResults() == testLocation)}"

    if (context.getTestResults().trim().length() > 0 && !(context.getTestResults() == testLocation)) {
      // verify the beast exists
      def verifyDir = script.sh(script: "ls ${context.getTestResults()}", returnStatus: true, label: "verifying existance of ${testLocation}")
      script.sh(script: "mkdir -p ${testLocation}", label: "create test result folder: ${testLocation}")
      if (verifyDir == 2) {
        script.currentBuild = 'FAILURE'
        throw new RuntimeException("The test results directory ${context.getTestResults()} provided does NOT exist!")
      } else {
        // copy files to default location
        script.sh(script: "cp -rf ${context.getTestResults()}/* ${testLocation}", label: "Moving test results to expected location")
      }
    }

    script.sh(script: "mkdir -p ${testLocation}", label: "Creating final test result dir: ${testLocation}")
    def foundTests = script.sh(script: "ls -la ${testLocation}/*.xml | wc -l", returnStdout: true, label: "Find test results").trim()
    logger.info "Found ${foundTests} tests in ${testLocation}, failed earlier? ${hasFailed}"

    context.addArtifactURI("testResults", foundTests)

    script.junit (testResults: "${testLocation}/**/*.xml", allowEmptyResults : true)

    if (hasFailed && foundTests.toInteger() == 0) {
      logger.debug "ODS Build did fail, and no test results,.. returning"
      return
    }

    // stash them in the mro pattern
    script.stash(name: "test-reports-junit-xml-${context.componentId}-${context.buildNumber}", includes: 'build/test-results/test/*.xml', allowEmpty: true)
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
    while (retries++ < maxAttempts) {
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
      } catch (err) {
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

      if (!!script.env.MULTI_REPO_BUILD) {
        logger.info "MRO Build - skipping env mapping"
      } else {
        def assumedEnvironments = context.branchToEnvironmentMapping.values()
        if (assumedEnvironments.contains(context.environment)) {
          logger.info "Skipping for ${context.environment} environment based on ${assumedEnvironments} ..."
          return
        }
      }

      if (context.environmentExists(context.targetProject)) {
        logger.info "Target Environment ${context.targetProject} exists already ..."
        return
      }

      if (!context.environmentExists("${context.projectId.toLowerCase()}-${context.cloneSourceEnv}")) {
        logger.info "Source Environment ${context.cloneSourceEnv} DOES NOT EXIST, skipping ..."
        return
      }

      if (tooManyEnvironments(context.projectId, context.environmentLimit)) {
        logger.error "Cannot create OC project " +
            "as there are already ${context.environmentLimit} OC projects! " +
            "Please clean up and run the pipeline again."
      }

      logger.info 'Environment does not exist yet. Creating now ...'
      script.withCredentials([script.usernameColonPassword(credentialsId: context.credentialsId, variable: 'USERPASS')]) {
        def userPass = script.USERPASS.replace('$', '\'$\'')
        def cloneProjectScriptUrl = "https://${context.bitbucketHost}/projects/opendevstack/repos/ods-core/raw/ocp-scripts/clone-project.sh?at=refs%2Fheads%2Fproduction"
        def branchName = "${script.env.JOB_NAME}-${script.env.BUILD_NUMBER}-${context.cloneSourceEnv}"
        logger.info 'Calculated branch name: ${branchName}'
        script.sh(script: "curl --fail -s --user ${userPass} -G '${cloneProjectScriptUrl}' -d raw -o clone-project.sh")
        def debugMode = ""
        if (context.getDebug()) {
          debugMode = "--debug"
        }
        userPass = userPass.replace('@', '\\@')
        script.sh(script: "sh clone-project.sh -o ${context.openshiftHost} -b ${context.bitbucketHost} -c ${userPass} -p ${context.projectId} -s ${context.cloneSourceEnv} -gb ${branchName} -t ${context.environment} ${debugMode}")
        logger.info 'Environment created!'
      }
    }
  }

  private boolean tooManyEnvironments(String projectId, Integer limit) {
    script.sh(
        returnStdout: true, script: "oc projects | grep '^\\s*${projectId}-' | wc -l", label: "check ocp environment maximum"
    ).trim().toInteger() >= limit
  }

  def updateBuildStatus(String status) {
    if (context.displayNameUpdateEnabled) {
      script.currentBuild.result = status
    }
  }

  private void checkoutWithGit(boolean lfsEnabled) {
    def gitParams = [$class                           : 'GitSCM',
                     branches                         : [[name: context.gitCommit]],
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

  private boolean isSlaveNodeGitLFSenabled() {
    def statusCode = script.sh(
        script: "git lfs &> /dev/null",
        label: "check if slave is GIT lfs enabled",
        returnStatus: true
    )
    return statusCode == 0
  }

  public Map<String, String> getBuildArtifactURIs() {
    return context.getBuildArtifactURIs()
  }

  // Whether the build should be skipped, based on the Git commit message.
  private boolean isCiSkip() {
    return context.ciSkipEnabled && context.ciSkip
  }
}
