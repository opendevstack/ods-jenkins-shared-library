package org.ods

interface Context {

    def assemble()

    boolean getDebug()

    String getJobName()

    String getBuildNumber()

    String getBuildUrl()

    String getBuildTime()

    String getImage()

    String getPodLabel()

    Object getPodContainers()

    Object getPodVolumes()

    boolean getPodAlwaysPullImage()

    String getPodServiceAccount()

    String getGitBranch()

    String getCredentialsId()

    String getGitUrl()

    String getTagversion()

    boolean getNotifyNotGreen()

    String getNexusHost()

    String getNexusUsername()

    String getNexusPassword()

    String getNexusHostWithBasicAuth()

    String getBranchToEnvironmentMapping()

    String getAutoCloneEnvironmentsFromSourceMapping()

    String getCloneSourceEnv()

    String getEnvironment()

    String getGroupId()

    String getProjectId()

    String getComponentId()

    String getGitCommit()

    String getGitCommitAuthor()

    String getGitCommitMessage()

    String getGitCommitTime()

    String getTargetProject()

    String getSonarQubeBranch()

    String getDependencyCheckBranch()

    int getEnvironmentLimit()

    boolean getAdmins()

    String getOpenshiftHost()

    String getOdsSharedLibVersion()

    String getBitbucketHost()

    boolean getEnvironmentCreated()

    int getOpenshiftBuildTimeout()

    def setEnvironmentCreated(boolean created)

    boolean getCiSkip()

}
