package org.ods.component

import com.cloudbees.groovy.cps.NonCPS
import groovy.json.JsonOutput
import groovy.json.JsonSlurperClassic
import org.ods.services.BitbucketService
import org.ods.services.GitService
import org.ods.services.NexusService
import org.ods.services.OpenShiftService
import org.ods.util.ILogger
import org.ods.util.IPipelineSteps
import org.ods.util.PipelineSteps
import org.ods.util.ShellWithRetry

import java.util.concurrent.ExecutionException

@SuppressWarnings(['MethodCount', 'UnnecessaryObjectReferences'])
class Context implements IContext {

    static final int MAX_RETRIES = 5
    static final int WAIT_TIME_SECONDS = 1

    final List excludeFromContextDebugConfig = ['nexusPassword', 'nexusUsername']
    // script is the context of the Jenkinsfile. That means that things like "sh" need to be called on script.
    private final def script
    // config is a map of config properties to customise the behaviour.
    private final Map config
    private final IPipelineSteps steps
    private final ILogger logger
    // artifact store, the interface to MRP
    private final def artifactUriStore = [builds: [:], deployments: [:]]

    // is the library checking out the code again, or relying on check'd out source?
    private final boolean localCheckoutEnabled

    private String appDomain

    Context(def script, Map config, ILogger logger, boolean localCheckoutEnabled = true) {
        this.script = script
        this.config = config
        this.logger = logger
        this.localCheckoutEnabled = localCheckoutEnabled
        this.steps = new PipelineSteps(script)
    }

    def assemble() {
        int retry = 0
        boolean executedWithErrors = true

        while (executedWithErrors && retry++ < MAX_RETRIES) {
            try {
                assembleWithRetry()
                executedWithErrors = false
            } catch (java.io.NotSerializableException err) {
                logger.warn ("WARN: Jenkins serialization issue; attempt #: ${retry}, when: context.assemble()")
                script.sleep(WAIT_TIME_SECONDS)
            }
        }

        if (executedWithErrors) {
            throw new ExecutionException("Jenkins serialization issue, when: context.assemble()")
        }
    }

