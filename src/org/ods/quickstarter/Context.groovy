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
        config.bitbucketHost
    }

    @NonCPS
    String getNexusUrl() {
        config.nexusUrl
    }

    @NonCPS
    String getNexusHost() {
        config.nexusHost
    }

    @NonCPS
    String getNexusUsername() {
        config.nexusUsername
    }

    @NonCPS
    String getNexusPassword() {
        config.nexusPassword
    }

}
