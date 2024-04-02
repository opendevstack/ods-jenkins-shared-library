package org.ods.quickstarter

import com.cloudbees.groovy.cps.NonCPS

class Context implements IContext {

    private final Map config

    Context(Map config) {
        this.config = config
    }

    @NonCPS
    String getJobName() {
        config.jobName
    }

    @NonCPS
    String getBuildNumber() {
        config.buildNumber
    }

    @NonCPS
    String getBuildUrl() {
        config.buildUrl
    }

    @NonCPS
    String getBuildTime() {
        config.buildTime
    }

    @NonCPS
    String getDockerRegistry() {
        config.dockerRegistry
    }

    @NonCPS
    String getProjectId() {
        config.projectId
    }

    @NonCPS
    String getComponentId() {
        config.componentId
    }

    @NonCPS
    String getSourceDir() {
        config.sourceDir
    }

    @NonCPS
    String getGitUrlHttp() {
        config.gitUrlHttp
    }

    @NonCPS
    String getOdsNamespace() {
        config.odsNamespace
    }

    @NonCPS
    String getOdsImageTag() {
        config.odsImageTag
    }

    @NonCPS
    String getOdsGitRef() {
        config.odsGitRef
    }

    @NonCPS
    String getAgentImageTag() {
        config.agentImageTag
    }

    @NonCPS
    String getSharedLibraryRef() {
        config.sharedLibraryRef
    }

    @NonCPS
    String getTargetDir() {
        config.targetDir
    }

    @NonCPS
    String getPackageName() {
        config.packageName
    }

    @NonCPS
    String getGroup() {
        config.group
    }

    @NonCPS
    String getCdUserCredentialsId() {
        config.cdUserCredentialsId
    }

    @NonCPS
    String getBitbucketUrl() {
        config.bitbucketUrl
    }

    @NonCPS
    String getBitbucketHost() {
        getBitbucketUrl()
    }

    @NonCPS
    String getBitbucketHostWithoutScheme() {
        getBitbucketUrl().minus(~/^https?:\/\//)
    }

    @NonCPS
    String getNexusUrl() {
        config.nexusUrl
    }

    @NonCPS
    String getNexusHost() {
        getNexusUrl()
    }

    @NonCPS
    String getNexusHostWithoutScheme() {
        getNexusUrl().minus(~/^https?:\/\//)
    }

    @NonCPS
    String getNexusUsername() {
        config.nexusUsername
    }

    @NonCPS
    String getNexusPassword() {
        config.nexusPassword
    }

    @NonCPS
    String getGitBranch() {
        config.odsGitRef
    }

    @NonCPS
    String getAppDomain() {
        config.appDomain
    }

}
