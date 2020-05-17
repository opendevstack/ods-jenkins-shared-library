package org.ods.component

@SuppressWarnings('MethodCount')
interface IContext {

    def assemble()

    // Get debug mode
    boolean getDebug()

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

    // Nexus host (with scheme).
    String getNexusHost()

    // Nexus username.
    String getNexusUsername()

    // Nexus password.
    String getNexusPassword()

    // Nexus host (with scheme), including username and password as BasicAuth.
    String getNexusHostWithBasicAuth()

    // Define which branches are deployed to which environments.
    Map<String, String> getBranchToEnvironmentMapping()

    // Define which environments are cloned from which source environments.
    String getAutoCloneEnvironmentsFromSourceMapping()

    // The environment which was chosen as the clone source.
    String getCloneSourceEnv()

    // Set the environment to clone
    void setCloneSourceEnv( String cloneSourceEnv)

    // The branch in which the clone-project.sh script is used
    String getCloneProjectScriptBranch()

    Map<String, String> getCloneProjectScriptUrls()

    // The environment which was chosen as the deployment target, e.g. "dev".
    String getEnvironment()

    // Set environment
    void setEnvironment(String environment)

    // Target project, based on the environment. E.g. "foo-dev".
    String getTargetProject()

    // Group ID, defaults to: org.opendevstack.<projectID>.
    String getGroupId()

    // Project ID, e.g. "foo".
    String getProjectId()

    // Component ID, e.g. "be-auth-service".
    String getComponentId()

    // Git URL of repository
    String getGitUrl()

    // Git branch for which the build runs.
    String getGitBranch()

    // Git commit SHA to build.
    String getGitCommit()

    // Git commit author.
    String getGitCommitAuthor()

    // Git commit message.
    String getGitCommitMessage()

    // Git commit time in RFC 3399.
    String getGitCommitTime()

    // Gets last successful commit SHA built on Jenkins on a specific pipeline
    String getLastSuccessfulCommit()

    // Gets a string array of committed files since the last successful commit
    String[] getCommittedFiles()

    // Branch on which to run SonarQube analysis.
    String getSonarQubeBranch()

    // set branch on which to run SonarQube analysis.
    void setSonarQubeBranch(String sonarQubeBranch)

    // snyk behaviour configuration in case it reports vulnerabilities
    boolean getFailOnSnykScanVulnerabilities()

    // Number of environments to allow.
    int getEnvironmentLimit()

    // OpenShift host - value taken from OPENSHIFT_API_URL.
    String getOpenshiftHost()

    // ODS Jenkins shared library version, taken from reference in Jenkinsfile.
    String getOdsSharedLibVersion()

    // BitBucket URL - value taken from BITBUCKET_URL.
    String getBitbucketUrl()

    // BitBucket host - value derived from getBitbucketUrl.
    String getBitbucketHost()

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

    // set the application domain
    void setOpenshiftApplicationDomain (String domain)

    // get the application domain
    String getOpenshiftApplicationDomain ()

}