    @SuppressWarnings(['AbcMetric', 'CyclomaticComplexity', 'MethodSize', 'Instanceof'])
    def assembleWithRetry() {
        logger.debug 'Validating input ...'
        // branchToEnvironmentMapping must be given, but it is OK to be empty - e.g.
        // if the repository should not be deployed to OpenShift at all.
        if (!config.containsKey('branchToEnvironmentMapping') ||
            !(config.branchToEnvironmentMapping instanceof Map)) {
            throw new IllegalArgumentException("Param 'branchToEnvironmentMapping, type: Map' is required")
        }

        logger.debug 'Collecting environment variables ...'
        config.jobName = script.env.JOB_NAME
        config.buildNumber = script.env.BUILD_NUMBER
        config.buildUrl = script.env.BUILD_URL
        config.buildTag = script.env.BUILD_TAG
        config.buildTime = new Date()
        config.dockerRegistry = script.env.DOCKER_REGISTRY
        config.openshiftHost = script.env.OPENSHIFT_API_URL
        config << BitbucketService.readConfigFromEnv(script.env)
        config << NexusService.readConfigFromEnv(script.env)
        config.triggeredByOrchestrationPipeline = !!script.env.MULTI_REPO_BUILD

        config.odsBitbucketProject = script.env.ODS_BITBUCKET_PROJECT ?: 'opendevstack'

        config.sonarQubeEdition = script.env.SONAR_EDITION ?: 'community'

        config.globalExtensionImageLabels = getExtensionBuildParams()
        config.globalExtensionImageLabels.putAll(getEnvParamsAndAddPrefix('OPENSHIFT_BUILD',
            'JENKINS_MASTER_'))
        config.globalExtensionImageLabels.putAll(getEnvParamsAndAddPrefix('BUILD_',
            ''))

        logger.debug("Got external build labels: ${config.globalExtensionImageLabels}")

        config.odsSharedLibVersion = script.sh(
            script: "env | grep 'library.ods-jenkins-shared-library.version' | cut -d= -f2",
            returnStdout: true,
            label: 'getting ODS shared lib version'
        ).trim()

        logger.debug 'Validating environment variables ...'
        if (!config.jobName) {
            throw new IllegalArgumentException('JOB_NAME is required, but not set (usually provided by Jenkins)')
        }
        if (!config.buildNumber) {
            throw new IllegalArgumentException('BUILD_NUMBER is required, but not set (usually provided by Jenkins)')
        }
        if (!config.buildTag) {
            throw new IllegalArgumentException('BUILD_TAG is required, but not set (usually provided by Jenkins)')
        }
        if (!config.openshiftHost) {
            throw new IllegalArgumentException('OPENSHIFT_API_URL is required, but not set')
        }
        if (!config.buildUrl) {
            logger.info 'BUILD_URL is required to set a proper build status in ' +
                'BitBucket, but it is not present. Normally, it is provided ' +
                'by Jenkins - please check your JenkinsUrl configuration.'
        }

        logger.debug 'Deriving configuration from input ...'
        config.cdProject = "${config.projectId}-cd"
        config.credentialsId = config.cdProject + '-cd-user-with-password'

        logger.debug 'Setting defaults ...'
        if (!config.containsKey('failOnSnykScanVulnerabilities')) {
            config.failOnSnykScanVulnerabilities = true
        }
        if (!config.containsKey('environmentLimit')) {
            config.environmentLimit = 5
        }
        if (!config.containsKey('openshiftBuildTimeout')) {
            config.openshiftBuildTimeout = 15 // minutes
        }
        if (!config.containsKey('openshiftBuildTimeoutRetries')) {
            config.openshiftBuildTimeoutRetries = 5 // retries
        }
        if (!config.containsKey('openshiftRolloutTimeout')) {
            config.openshiftRolloutTimeout = 15 // minutes
        }
        if (!config.containsKey('openshiftRolloutTimeoutRetries')) {
            config.openshiftRolloutTimeoutRetries = 5 // retries
        }
        if (!config.containsKey('emailextRecipients')) {
            config.emailextRecipients = null
        }
        if (!config.groupId) {
            config.groupId = "org.opendevstack.${config.projectId}"
        }

        logger.debug 'Retrieving Git information ...'
        config.gitUrl = retrieveGitUrl()
        logger.debug("Retrieved Git Url: ${config.gitUrl}")
        config.gitBranch = retrieveGitBranch()
        logger.debug("Retrieved Git Branch: ${config.gitBranch}")
        config.gitCommit = retrieveGitCommit()
        logger.debug("Retrieved Git Commit: ${config.gitCommit}")
        config.gitCommitAuthor = retrieveGitCommitAuthor()
        config.gitCommitMessage = retrieveGitCommitMessage()
        config.gitCommitRawMessage = retrieveGitCommitRawMessage()
        config.gitCommitTime = retrieveGitCommitTime()
        config.tagversion = "${config.buildNumber}-${getShortGitCommit()}"

        if (!config.containsKey('testResults')) {
            config.testResults = ''
        }

        if (!config.containsKey('dockerDir')) {
            config.dockerDir = 'docker'
        }

        logger.debug "Setting target OCP environment, rm context? ${config.triggeredByOrchestrationPipeline}"
        determineEnvironment()
        if (config.environment) {
            config.targetProject = "${config.projectId}-${config.environment}"
        }

        if (!config.containsKey('commitGitWorkingTree')) {
            config.commitGitWorkingTree = false
        }
        logger.debug "Commit workingtree? ${config.commitGitWorkingTree}"

        // clone the map and overwrite exclusions
        Map debugConfig = new JsonSlurperClassic().
            parseText(JsonOutput.toJson(config))

        excludeFromContextDebugConfig.each { exclusion ->
            if (debugConfig[exclusion]) {
                debugConfig[exclusion] = '****'
            }
        }

        logger.debug "Assembled configuration: ${debugConfig}"
    }

    def amendWithAgentInformation() {
        if (!config.globalExtensionImageLabels) {
            config.globalExtensionImageLabels = [:]
        }
        // get the build labels from the env running in ..
        config.globalExtensionImageLabels.putAll(getEnvParamsAndAddPrefix('OPENSHIFT_BUILD',
            'JENKINS_AGENT_'))
    }

    boolean getDebug() {
        config.debug
    }

    void setDebug(def debug) {
        config.debug = debug
    }

