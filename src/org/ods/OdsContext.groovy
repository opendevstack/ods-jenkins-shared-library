package org.ods

class OdsContext implements Context {

  def script
  Logger logger
  Map config

  boolean environmentCreated

  OdsContext(script, config, logger) {
    this.script = script
    this.config = config
    this.logger = logger
  }

  def assemble() {
    logger.debug "Validating input ..."
    if (!config.projectId) {
      logger.error "Param 'projectId' is required"
    }
    if (!config.componentId) {
      logger.error "Param 'componentId' is required"
    }
    if (!config.image && !config.podContainers) {
      logger.error "Param 'image' or 'podContainers' is required"
    }
    // branchToEnvironmentMapping must be given, but it is OK to be empty - e.g.
    // if the repository should not be deployed to OpenShift at all.
    if (!config.containsKey('branchToEnvironmentMapping')) {
      logger.error "Param 'branchToEnvironmentMapping' is required"
    }

    logger.debug "Collecting environment variables ..."
    config.jobName = script.env.JOB_NAME
    config.buildNumber = script.env.BUILD_NUMBER
    config.buildUrl = script.env.BUILD_URL
    config.buildTime = new Date()
    config.nexusHost = script.env.NEXUS_HOST
    config.nexusUsername = script.env.NEXUS_USERNAME
    config.nexusPassword = script.env.NEXUS_PASSWORD
    config.openshiftHost = script.env.OPENSHIFT_API_URL
    config.bitbucketHost = script.env.BITBUCKET_HOST
    config.odsSharedLibVersion = script.sh(script: "env | grep 'library.ods-library.version' | cut -d= -f2", returnStdout: true)

    logger.debug "Validating environment variables ..."
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
    if (!config.buildUrl) {
      logger.info 'BUILD_URL is required to set a proper build status in ' +
                  'BitBucket, but it is not present. Normally, it is provided ' +
                  'by Jenkins - please check your JenkinsUrl configuration.'
    }

    logger.debug "Deriving configuration from input ..."
    config.openshiftProjectId = "${config.projectId}-cd"
    config.credentialsId = config.openshiftProjectId + '-cd-user-with-password'

    logger.debug "Setting defaults ..."
    if (!config.containsKey('autoCloneEnvironmentsFromSourceMapping')) {
      config.autoCloneEnvironmentsFromSourceMapping = [:]
    }
    if (!config.containsKey('sonarQubeBranch')) {
      config.sonarQubeBranch = 'master'
    }
    if (!config.containsKey('failOnSnykScanVulnerabilities')) {
      config.failOnSnykScanVulnerabilities = true
    }
    if (!config.containsKey('dependencyCheckBranch')) {
      config.dependencyCheckBranch = 'master'
    }
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
    if (!config.podVolumes) {
      config.podVolumes = []
    }
    if (!config.containsKey('podAlwaysPullImage')) {
      config.podAlwaysPullImage = true
    }
    if (!config.containsKey('podServiceAccount')) {
      config.podServiceAccount = 'jenkins'
    }
    if (!config.containsKey('podContainers')) {
      config.podContainers = [
        script.containerTemplate(
          name: 'jnlp',
          image: config.image,
          workingDir: '/tmp',
          alwaysPullImage: config.podAlwaysPullImage,
          args: '${computer.jnlpmac} ${computer.name}'
        )
      ]
    }
    if (!config.containsKey('podLabel')) {
      config.podLabel = "pod-${UUID.randomUUID().toString()}"
    }

    logger.debug "Retrieving Git information ..."
    config.gitUrl = retrieveGitUrl()
    config.gitBranch = retrieveGitBranch()
    config.gitCommit = retrieveGitCommit()
    config.gitCommitAuthor = retrieveGitCommitAuthor()
    config.gitCommitMessage = retrieveGitCommitMessage()
    config.gitCommitTime = retrieveGitCommitTime()
    config.tagversion = "${config.buildNumber}-${config.gitCommit.take(8)}"

    logger.debug "Setting environment ..."
    determineEnvironment()
    if (config.environment) {
      config.targetProject = "${config.projectId}-${config.environment}"
    }

    logger.info "Assembled configuration: ${config}"
  }

