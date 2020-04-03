package org.ods.quickstarter

interface IContext {

  // Value of JOB_NAME. It is the name of the project of the build.
  String getJobName()

  // Value of BUILD_NUMBER. The current build number, such as "153".
  String getBuildNumber()

  // Value of BUILD_URL. The URL where the results of the build can be found (e.g. http://buildserver/jenkins/job/MyJobName/123/)
  String getBuildUrl()

  // Time of the build, collected when the odsPipeline starts.
  String getBuildTime()

  // Project ID, e.g. "foo".
  String getProjectId()

  // Component ID, e.g. "be-auth-service".
  String getComponentId()

  // Quickstarter ID, e.g. "be-golang-plain".
  String getQuickstarterId()

  String getGitUrlHttp()

  String getOdsImageTag()

  String getOdsGitRef()

  String getOutputDir()

  String getPackageName()

  String getGroup()

  String getCdUserCredentialsId()
}
