package org.ods

interface Context {

    def assemble()

    // Get debug mode
    boolean getDebug()

    // Get the location of the xmlunit results
    boolean getTestResults()
    
    // Value of JOB_NAME. It is the name of the project of the build.
    String getJobName()

    // Value of BUILD_NUMBER. The current build number, such as "153".
    String getBuildNumber()

    // Value of BUILD_URL. The URL where the results of the build can be found (e.g. http://buildserver/jenkins/job/MyJobName/123/)
    String getBuildUrl()

    // Time of the build, collected when the odsPipeline starts.
    String getBuildTime()

    // Container image to use for the Jenkins agent container.
    // This value is not used when "podContainers" is set.
    String getImage()

    // Pod label, set by default to a random label to avoid caching issues.
    // Set to a stable label if you want to reuse pods across builds.
    String getPodLabel()

    // Custom pod containers to use. By default, only one container is used, and it is
    // configure automatically. If you need to run multiple containers (e.g. app and
    // database), then you can configure the containers via this property.
    Object getPodContainers()

    // Volumes to make available to the pod.
    Object getPodVolumes()

    // Determine whether to always pull the container image before each build run.
    boolean getPodAlwaysPullImage()

    // Serviceaccount to use when running the pod.
    String getPodServiceAccount()

    // Credentials identifier (Credentials are created and named automatically by the OpenShift Jenkins plugin).
    String getCredentialsId()

    // The tagversion is made up of the build number and the first 8 chars of the commit SHA.
    String getTagversion()

    // Whether to send notifications if the build is not successful.
    boolean getNotifyNotGreen()

    // Enable/disable notifications
    void setNotifyNotGreen(boolean notifyNotGreen)

    // Nexus host (with scheme).
    String getNexusHost()

    // Nexus username.
    String getNexusUsername()

    // Nexus password.
    String getNexusPassword()

    // Nexus host (with scheme), including username and password as BasicAuth.
    String getNexusHostWithBasicAuth()

    // Define which branches are deployed to which environments.
    String getBranchToEnvironmentMapping()

    // Define which environments are cloned from which source environments.
    String getAutoCloneEnvironmentsFromSourceMapping()

    // The environment which was chosen as the clone source.
    String getCloneSourceEnv()

    // Set the environment to clone
    void setCloneSourceEnv( String cloneSourceEnv)

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

    // Branch on which to run SonarQube analysis.
    String getSonarQubeBranch()

    // snyk behaviour configuration in case it reports vulnerabilities
    String getFailOnSnykScanVulnerabilities()

    // Branch on which to run dependency checks.
    String getDependencyCheckBranch()

    // Number of environments to allow.
    int getEnvironmentLimit()

    // OpenShift host - value taken from OPENSHIFT_API_URL.
    String getOpenshiftHost()

    // ODS Jenkins shared library version, taken from reference in Jenkinsfile.
    String getOdsSharedLibVersion()

    // BitBucket host - value taken from BITBUCKET_HOST.
    String getBitbucketHost()

    // Timeout for the OpenShift build of the container image.
    int getOpenshiftBuildTimeout()

    // Whether the build should be skipped, based on the Git commit message.
    boolean getCiSkip()

    // Whether CI skip is enabled
    boolean getCiSkipEnabled()

    // Enable/disable CI skip is enabled
    void setCiSkipEnabled(boolean ciSkipEnabled)

    // Whether Bitbucket notification is enabled
    boolean getBitbucketNotificationEnabled()

    // nable/disable Bitbucket notification
    void setBitbucketNotificationEnabled(boolean bitbucketNotificationEnabled)

    // Whether local checkout is enabled
    boolean getLocalCheckoutEnabled()

    // Enable/disable local checkout
    void setLocalCheckoutEnabled(boolean localCheckoutEnabled)

    // Whether display name update is enabled
    boolean getDisplayNameUpdateEnabled()

    // Enable/disable display name update
    void setDisplayNameUpdateEnabled(boolean displayNameUpdateEnabled)
    
    // get any build artifact URIs there were created
    public Map<String, String> getBuildArtifactURIs()
  
    // adds a build artifact URI to the context for later retrieval,
    // e.g. in case a stage fails - the failed stage name - with key failedStage
    public void addArtifactURI (String key, value)
}
