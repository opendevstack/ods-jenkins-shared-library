package org.ods.quickstarter

import com.cloudbees.groovy.cps.NonCPS

// import org.ods.util.Logger
// import org.ods.util.IPipelineSteps
// import org.ods.util.PipelineSteps

// import org.ods.services.OpenShiftService

class Context implements IContext {

    private final Map config
    // private final Logger logger
    // private final IPipelineSteps steps
    // private final def script
    // private OpenShiftService openShiftService

    // Context(Map config, Logger logger, def script) {
    Context(Map config) {
        this.config = config
        // this.logger = logger
        // this.steps = new PipelineSteps(script)
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

    // void setAppDomain() {
    //     if (config.projectId) {
    //         logger.startClocked("${config.componentId}-get-oc-app-domain")

    //         this.openShiftService = new OpenShiftService(steps, logger)
    //         config.appDomain = openShiftService.getApplicationDomain("${config.projectId}-cd")

    //         logger.debugClocked("${config.componentId}-get-oc-app-domain")
    //     } else {
    //         logger.debug('Could not get application domain, as no projectId is available')
    //     }
    // }

}
