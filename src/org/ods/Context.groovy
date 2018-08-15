package org.ods

interface Context {

    def assemble()

    boolean getVerbose()

    String getJobName()

    String getBuildNumber()

    String getBuildUrl()

    boolean getResponsible()

    String getImage()

    String getPodLabel()

    Object getPodVolumes()

    boolean getPodAlwaysPullImage()

    boolean getUpdateBranch()

    String getGitBranch()

    String getCredentialsId()

    boolean getBranchUpdated()

    String getGitUrl()

    String getShortBranchName()

    String getTagversion()

    boolean getNotifyNotGreen()

    String getNexusHost()

    String getNexusUsername()

    String getNexusPassword()

    String getTestProjectBranch()

    String getEnvironment()

    String getGroupId()

    String getProjectId()

    String getComponentId()

    String getGitCommit()

    String getTargetProject()

    int getEnvironmentLimit()

    boolean getAdmins()

    String getOpenshiftHost()

    String getBitbucketHost()

    boolean getEnvironmentCreated()

    int getOpenshiftBuildTimeout()

    def setBranchUpdated(boolean branchUpdated)

    def setEnvironmentCreated(boolean created)
}
