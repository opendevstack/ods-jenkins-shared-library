package org.ods.component

import org.ods.services.GitService
import org.ods.services.BitbucketService
import org.ods.services.OpenShiftService
import org.ods.services.SonarQubeService
import org.ods.services.ServiceRegistry
import groovy.json.JsonOutput

class Pipeline implements Serializable {

  private GitService gitService
  private OpenShiftService openShiftService

  private def script
  private IContext context
  private ILogger logger

  Pipeline(def script, ILogger logger) {
    this.script = script
    this.logger = logger
  }

  // Main entry point.
  def execute(Map config, Closure stages) {
    if (!config.projectId) {
      logger.error "Param 'projectId' is required"
    }
    if (!config.componentId) {
      logger.error "Param 'componentId' is required"
    }

    prepareAgentPodConfig(config)

    context = new Context(script, config, logger)
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
            if (!config.containsKey('podContainers') && !config.image) {
              config.image = "${script.env.DOCKER_REGISTRY}/${config.imageStreamTag}"
            }
            context.assemble()
            // register services after context was assembled
            def registry = ServiceRegistry.instance

            registry.add(GitService, new GitService(script))
            gitService = registry.get(GitService)

            registry.add(BitbucketService, new BitbucketService(
              script,
              context.bitbucketUrl,
              context.projectId,
              context.credentialsId
            ))

            registry.add(OpenShiftService, new OpenShiftService(script, context.targetProject))
          }

