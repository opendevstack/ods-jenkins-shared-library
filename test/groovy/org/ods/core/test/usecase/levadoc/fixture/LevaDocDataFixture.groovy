package org.ods.core.test.usecase.levadoc.fixture

import groovy.util.logging.Slf4j
import org.apache.commons.io.FileUtils
import org.ods.orchestration.usecase.LeVADocumentUseCase
import org.ods.orchestration.util.GitTag
import org.ods.orchestration.util.Project
import org.ods.util.IPipelineSteps

import static groovy.json.JsonOutput.prettyPrint
import static groovy.json.JsonOutput.toJson

@Slf4j
class LevaDocDataFixture {

    static final String JENKINS_URLS_BASE = "https://jenkinsHost.org/"
    static final String JENKINS_URL_RUN_DISPLAY = JENKINS_URLS_BASE + "display"
    static final String JENKINS_URL_JOB_BUILD = JENKINS_URLS_BASE + "buildJob"

    LevaDocDataFixture(){
    }

    void configStepsEnvFromFixture(ProjectFixture projectFixture, IPipelineSteps steps, File tmpWorkspace) {
        steps.env = [
            BUILD_ID             : "${projectFixture.stepsEnv.jenkinsBuildId}" ?: "2022-01-22_23-59-59",
            WORKSPACE            : tmpWorkspace.absolutePath,
            RUN_DISPLAY_URL      : "${projectFixture.stepsEnv.jenkinsRunDisplayUrl}" ?:JENKINS_URL_RUN_DISPLAY,
            version              : "${projectFixture.version}",
            BUILD_NUMBER         : "${projectFixture.buildNumber}",
            BUILD_URL            : "${projectFixture.stepsEnv.jenkinsBuildJobUrl}" ?: JENKINS_URL_JOB_BUILD,
            JOB_NAME             : "${projectFixture.getJobName()}",
            rePromote            : "${projectFixture.stepsEnv.rePromote}",
            changeId             : "${projectFixture.stepsEnv.changeId}",
            configItem           : "${projectFixture.stepsEnv.configItem}",
            changeDescription    : "${projectFixture.stepsEnv.changeDescription}",
            releaseStatusJiraIssueKey : "${projectFixture.stepsEnv.releaseStatusJiraIssueKey}",
        ]
    }

    void reconfigureStepsEnvFromProject(Project project, IPipelineSteps steps) {
        List<String> buildEnv = project.getBuildEnvironment(steps, true)
        log.debug("steps.env:")
        log.debug(prettyPrint(toJson(steps.env)))
        log.debug("buildEnv:")
        log.debug(prettyPrint(toJson(buildEnv)))
        steps.env = steps.env << buildEnv
        log.debug("steps.env:")
        log.debug(prettyPrint(toJson(steps.env)))
    }
    void setupProjectFromFixture(ProjectFixture projectFixture, Project project) {
        Map extraBuildParams = [
            testResultsURLs: projectFixture.getTestResultsUrls(),
            jenkinsLog: projectFixture.getJenkinsLogUrl()
        ]
        project.data.buildParams << extraBuildParams
        project.data.git =  buildGitData(projectFixture)

        String newMetadataId = "${projectFixture.project}"
        log.warn("Changing project.data.metadata.id: " +
            "${project.data.metadata?.id} -> ${newMetadataId}")
        project.data.metadata.id = newMetadataId
    }

    void fixOpenshiftData(ProjectFixture projectFixture, Project project) {
        project.data.openshift = projectFixture.getOpenshiftData()
    }

    private Map<String, String> buildGitData(ProjectFixture projectFixture) {
        String bitbucketUrl = System.properties["bitbucket.url"]?: "https://bitbucket-dev.biscrum.com"
        return  [
                commit: "1e84b5100e09d9b6c5ea1b6c2ccee8957391beec",
                baseTag: "ods-generated-v3.0-3.0-0b11-D",
                targetTag: "ods-generated-v3.0-3.0-0b11-D",
                author: "ODS Jenkins Shared Library System User (undefined)",
                message: "Swingin' The Bottle",
                time: "2021-04-20T14:58:31.042152",
                releaseManagerRepo: "${projectFixture.releaseManagerRepo}",
                releaseManagerBranch: "${projectFixture.releaseManagerBranch}"
        ]
    }

}