    List<String> getEmailextRecipients() {
        config.emailextRecipients
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

    String getBuildTag() {
        config.buildTag
    }

    String getBuildTime() {
        config.buildTime
    }

    @NonCPS
    String getGitBranch() {
        config.gitBranch
    }

    @NonCPS
    String getCredentialsId() {
        config.credentialsId
    }

    String getGitUrl() {
        config.gitUrl
    }

    @NonCPS
    String getTagversion() {
        config.tagversion
    }

    String getLastSuccessfulCommit() {
        retrieveLastSuccessfulCommit()
    }

    String[] getCommittedFiles() {
        def lastSuccessfulCommit = getLastSuccessfulCommit()
        retrieveGitCommitFiles(lastSuccessfulCommit)
    }

    @NonCPS
    String getNexusUrl() {
        config.nexusUrl
    }

    @NonCPS
    String getNexusHost() {
        getNexusUrl()
    }

    @NonCPS
    String getNexusHostWithoutScheme() {
        getNexusUrl().minus(~/^https?:\/\//)
    }

    @NonCPS
    String getNexusUsername() {
        config.nexusUsername
    }

    @NonCPS
    String getNexusPassword() {
        config.nexusPassword
    }

    @NonCPS
    String getNexusUrlWithBasicAuth() {
        String un = URLEncoder.encode(config.nexusUsername as String, 'UTF-8')
        String pw = URLEncoder.encode(config.nexusPassword as String, 'UTF-8')
        config.nexusUrl.replace('://', "://${un}:${pw}@")
    }

    @NonCPS
    String getNexusHostWithBasicAuth() {
        getNexusUrlWithBasicAuth()
    }

    @NonCPS
    Map<String, String> getBranchToEnvironmentMapping() {
        config.branchToEnvironmentMapping
    }

    @NonCPS
    String getEnvironment() {
        config.environment
    }

    void setEnvironment(String environment) {
        config.environment = environment
    }

    @NonCPS
    String getGroupId() {
        config.groupId
    }

    @NonCPS
    String getProjectId() {
        config.projectId
    }

    @NonCPS
    String getComponentId() {
        config.componentId
    }

    @NonCPS
    String getSelector() {
        "app=${config.projectId}-${config.componentId}"
    }

    @NonCPS
    String getRepoName() {
        config.repoName
    }

    @NonCPS
    String getGitCommit() {
        config.gitCommit
    }

    @NonCPS
    String getShortGitCommit() {
        config.gitCommit.take(8)
    }

    String getGitCommitAuthor() {
        config.gitCommitAuthor
    }

    String getGitCommitMessage() {
        config.gitCommitMessage
    }

    String getGitCommitRawMessage() {
        config.gitCommitRawMessage
    }

    String getGitCommitTime() {
        config.gitCommitTime
    }

    @NonCPS
    String getTargetProject() {
        config.targetProject
    }

    @NonCPS
    String getCdProject() {
        config.cdProject
    }

    @NonCPS
    String getSonarQubeEdition() {
        config.sonarQubeEdition
    }

    @NonCPS
    String getSonarQubeNexusRepository() {
        config.sonarQubeNexusRepository
    }

    @NonCPS
    String getSonarQubeBranch() {
        config.sonarQubeBranch
    }

    void setSonarQubeBranch(String sonarQubeBranch) {
        config.sonarQubeBranch = sonarQubeBranch
    }

  	@NonCPS
    boolean getSonarExecuted() {
        config.sonarExecuted
    }

  	void setSonarExecuted(boolean executed) {
        config.sonarExecuted = executed
    }

    @NonCPS
    boolean getFailOnSnykScanVulnerabilities() {
        config.failOnSnykScanVulnerabilities
    }

    @NonCPS
    boolean getTriggeredByOrchestrationPipeline() {
        config.triggeredByOrchestrationPipeline
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

    String getBitbucketUrl() {
        config.bitbucketUrl
    }

    String getBitbucketHost() {
        getBitbucketUrl()
    }

    @NonCPS
    String getBitbucketHostWithoutScheme() {
        getBitbucketUrl().minus(~/^https?:\/\//)
    }

    @NonCPS
    Integer getOpenshiftBuildTimeout() {
        config.openshiftBuildTimeout
    }

    @NonCPS
    Integer getOpenshiftRolloutTimeout() {
        config.openshiftRolloutTimeout
    }

    String getTestResults() {
        return config.testResults
    }

    @NonCPS
    String getDockerDir() {
        return config.dockerDir
    }

    boolean environmentExists(String name) {
        def statusCode = script.sh(
            script: "oc project ${name} &> /dev/null",
            label: "check if OCP environment ${name} exists",
            returnStatus: true
        )
        return statusCode == 0
    }

    String getIssueId() {
        if (!config.containsKey("issueId")) {
            config.issueId = GitService.issueIdFromBranch(
                config.gitBranch, config.projectId
            ) ?: GitService.issueIdFromCommit(
                config.gitCommitMessage, config.projectId
            )
        }
        config.issueId
    }

    // This logic must be consistent with what is described in README.md.
    // To make it easier to follow the logic, it is broken down by workflow (at
    // the cost of having some duplication).
    void determineEnvironment() {
        if (config.environment) {
            // environment already set
            return
        }
        // Fixed name
        def env = config.branchToEnvironmentMapping[config.gitBranch]
        // this is for cases where we set a $key into the map - e.g. prov app / doc gen
        if (!env) {
            env = config.branchToEnvironmentMapping.get("${config.gitBranch}")
        }
        if (env) {
            config.environment = env
            logger.debug("Target env: ${env}")
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
                    config.gitBranch.replace(key, '')
                )
                return
            }
        }

        // Any branch
        def genericEnv = config.branchToEnvironmentMapping['*']
        if (genericEnv) {
            setMostSpecificEnvironment(
                genericEnv,
                config.gitBranch.replace('/', '')
            )
            return
        }

        logger.info 'No environment to deploy to was determined.\r' +
            "[gitBranch=${config.gitBranch}, projectId=${config.projectId}]"
        config.environment = ''
    }

    Map<String, Object> getBuildArtifactURIs() {
        return artifactUriStore.asImmutable()
    }

    void addArtifactURI(String key, value) {
        artifactUriStore.put(key, value)
    }

    void addBuildToArtifactURIs (String buildConfigName, Map <String, String> buildInformation) {
        artifactUriStore.builds [buildConfigName] = buildInformation
    }

    void addDeploymentToArtifactURIs (String deploymentConfigName, Map deploymentInformation) {
        artifactUriStore.deployments [deploymentConfigName] = deploymentInformation
    }

    // get extension image labels
    @NonCPS
    Map<String, String> getExtensionImageLabels () {
        return config.globalExtensionImageLabels
    }

    // set and add image labels
    @NonCPS
    void setExtensionImageLabels (Map <String, String> extensionLabels) {
        if (extensionLabels) {
            config.globalExtensionImageLabels.putAll(extensionLabels)
        }
    }

    Map<String,String> getExtensionBuildParams () {
        return getEnvParamsAndAddPrefix()
    }

    Map<String,String> getEnvParamsAndAddPrefix (String envNamePattern = 'ods.build.', String keyPrefix = '') {
        String rawEnv = new ShellWithRetry(script, logger).execute(
            returnStdout: true,
            script: "env | grep ${envNamePattern} || true",
            label: 'getting extension labels from current environment')

        if (rawEnv.length() == 0 ) {
            return [:]
        }

        return normalizeEnvironment(rawEnv, keyPrefix)
    }

    @NonCPS
    Map<String,String> normalizeEnvironment (String rawEnv, String keyPrefix) {
        def lineSplitEnv = rawEnv.normalize().split(System.getProperty('line.separator'))
        Map normalizedEnv = [ : ]
        for (int lineC = 0; lineC < lineSplitEnv.size(); lineC++) {
            def splittedLine = lineSplitEnv[lineC].toString().tokenize('=')
            normalizedEnv.put(keyPrefix + splittedLine[0], splittedLine[1])
        }
        return normalizedEnv
    }

    String getOpenshiftApplicationDomain () {
        def appDomain = appDomain
        if (!appDomain) {
            this.appDomain = appDomain = OpenShiftService.getApplicationDomain(steps)
        }
        return appDomain
    }

    // set the rollout retry
    void setOpenshiftRolloutTimeoutRetries (int retries) {
        config.openshiftRolloutTimeoutRetries = retries
    }

    // get the rollout retry
    @NonCPS
    int getOpenshiftRolloutTimeoutRetries () {
        config.openshiftRolloutTimeoutRetries
    }

    // set the build retry
    void setOpenshiftBuildTimeoutRetries (int retries) {
        config.openshiftBuildTimeoutRetries = retries
    }

    // get the build retry
    @NonCPS
    int getOpenshiftBuildTimeoutRetries () {
        config.openshiftBuildTimeoutRetries
    }

    // set to commit the working tree after custom work
    void setCommitGitWorkingTree (boolean commit) {
        config.commitGitWorkingTree = commit
    }

    // get commit the working tree
    @NonCPS
    boolean getCommitGitWorkingTree () {
        config.commitGitWorkingTree
    }

    @NonCPS
    String getClusterRegistryAddress () {
        config.dockerRegistry
    }

    private String retrieveGitUrl() {
        def gitUrl = script.sh(
            returnStdout: true,
            script: 'git config --get remote.origin.url',
            label: 'getting GIT url'
        ).trim()
        return gitUrl
    }

    private String retrieveGitCommit() {
        script.sh(
            returnStdout: true, script: 'git rev-parse HEAD',
            label: 'getting GIT commit'
        ).trim()
    }

    private String retrieveGitCommitAuthor() {
        script.sh(
            returnStdout: true, script: "git --no-pager show -s --format='%an (%ae)' HEAD",
            label: 'getting GIT commit author'
        ).trim()
    }

    private String retrieveGitCommitMessage() {
        script.sh(
            returnStdout: true, script: "git log -1 --format='%f' HEAD",
            label: 'getting GIT commit message'
        ).trim()
    }

    private String retrieveGitCommitRawMessage() {
        script.sh(
            returnStdout: true, script: "git log -1 --pretty=%B HEAD",
            label: 'getting raw GIT commit message'
        ).trim()
    }

    private String retrieveLastSuccessfulCommit() {
        def lastSuccessfulBuild = script.currentBuild.rawBuild.getPreviousSuccessfulBuild()
        if (!lastSuccessfulBuild) {
            logger.info 'There seems to be no last successful build.'
            return ''
        }
        return commitHashForBuild(lastSuccessfulBuild)
    }

    private String commitHashForBuild(build) {
        return build
            .getActions(hudson.plugins.git.util.BuildData)
            .find { action -> action.getRemoteUrls().contains(config.gitUrl) }
            .getLastBuiltRevision().getSha1String()
    }

    private String[] retrieveGitCommitFiles(String lastSuccessfulCommitHash) {
        if (!lastSuccessfulCommitHash) {
            logger.info("Didn't find the last successful commit. Can't return the committed files.")
            return []
        }
        return script.sh(
            returnStdout: true,
            label: 'getting git commit files',
            script: "git diff-tree --no-commit-id --name-only -r ${config.gitCommit}"
        ).trim().split()
    }

    private String retrieveGitCommitTime() {
        script.sh(
            returnStdout: true,
            script: 'git show -s --format=%ci HEAD',
            label: 'getting GIT commit date/time'
        ).trim()
    }

    private String retrieveGitBranch() {
        def branch
        if (this.localCheckoutEnabled) {
            def pipelinePrefix = "${config.cdProject}/${config.cdProject}-"
            def buildConfigName = config.jobName.substring(pipelinePrefix.size())

            def n = config.cdProject
            branch = script.sh(
                returnStdout: true,
                label: 'getting GIT branch to build',
                script: "oc get bc/${buildConfigName} -n ${n} -o jsonpath='{.spec.source.git.ref}'"
            ).trim()
        } else {
            // in case code is already checked out, OpenShift build config can not be used for retrieving branch
            branch = script.sh(
                returnStdout: true,
                script: 'git rev-parse --abbrev-ref HEAD',
                label: 'getting GIT branch to build').trim()
            branch = script.sh(
                returnStdout: true,
                script: "git name-rev --name-only --exclude=tags/* ${branch} | cut -d ' ' -f2  |" +
                    " sed -e 's|remotes/origin/||g'",
                label: 'resolving to real GIT branch to build').trim()
        }
        logger.debug "resolved branch ${branch}"
        return branch
    }

    // Based on given +genericEnv+ (e.g. "preview") and +branchSuffix+ (e.g.
    // "foo-123-bar"), it finds the most specific environment. This is either:
    // - the +genericEnv+ suffixed with a numeric ticket ID
    // - the +genericEnv+ suffixed with the +branchSuffix+
    // - the +genericEnv+ without suffix
    private void setMostSpecificEnvironment(String genericEnv, String branchSuffix) {
        def specifcEnv = genericEnv + '-' + branchSuffix
        def ticketId = GitService.issueIdFromBranch(config.gitBranch, config.projectId)
        if (ticketId) {
            specifcEnv = genericEnv + '-' + ticketId
        }

        if (environmentExists(specifcEnv)) {
            config.environment = specifcEnv
        } else {
            config.environment = genericEnv
        }
    }

}