          skipCi = isCiSkip()
          if (skipCi) {
            logger.info 'Skipping build due to [ci skip] in the commit message ...'
            updateBuildStatus('NOT_BUILT')
            setBitbucketBuildStatus('SUCCESSFUL')
          } else {
            context.setOpenshiftApplicationDomain (
              ServiceRegistry.instance.get(OpenShiftService).getOpenshiftApplicationDomain())
  
            def autoCloneEnabled = !!context.cloneSourceEnv
            if (autoCloneEnabled) {
              createOpenShiftEnvironment(context)
            }
          }
        }
      } catch (err) {
        updateBuildStatus('FAILURE')
        setBitbucketBuildStatus('FAILED')
        if (context.notifyNotGreen) {
          notifyNotGreen()
        }
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
      if (!config.containsKey('podContainers')) {
        config.podContainers = [
            script.containerTemplate(
                name: 'jnlp',
                image: config.image,
                workingDir: '/tmp',
                resourceRequestMemory: config.resourceRequestMemory,
                resourceLimitMemory: config.resourceLimitMemory,
                resourceRequestCpu: config.resourceRequestCpu,
                resourceLimitCpu: config.resourceLimitCpu,
                alwaysPullImage: config.alwaysPullImage,
                args: '${computer.jnlpmac} ${computer.name}'
            )
        ]
      }
      def msgBasedOn = ''
      if (config.image) {
        msgBasedOn = " based on image '${config.image}'"
      }
      logger.info "***** Continuing on node '${config.podLabel}'${msgBasedOn} *****"
      script.podTemplate(
          label: config.podLabel,
          cloud: 'openshift',
          containers: config.podContainers,
          volumes: config.podVolumes,
          serviceAccount: config.podServiceAccount
      ) {
        script.node(config.podLabel) {
          try {
            setBitbucketBuildStatus('INPROGRESS')
            script.wrap([$class: 'AnsiColorBuildWrapper', 'colorMapName': 'XTerm']) {
              gitService.checkout(
                context.gitCommit,
                [[credentialsId: context.credentialsId, url: context.gitUrl]]
              )
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
              logger.info "***** Finished ODS Pipeline for ${context.componentId} (with error) *****"
              try {
                script.echo("Error: ${err}")
                stashTestResults(true)
              } catch (e) {
                script.echo("Error: Cannot stash test results: ${e}")
              }
              updateBuildStatus('FAILURE')
              setBitbucketBuildStatus('FAILED')
              if (context.notifyNotGreen) {
                notifyNotGreen()
              }
              if (!!script.env.MULTI_REPO_BUILD) {
                // this is the case on a parallel node to be interrupted
                if (err instanceof org.jenkinsci.plugins.workflow.steps.FlowInterruptedException) {
                  throw err
                } else {
                  context.addArtifactURI('failedStage', script.env.STAGE_NAME)
                  return this
                }
              } else {
                throw err
              }
            }
          } finally {
            logger.debug ("ODS Build Artifacts: \r${JsonOutput.prettyPrint(JsonOutput.toJson(context.getBuildArtifactURIs()))}")
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
    context.sonarQubeBranch = '*'
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

  private void stashTestResults(boolean hasFailed = false) {
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
    if (!context.jobName || !context.tagversion || !context.credentialsId || !context.buildUrl || !context.bitbucketUrl || !context.gitCommit) {
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
            ${context.bitbucketUrl}/rest/build-status/1.0/commits/${context.gitCommit}
          """
        }
        return
      } catch (err) {
        logger.info "Could not set BitBucket build status to ${state} due to ${err}"
      }
    }
  }

  private void notifyNotGreen() {
    String subject = "Build $context.componentId on project $context.projectId  failed!"
    String body = "<p>$subject</p> <p>URL : <a href=\"$context.buildUrl\">$context.buildUrl</a></p> "

    script.emailext(
        body: body, mimeType: 'text/html',
        replyTo: '$script.DEFAULT_REPLYTO', subject: subject,
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

      if (!!script.env.MULTI_REPO_BUILD) {
        logger.info "MRO Build - skipping env mapping"
      } else {
        def assumedEnvironments = context.branchToEnvironmentMapping.values()
        def envExists = context.environmentExists(context.targetProject)
        logger.debug "context.environment: $context.environment, context.cloneSourceEnv: $context.cloneSourceEnv, context.targetProject: $context.targetProject, envExists: $envExists "
        if (assumedEnvironments.contains(context.environment) && (envExists)) {
          logger.info "Skipping for ${context.environment} environment based on ${assumedEnvironments} ..."
          return
        }
      }

      if (context.environmentExists(context.targetProject)) {
        logger.info "Target environment $context.targetProject exists already ..."
        return
      }

      if (!context.environmentExists("${context.projectId.toLowerCase()}-${context.cloneSourceEnv}")) {
        logger.info "Source Environment ${context.cloneSourceEnv} DOES NOT EXIST, skipping ..."
        return
      }

      if (openShiftService.tooManyEnvironments(context.projectId, context.environmentLimit)) {
        logger.error "Cannot create OC project " +
            "as there are already ${context.environmentLimit} OC projects! " +
            "Please clean up and run the pipeline again."
      }

      logger.info 'Environment does not exist yet. Creating now ...'
      script.withCredentials([script.usernameColonPassword(credentialsId: context.credentialsId, variable: 'USERPASS')]) {
        def userPass = script.USERPASS.replace('$', '\'$\'')
        def branchName = "${script.env.JOB_NAME}-${script.env.BUILD_NUMBER}-${context.cloneSourceEnv}"
        logger.info "Calculated branch name: ${branchName}"
        def scriptToUrls = context.getCloneProjectScriptUrls()
        // NOTE: a for loop did not work here due to https://issues.jenkins-ci.org/browse/JENKINS-49732
        scriptToUrls.each { scriptName, url ->
          script.sh(script: "curl --fail -s --user ${userPass} -G '${url}' -d raw -o '${scriptName}'")
        }
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

  def updateBuildStatus(String status) {
    if (context.displayNameUpdateEnabled) {
      // @ FIXME ? groovy.lang.MissingPropertyException: No such property: result for class: java.lang.String
      if (script.currentBuild instanceof String) {
        script.currentBuild = status
      } else {
        script.currentBuild.result = status
      }
    }
  }

  public Map<String, String> getBuildArtifactURIs() {
    return context.getBuildArtifactURIs()
  }

  // Whether the build should be skipped, based on the Git commit message.
  private boolean isCiSkip() {
    return context.ciSkipEnabled && gitService.ciSkipInCommitMessage
  }

  private def prepareAgentPodConfig(Map config) {
    if (!config.image && !config.imageStreamTag && !config.podContainers) {
      logger.error "One of 'image', 'imageStreamTag' or 'podContainers' is required"
    }
    if (!config.podVolumes) {
      config.podVolumes = []
    }
    if (!config.containsKey('podServiceAccount')) {
      config.podServiceAccount = 'jenkins'
    }
    if (!config.containsKey('alwaysPullImage')) {
      config.alwaysPullImage = true
    }
    if (!config.containsKey('resourceRequestMemory')) {
      config.resourceRequestMemory = '1Gi'
    }
    if (!config.containsKey('resourceLimitMemory')) {
      // 2Gi is required for e.g. jenkins-slave-maven, which selects the Java
      // version based on available memory.
      // Also, e.g. Angular is known to use a lot of memory during production
      // builds.
      // Quickstarters should set a lower value if possible.
      config.resourceLimitMemory = '2Gi'
    }
    if (!config.containsKey('resourceRequestCpu')) {
      config.resourceRequestCpu = '100m'
    }
    if (!config.containsKey('resourceLimitCpu')) {
      // 1 core is a lot but this directly influences build time.
      // Quickstarters should set a lower value if possible.
      config.resourceLimitCpu = '1'
    }
    if (!config.containsKey('podLabel')) {
      config.podLabel = "pod-${UUID.randomUUID().toString()}"
    }
  }
}
