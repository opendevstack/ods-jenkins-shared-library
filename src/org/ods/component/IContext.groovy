package org.ods.component

@SuppressWarnings('MethodCount')
interface IContext {

    def assemble()

    def amendWithAgentInformation()

    // Get debug mode
    boolean getDebug()

    // Jenkins email extension recipients list
    // docs: https://www.jenkins.io/doc/pipeline/steps/email-ext/
    List<String> getEmailextRecipients()

    // Get the location of the xmlunit results
    String getTestResults()

    // Value of JOB_NAME. It is the name of the project of the build.
    String getJobName()

    // Value of BUILD_NUMBER. The current build number, such as "153".
    String getBuildNumber()

    // Value of BUILD_URL. The URL where the results of the build can be found
    // E.g. http://buildserver/jenkins/job/MyJobName/123/
    String getBuildUrl()

    // Value of BUILD_TAG. The tag of the build as a string in the format 'jenkins-${JOB_NAME}-${BUILD_NUMBER}'
    String getBuildTag()

    // Time of the build, collected when the odsPipeline starts.
    String getBuildTime()

    // Credentials identifier (Credentials are created and named automatically by the OpenShift Jenkins plugin).
    String getCredentialsId()

    // The tagversion is made up of the build number and the first 8 chars of the commit SHA.
    String getTagversion()

    // Nexus URL - value taken from NEXUS_URL.
    String getNexusUrl()

    // Nexus host - alias for getNexusUrl.
    String getNexusHost()

    // Nexus host without scheme/protocol.
    String getNexusHostWithoutScheme()

    // Nexus username.
    String getNexusUsername()

    // Nexus password.
    String getNexusPassword()

    // Nexus URL (with scheme), including username and password as BasicAuth.
    String getNexusUrlWithBasicAuth()

    // Nexus host (with scheme), including username and password as BasicAuth. Alias for getNexusUrlWithBasicAuth.
    String getNexusHostWithBasicAuth()

    // Define which branches are deployed to which environments.
    Map<String, String> getBranchToEnvironmentMapping()

    // The environment which was chosen as the deployment target, e.g. "dev".
    String getEnvironment()

    // Set environment
    void setEnvironment(String environment)

    // Target project, based on the environment. E.g. "foo-dev".
    String getTargetProject()

    // Name of the CD project
    String getCdProject()

    // Group ID, defaults to: org.opendevstack.<projectID>.
    String getGroupId()

    // Project ID, e.g. "foo".
    String getProjectId()

    // Component ID, e.g. "be-auth".
    String getComponentId()

    // Selector common to all resources of component, e.g. "app=foo-be-auth"
    String getSelector()

    // Repository name, e.g. "foo-be-auth".
    String getRepoName()

    // Git URL of repository
    String getGitUrl()

    // Git branch for which the build runs.
    String getGitBranch()

    // Git commit SHA to build.
    String getGitCommit()

    // Shortened Git commit SHA to build.
    String getShortGitCommit()

    // Git commit author.
    String getGitCommitAuthor()

    // Git commit message.
    String getGitCommitMessage()

    // Git commit raw message.
    String getGitCommitRawMessage()

    // Git commit time in RFC 3399.
    String getGitCommitTime()

    // Gets last successful commit SHA built on Jenkins on a specific pipeline
    String getLastSuccessfulCommit()

    // Gets a string array of committed files since the last successful commit
    String[] getCommittedFiles()

    // Branch on which to run SonarQube analysis.
    String getSonarQubeBranch()

    // Edition of the SonarQube server
    String getSonarQubeEdition()

    // Nexus repository to store SonarQube reports
    String getSonarQubeNexusRepository()

    // set branch on which to run SonarQube analysis.
    void setSonarQubeBranch(String sonarQubeBranch)

    // snyk behaviour configuration in case it reports vulnerabilities
    boolean getFailOnSnykScanVulnerabilities()

    // Whether the pipeline run has been triggered by the orchestration pipeline
    boolean getTriggeredByOrchestrationPipeline()

    String getIssueId()

    // Number of environments to allow.
    int getEnvironmentLimit()

    // OpenShift host - value taken from OPENSHIFT_API_URL.
    String getOpenshiftHost()

    // ODS Jenkins shared library version, taken from reference in Jenkinsfile.
    String getOdsSharedLibVersion()

    // Bitbucket URL - value taken from BITBUCKET_URL.
    String getBitbucketUrl()

    // Bitbucket host - alias for getBitbucketUrl.
    String getBitbucketHost()

    // Bitbucket host without scheme/protocol.
    String getBitbucketHostWithoutScheme()

    // Timeout for the OpenShift build of the container image in minutes.
    Integer getOpenshiftBuildTimeout()

    // Timeout for the OpenShift rollout of the pod in minutes.
    Integer getOpenshiftRolloutTimeout()

    // The docker directory to use when building the image in openshift
    String getDockerDir()

    // get any build artifact URIs there were created
    Map<String, Object> getBuildArtifactURIs()

    // adds a build artifact URI to the context for later retrieval,
    // e.g. in case a stage fails - the failed stage name - with key failedStage
    void addArtifactURI (String key, value)

    // add a build to the artifact urls (key buildConfig name)
    void addBuildToArtifactURIs (String buildConfigName, Map <String, String> buildInformation)

    // add a deployment to the artifact urls (key deploymentConfig name)
    void addDeploymentToArtifactURIs (String deploymentConfigName, Map deploymentInformation)

    // get extension image labels
    Map<String, String> getExtensionImageLabels ()

    // set and add image labels
    void setExtensionImageLabels (Map <String, String> extensionLabels)

    // get the application domain
    String getOpenshiftApplicationDomain ()

    // set the rollout retry
    void setOpenshiftRolloutTimeoutRetries (int retries)

    // get the rollout retry
    int getOpenshiftRolloutTimeoutRetries ()

    // set the build retry
    void setOpenshiftBuildTimeoutRetries (int retries)

    // get the build retry
    int getOpenshiftBuildTimeoutRetries ()

    // get commit the working tree
    boolean getCommitGitWorkingTree ()

    // get the internal cluster registry address
    String getClusterRegistryAddress ()

}
