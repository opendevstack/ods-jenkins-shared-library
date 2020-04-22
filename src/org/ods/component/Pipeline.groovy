package org.ods.component

import org.ods.services.GitService
import org.ods.services.BitbucketService
import org.ods.services.OpenShiftService
import org.ods.services.SonarQubeService
import org.ods.services.ServiceRegistry
import org.ods.services.JenkinsService
import groovy.json.JsonOutput

class Pipeline implements Serializable {

  private GitService gitService
  private OpenShiftService openShiftService
  private JenkinsService jenkinsService
  private BitbucketService bitbucketService

  private def script
  private IContext context
  private ILogger logger
  private boolean notifyNotGreen
  private boolean ciSkipEnabled
  private boolean displayNameUpdateEnabled
  private boolean localCheckoutEnabled
  private boolean bitbucketNotificationEnabled

  Pipeline(def script, ILogger logger) {
    this.script = script
    this.logger = logger
    this.notifyNotGreen = true
    this.ciSkipEnabled = true
    this.displayNameUpdateEnabled= true
    this.localCheckoutEnabled = true
    this.bitbucketNotificationEnabled = true
  }

  // Main entry point.
  def execute(Map config, Closure stages) {
    if (!config.projectId) {
      logger.error "Param 'projectId' is required"
    }
    if (!config.componentId) {
      logger.error "Param 'componentId' is required"
    }

    if (config.containsKey('notifyNotGreen')) {
      this.notifyNotGreen = config.notifyNotGreen
    }
    if (config.containsKey('ciSkipEnabled')) {
      this.ciSkipEnabled = config.ciSkipEnabled
    }
    if (config.containsKey('displayNameUpdateEnabled')) {
      this.displayNameUpdateEnabled = config.displayNameUpdateEnabled
    }
    if (config.containsKey('localCheckoutEnabled')) {
      this.localCheckoutEnabled = config.localCheckoutEnabled
    }
    if (config.containsKey('bitbucketNotificationEnabled')) {
      this.bitbucketNotificationEnabled = config.bitbucketNotificationEnabled
    }

    prepareAgentPodConfig(config)
    logger.info "***** Starting ODS Pipeline (${context.componentId})*****"
    
    if (!!script.env.MULTI_REPO_BUILD) {
      setupForMultiRepoBuild(config)
    }

    context = new Context(script, config, logger, this.localCheckoutEnabled)

    boolean skipCi = false
    def cl = {
      try {
        script.wrap([$class: 'AnsiColorBuildWrapper', 'colorMapName': 'XTerm']) {
          if (this.localCheckoutEnabled) {
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
            bitbucketService = registry.get(BitbucketService)

            registry.add(OpenShiftService, new OpenShiftService(script, context.targetProject))
            
            registry.add(JenkinsService, new JenkinsService(script, logger))
            jenkinsService = registry.get(JenkinsService)
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
        if (notifyNotGreen) {
          doNotifyNotGreen()
        }
        throw err
      }
    }
    if (this.localCheckoutEnabled) {
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
              if (this.displayNameUpdateEnabled) {
                script.currentBuild.displayName = "#${context.tagversion}"
              }

              stages(context)
            }
            script.stage('odsPipeline finished') {
              updateBuildStatus('SUCCESS')
              setBitbucketBuildStatus('SUCCESSFUL')
              logger.info "***** Finished ODS Pipeline for ${context.componentId} *****"
            }
            return this
          } catch (err) {
            script.stage('odsPipeline error') {
              logger.info "***** Finished ODS Pipeline for ${context.componentId} (with error) *****"
              script.echo("Error: ${err}")
              updateBuildStatus('FAILURE')
              setBitbucketBuildStatus('FAILED')
              if (notifyNotGreen) {
                doNotifyNotGreen()
              }
              if (!!script.env.MULTI_REPO_BUILD) {
                context.addArtifactURI('failedStage', script.env.STAGE_NAME)
                // this is the case on a parallel node to be interrupted
                if (err instanceof org.jenkinsci.plugins.workflow.steps.FlowInterruptedException) {
                  throw err
                }
                return this
              } else {
                throw err
              }
            }
          } finally {
            jenkinsService.stashTestResults(
              context.testResults, "${context.componentId}-${context.buildNumber}").each {resultKey, resultValue ->
                context.addArtifactURI (resultKey, resultValue)
              }
            logger.debug ("ODS Build Artifacts '${context.componentId}': \r${JsonOutput.prettyPrint(JsonOutput.toJson(context.getBuildArtifactURIs()))}")
          }
        }
      }
    }
  }

  def setupForMultiRepoBuild(def config) {
    logger.info 'Detected multirepo MRO build'
    this.bitbucketNotificationEnabled = false
    config.bitbucketNotificationEnabled = false
    this.localCheckoutEnabled = false
    config.localCheckoutEnabled = false
    this.localCheckoutEnabled = false
    config.localCheckoutEnabled = false
    this.ciSkipEnabled = false
    config.ciSkipEnabled = false
    this.notifyNotGreen = false
    config.notifyNotGreen = false
    def buildEnv = script.env.MULTI_REPO_ENV
    if (buildEnv) {
      context.environment = buildEnv
      logger.debug("Setting target env ${context.environment} on ${context.projectId}")
    } else {
      logger.error("Variable MULTI_REPO_ENV (target environment!) must not be null!")
      // Using exception because error step would skip post steps
      throw new RuntimeException("Variable MULTI_REPO_ENV (target environment!) must not be null!")
    }
  }

  private void setBitbucketBuildStatus(String state) {
    if (!this.bitbucketNotificationEnabled) {
      return
    }
    if (!context.jobName || !context.tagversion || !context.buildUrl || !context.gitCommit) {
      logger.info "Cannot set Bitbucket build status to '${state}' because required data is missing!"
      return
    }

    def buildName = "${context.jobName}-${context.tagversion}"
    bitbucketService.setBuildStatus(context.buildUrl, context.gitCommit, state, buildName)
  }

  private void doNotifyNotGreen() {
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
    if (this.displayNameUpdateEnabled) {
      // @ FIXME ? groovy.lang.MissingPropertyException: No such property: result for class: java.lang.String
      if (script.currentBuild instanceof String) {
        script.currentBuild = status
      } else {
        script.currentBuild.result = status
      }
    }
  }

  public Map<String, Object> getBuildArtifactURIs() {
    return context.getBuildArtifactURIs()
  }

  // Whether the build should be skipped, based on the Git commit message.
  private boolean isCiSkip() {
    return this.ciSkipEnabled && gitService.ciSkipInCommitMessage
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
