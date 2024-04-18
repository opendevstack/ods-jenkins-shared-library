package org.ods.quickstarter

interface IContext {

    // Value of JOB_NAME. It is the name of the project of the build.
    String getJobName()

    // Value of BUILD_NUMBER. The current build number, such as "153".
    String getBuildNumber()

    // Value of BUILD_URL. The URL where the results of the build can be found.
    // E.g. http://buildserver/jenkins/job/MyJobName/123/
    String getBuildUrl()

    // Time of the build, collected when the odsPipeline starts.
    String getBuildTime()

    // Project ID, e.g. "foo".
    String getProjectId()

    // Component ID, e.g. "be-auth-service".
    String getComponentId()

    // Source directory, e.g. "be-golang-plain".
    String getSourceDir()

    String getGitUrlHttp()

    String getDockerRegistry()

    String getOdsNamespace()

    String getOdsImageTag()

    String getOdsGitRef()

    String getAgentImageTag()

    String getSharedLibraryRef()

    String getTargetDir()

    String getPackageName()

    String getGroup()

    String getCdUserCredentialsId()

    // Bitbucket URL - value taken from BITBUCKET_URL.
    String getBitbucketUrl()

    // Bitbucket host - alias for getBitbucketUrl.
    String getBitbucketHost()

    // Bitbucket host without scheme/protocol.
    String getBitbucketHostWithoutScheme()

    // Nexus URL - value taken from NEXUS_URL.
    String getNexusUrl()

    // Nexus host - alias for getNexusUrl.
    String getNexusHost()

    // Nexus host without scheme/protocol.
    String getNexusHostWithoutScheme()

    String getNexusUsername()

    String getNexusPassword()

    // alias for odsGitRef
    String getGitBranch()

    // get app domain of the Openshift cluster
    String getAppDomain()

}