  boolean getDebug() {
      config.debug
  }

  String getJobName() {
    config.jobName
  }

  String getBuildNumber() {
    config.buildNumber
  }

  String getBuildUrl() {
    config.buildUrl
  }

  String getBuildTime() {
    config.buildTime
  }

  String getGitBranch() {
      config.gitBranch
  }

  String getCredentialsId() {
      config.credentialsId
  }

  String getImage() {
    config.image
  }

  String getPodLabel() {
    config.podLabel
  }

  Object getPodContainers() {
    config.podContainers
  }

  Object getPodVolumes() {
    config.podVolumes
  }

  boolean getPodAlwaysPullImage() {
    config.podAlwaysPullImage
  }

  String getPodServiceAccount() {
    config.podServiceAccount
  }

  String getGitUrl() {
    config.gitUrl
  }

  String getTagversion() {
    config.tagversion
  }

  boolean getNotifyNotGreen() {
    config.notifyNotGreen
  }

  String getNexusHost() {
      config.nexusHost
  }

  String getNexusUsername() {
      config.nexusUsername
  }

  String getNexusPassword() {
      config.nexusPassword
  }

  String getNexusHostWithBasicAuth() {
    config.nexusHost.replace("://", "://${config.nexusUsername}:${config.nexusPassword}@")
  }

  String getBranchToEnvironmentMapping() {
      config.branchToEnvironmentMapping
  }

  String getAutoCloneEnvironmentsFromSourceMapping() {
      config.autoCloneEnvironmentsFromSourceMapping
  }

  String getCloneSourceEnv() {
      config.cloneSourceEnv
  }

  String getEnvironment() {
      config.environment
  }

  String getGroupId() {
      config.groupId
  }

  String getProjectId() {
      config.projectId
  }

  String getComponentId() {
      config.componentId
  }

  String getGitCommit() {
    config.gitCommit
  }

  String getGitCommitAuthor() {
    config.gitCommitAuthor
  }

  String getGitCommitMessage() {
    config.gitCommitMessage
  }

  String getGitCommitTime() {
    config.gitCommitTime
  }

  String getTargetProject() {
      config.targetProject
  }

  String getSonarQubeBranch() {
    config.sonarQubeBranch
  }

  String getFailOnSnykScanVulnerabilities() {
      config.failOnSnykScanVulnerabilities
  }

  String getDependencyCheckBranch() {
      config.dependencyCheckBranch
  }

  int getEnvironmentLimit() {
      config.environmentLimit
  }

  String getOpenshiftHost() {
      config.openshiftHost
  }

  String getOdsSharedLibVersion() {
    config.odsSharedLibVersion
  }

  String getBitbucketHost() {
      config.bitbucketHost
  }

  boolean getEnvironmentCreated() {
      this.environmentCreated
  }

  int getOpenshiftBuildTimeout() {
      config.openshiftBuildTimeout
  }

  def setEnvironmentCreated(boolean created) {
      this.environmentCreated = created
  }

  private String retrieveGitUrl() {
    script.sh(
      returnStdout: true, script: 'git config --get remote.origin.url'
    ).trim().replace('https://bitbucket', 'https://cd_user@bitbucket')
  }

  private String retrieveGitCommit() {
    script.sh(
      returnStdout: true, script: 'git rev-parse HEAD'
    ).trim()
  }

  private String retrieveGitCommitAuthor() {
    script.sh(
      returnStdout: true, script: "git --no-pager show -s --format='%an (%ae)' HEAD"
    ).trim()
  }

  private String retrieveGitCommitMessage() {
    script.sh(
            returnStdout: true, script: "git log -1 --pretty=%B HEAD"
    ).trim()
  }

  private String retrieveGitCommitTime() {
    script.sh(
            returnStdout: true, script: "git show -s --format=%ci HEAD"
    ).trim()
  }

