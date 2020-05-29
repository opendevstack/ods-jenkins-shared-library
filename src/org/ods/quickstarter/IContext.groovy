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

    String getOdsNamespace()

    String getOdsImageTag()

    String getOdsGitRef()

    String getTargetDir()

    String getPackageName()

    String getGroup()

    String getCdUserCredentialsId()

    // BitBucket URL - value taken from BITBUCKET_URL.
    String getBitbucketUrl()

    // BitBucket host - value derived from getBitbucketUrl.
    String getBitbucketHost()

    String getNexusHost()

    String getNexusUsername()

    String getNexusPassword()

}
