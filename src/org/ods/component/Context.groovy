package org.ods.component

import com.cloudbees.groovy.cps.NonCPS

@SuppressWarnings('MethodCount')
class Context implements IContext {

    // script is the context of the Jenkinsfile. That means that things like "sh" need to be called on script.
    private def script
    // config is a map of config properties to customise the behaviour.
    private Map config
    private Logger logger

    private def artifactUriStore = [builds: [:], deployments: [:]]

    private boolean localCheckoutEnabled

    Context(def script, Map config, Logger logger, boolean localCheckoutEnabled = true) {
        this.script = script
        this.config = config
        this.logger = logger
        this.localCheckoutEnabled = localCheckoutEnabled
    }

    @SuppressWarnings(['AbcMetric', 'CyclomaticComplexity'])
    def assemble() {
        logger.debug "Validating input ..."
        // branchToEnvironmentMapping must be given, but it is OK to be empty - e.g.
        // if the repository should not be deployed to OpenShift at all.
        if (!config.containsKey('branchToEnvironmentMapping')) {
            throw new IllegalArgumentException("Param 'branchToEnvironmentMapping' is required")
        }

        logger.debug "Collecting environment variables ..."
        config.jobName = script.env.JOB_NAME
        config.buildNumber = script.env.BUILD_NUMBER
        config.buildUrl = script.env.BUILD_URL
        config.buildTag = script.env.BUILD_TAG
        config.buildTime = new Date()
        config.nexusHost = script.env.NEXUS_HOST
        config.nexusUsername = script.env.NEXUS_USERNAME
        config.nexusPassword = script.env.NEXUS_PASSWORD
        config.openshiftHost = script.env.OPENSHIFT_API_URL

        if (script.env.BITBUCKET_URL) {
            config.bitbucketUrl = script.env.BITBUCKET_URL
            config.bitbucketHost = config.bitbucketUrl.minus(~/^https?:\/\//)
        } else if (script.env.BITBUCKET_HOST) {
            config.bitbucketHost = script.env.BITBUCKET_HOST
            config.bitbucketUrl = "https://${config.bitbucketHost}"
        }

        config.globalExtensionImageLabels = getExtensionBuildParams()

        logger.debug("Got external build labels: ${config.globalExtensionImageLabels}")

        config.odsSharedLibVersion = script.sh(
            script: "env | grep 'library.ods-jenkins-shared-library.version' | cut -d= -f2",
            returnStdout: true,
            label: 'getting ODS shared lib version'
        ).trim()

        logger.debug "Validating environment variables ..."
        if (!config.jobName) {
            throw new IllegalArgumentException('JOB_NAME is required, but not set (usually provided by Jenkins)')
        }
        if (!config.buildNumber) {
            throw new IllegalArgumentException('BUILD_NUMBER is required, but not set (usually provided by Jenkins)')
        }
        if (!config.buildTag) {
            throw new IllegalArgumentException('BUILD_TAG is required, but not set (usually provided by Jenkins)')
        }
        if (!config.nexusHost) {
            throw new IllegalArgumentException('NEXUS_HOST is required, but not set')
        }
        if (!config.nexusUsername) {
            throw new IllegalArgumentException('NEXUS_USERNAME is required, but not set')
        }
        if (!config.nexusPassword) {
            throw new IllegalArgumentException('NEXUS_PASSWORD is required, but not set')
        }
        if (!config.openshiftHost) {
            throw new IllegalArgumentException('OPENSHIFT_API_URL is required, but not set')
        }
        if (!config.bitbucketUrl) {
            throw new IllegalArgumentException('BITBUCKET_URL is required, but not set')
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
        if (!config.containsKey('cloneProjectScriptBranch')) {
            config.cloneProjectScriptBranch = 'production'
        }
        if (config.containsKey('sonarQubeBranch')) {
            script.echo "Setting option 'sonarQubeBranch' of the pipeline is deprecated, " +
                "please use option 'branch' of the stage."
        } else {
            config.sonarQubeBranch = 'master'
        }
        if (!config.containsKey('failOnSnykScanVulnerabilities')) {
            config.failOnSnykScanVulnerabilities = true
        }
        if (!config.containsKey('dependencyCheckBranch')) {
            config.dependencyCheckBranch = 'master'
        }
        if (!config.containsKey('environmentLimit')) {
            config.environmentLimit = 5
        }
        if (!config.containsKey('openshiftBuildTimeout')) {
            config.openshiftBuildTimeout = 15 // minutes
        }
        if (!config.containsKey('openshiftRolloutTimeout')) {
            config.openshiftRolloutTimeout = 5 // minutes
        }
        if (!config.groupId) {
            config.groupId = "org.opendevstack.${config.projectId}"
        }

        logger.debug "Retrieving Git information ..."
        config.gitUrl = retrieveGitUrl()
        config.gitBranch = retrieveGitBranch()
        config.gitCommit = retrieveGitCommit()
        config.gitCommitAuthor = retrieveGitCommitAuthor()
        config.gitCommitMessage = retrieveGitCommitMessage()
        config.gitCommitTime = retrieveGitCommitTime()
        config.tagversion = "${config.buildNumber}-${config.gitCommit.take(8)}"

        if (!config.containsKey('testResults')) {
            config.testResults = ''
        }

        if (!config.containsKey('dockerDir')) {
            config.dockerDir = 'docker'
        }

        logger.debug "Setting environment ..."
        determineEnvironment()
        if (config.environment) {
            config.targetProject = "${config.projectId}-${config.environment}"
        }

        logger.debug "Assembled configuration: ${config}"
    }

    boolean getDebug() {
        config.debug
    }

    void setDebug(def debug) {
        config.debug = debug
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
    String getNexusHost() {
        config.nexusHost
    }

    @NonCPS
    String getNexusUsername() {
        config.nexusUsername
    }

    @NonCPS
    String getNexusPassword() {
        config.nexusPassword
    }

    String getNexusHostWithBasicAuth() {
        config.nexusHost.replace("://", "://${config.nexusUsername}:${config.nexusPassword}@")
    }

    @NonCPS
    Map<String, String> getBranchToEnvironmentMapping() {
        config.branchToEnvironmentMapping
    }

    String getAutoCloneEnvironmentsFromSourceMapping() {
        config.autoCloneEnvironmentsFromSourceMapping
    }

    String getCloneSourceEnv() {
        config.cloneSourceEnv
    }

    void setCloneSourceEnv(String cloneSourceEnv) {
        config.cloneSourceEnv = cloneSourceEnv
    }

    String getCloneProjectScriptBranch() {
        config.cloneProjectScriptBranch
    }

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

    @NonCPS
    String getSonarQubeBranch() {
        config.sonarQubeBranch
    }

    void setSonarQubeBranch(String sonarQubeBranch) {
        config.sonarQubeBranch = sonarQubeBranch
    }

    @NonCPS
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

    String getBitbucketUrl() {
        config.bitbucketUrl
    }

    String getBitbucketHost() {
        config.bitbucketHost
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

    private String retrieveGitUrl() {
        def gitUrl = script.sh(
            returnStdout: true, script: 'git config --get remote.origin.url',
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
            returnStdout: true, script: "git log -1 --pretty=%B HEAD",
            label: 'getting GIT commit message'
        ).trim()
    }

    private String retrieveLastSuccessfulCommit() {
        def lastSuccessfulBuild = script.currentBuild.rawBuild.getPreviousSuccessfulBuild()
        if (!lastSuccessfulBuild) {
            logger.info("There seems to be no last successful build.")
            return ""
        }
        return commitHashForBuild(lastSuccessfulBuild)
    }

    private String commitHashForBuild(build) {
        return build
            .getActions(hudson.plugins.git.util.BuildData.class)
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
            returnStdout: true, script: "git show -s --format=%ci HEAD",
            label: 'getting GIT commit date/time'
        ).trim()
    }

    private String retrieveGitBranch() {
        def branch
        if (this.localCheckoutEnabled) {
            def pipelinePrefix = "${config.openshiftProjectId}/${config.openshiftProjectId}-"
            def buildConfigName = config.jobName.substring(pipelinePrefix.size())

            def n = config.openshiftProjectId
            branch = script.sh(
                returnStdout: true,
                label: 'getting GIT branch to build',
                script: "oc get bc/${buildConfigName} -n ${n} -o jsonpath='{.spec.source.git.ref}'"
            ).trim()
        } else {
            // in case code is already checked out, OpenShift build config can not be used for retrieving branch
            branch = script.sh(
                returnStdout: true,
                script: "git rev-parse --abbrev-ref HEAD",
                label: 'getting GIT branch to build').trim()
            branch = script.sh(
                returnStdout: true,
                script: "git name-rev ${branch} | cut -d ' ' -f2  | sed -e 's|remotes/origin/||g'",
                label: 'resolving to real GIT branch to build').trim()
        }
        logger.debug "resolved branch ${branch}"
        return branch
    }

    boolean environmentExists(String name) {
        def statusCode = script.sh(
            script: "oc project ${name} &> /dev/null",
            label: "check if OCP environment ${name} exists",
            returnStatus: true
        )
        return statusCode == 0
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
        if (env) {
            config.environment = env
            config.cloneSourceEnv = environmentExists(env)
                ? false
                : config.autoCloneEnvironmentsFromSourceMapping[env]
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

        logger.info "No environment to deploy to was determined, returning" +
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

    Map<String, String> getCloneProjectScriptUrls() {
        def scripts = ['clone-project.sh', 'import-project.sh', 'export-project.sh']
        def m = [:]
        def branch = getCloneProjectScriptBranch().replace('/', '%2F')
        def baseUrl = "${config.bitbucketUrl}/projects/OPENDEVSTACK/repos/ods-core/raw/ocp-scripts"
        for (script in scripts) {
            def url = "${baseUrl}/${script}?at=refs%2Fheads%2F${branch}"
            m.put(script, url)
        }
        return m
    }

    public Map<String, Object> getBuildArtifactURIs() {
        return artifactUriStore.asImmutable()
    }

    public void addArtifactURI(String key, value) {
        artifactUriStore.put(key, value)
    }

    public void addBuildToArtifactURIs (String buildConfigName, Map <String, String> buildInformation) {
        artifactUriStore.builds [buildConfigName] = buildInformation
    }

    public void addDeploymentToArtifactURIs (String deploymentConfigName, Map deploymentInformation) {
        artifactUriStore.deployments [deploymentConfigName] = deploymentInformation
    }

    // get extension image labels
    @NonCPS
    public Map<String, String> getExtensionImageLabels () {
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
        String rawEnv = script.sh(
            returnStdout: true, script: "env | grep ods.build. || true",
            label: 'getting extension environment labels'
          ).trim()

        if (rawEnv.size() == 0 ) {
            return [:]
        }

        return rawEnv.normalize().split(System.getProperty("line.separator")).inject([ : ] ) { kvMap, line ->
            Iterator kv = line.toString().tokenize("=").iterator()
            kvMap.put(kv.next(), kv.hasNext() ? kv.next() : "")
            kvMap
        }
    }

    // set the application domain
    void setOpenshiftApplicationDomain (String domain) {
        config.domain = domain
    }

    // get the application domain
    String getOpenshiftApplicationDomain () {
        return config.domain
    }

}