  private String retrieveGitBranch() {
    def pipelinePrefix = "${config.openshiftProjectId}/${config.openshiftProjectId}-"
    def buildConfigName = config.jobName.substring(pipelinePrefix.size())

    script.sh(
      returnStdout: true,
      script: "oc get bc/${buildConfigName} -n ${config.openshiftProjectId} -o jsonpath='{.spec.source.git.ref}'"
    ).trim()
  }
  // looks for string [ci skip] in commit message
  boolean getCiSkip() {
    script.sh(
            returnStdout: true, script: 'git show --pretty=%s%b -s'
    ).toLowerCase().contains('[ci skip]')
  }

  // Given a branch like "feature/HUGO-4-brown-bag-lunch", it extracts
  // "HUGO-4" from it.
  private String extractBranchCode(String branch) {
      if (branch.startsWith("feature/")) {
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

  private String constructCredentialBitbucketURL(String url, String userPass) {
      return url.replace("cd_user", userPass.replace('@', '%40')).replaceAll("[\n\r]","").trim()
  }

  // This logic must be consistent with what is described in README.md.
  // To make it easier to follow the logic, it is broken down by workflow (at
  // the cost of having some duplication).
  void determineEnvironment() {
    // Fixed name
    def env = config.branchToEnvironmentMapping[config.gitBranch]
    if (env) {
      config.environment = env
      config.cloneSourceEnv = null
      return
    }

    // Prefix
    // Loop needs to be done like this due to
    // https://issues.jenkins-ci.org/browse/JENKINS-27421 and
    // https://issues.jenkins-ci.org/browse/JENKINS-35191.
    for (def key : config.branchToEnvironmentMapping.keySet()) {
      if (config.gitBranch.startsWith(key)) {
        setMostSpecificEnvironment(
          config.branchToEnvironmentMapping[key],
          config.gitBranch.replace(key, "")
        )
        return
      }
    }

    // Any branch
    def genericEnv = config.branchToEnvironmentMapping["*"]
    if (genericEnv) {
      setMostSpecificEnvironment(
        genericEnv,
        config.gitBranch.replace("/", "")
      )
      return
    }

    logger.info "No environment to deploy to was determined " +
      "[gitBranch=${config.gitBranch}, projectId=${config.projectId}]"
    config.environment = ""
    config.cloneSourceEnv = ""
  }

  // Based on given +genericEnv+ (e.g. "preview") and +branchSuffix+ (e.g.
  // "foo-123-bar"), it finds the most specific environment. This is either:
  // - the +genericEnv+ suffixed with a numeric ticket ID
  // - the +genericEnv+ suffixed with the +branchSuffix+
  // - the +genericEnv+ without suffix
  protected void setMostSpecificEnvironment(String genericEnv, String branchSuffix) {
    def specifcEnv = genericEnv + "-" + branchSuffix

    def ticketId = getTicketIdFromBranch(config.gitBranch, config.projectId)
    if (ticketId) {
      specifcEnv = genericEnv + "-" + ticketId
    }

    config.cloneSourceEnv = config.autoCloneEnvironmentsFromSourceMapping[genericEnv]
    def autoCloneEnabled = !!config.cloneSourceEnv
    if (autoCloneEnabled || environmentExists(specifcEnv)) {
      config.environment = specifcEnv
    } else {
      config.environment = genericEnv
    }
  }

  protected String getTicketIdFromBranch(String branchName, String projectId) {
    def tokens = extractBranchCode(branchName).split("-")
    def pId = tokens[0]
    if (!pId || !pId.equalsIgnoreCase(projectId)) {
      return ""
    }
    if (!tokens[1].isNumber()) {
      return ""
    }
    return tokens[1]
  }

  protected boolean environmentExists(String name) {
    def statusCode = script.sh(
      script:"oc project ${name} &> /dev/null",
      returnStatus: true
    )
    return statusCode == 0
  }

}
