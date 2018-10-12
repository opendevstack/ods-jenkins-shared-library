package org.ods

interface Context {

    def assemble()

    boolean getDebug()

    String getJobName()

    String getBuildNumber()

    String getBuildUrl()

    boolean getResponsible()

    String getImage()

    String getPodLabel()

    Object getPodVolumes()

    boolean getPodAlwaysPullImage()

    String getGitBranch()

    String getCredentialsId()

    String getGitUrl()

    String getShortBranchName()

    String getTagversion()

    boolean getNotifyNotGreen()

    String getNexusHost()

    String getNexusUsername()

    String getNexusPassword()

    String getBranchToEnvironmentMapping()

    String getAutoCloneEnvironmentsFromSourceMapping()

    String getCloneSourceEnv()

    String getEnvironment()

    String getGroupId()

    String getProjectId()

    String getComponentId()

    String getGitCommit()

    String getTargetProject()

    String getSonarQubeBranch()

    String getDependencyCheckBranch()

    int getEnvironmentLimit()

    boolean getAdmins()

    String getOpenshiftHost()

    String getBitbucketHost()

    boolean getEnvironmentCreated()

    int getOpenshiftBuildTimeout()

    def setEnvironmentCreated(boolean created)
}
