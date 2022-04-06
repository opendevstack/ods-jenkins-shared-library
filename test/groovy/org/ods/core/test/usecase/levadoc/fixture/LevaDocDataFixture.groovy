package org.ods.core.test.usecase.levadoc.fixture

import groovy.util.logging.Slf4j
import org.apache.commons.io.FileUtils
import org.ods.orchestration.usecase.LeVADocumentUseCase
import org.ods.orchestration.util.Project
import org.ods.util.IPipelineSteps

@Slf4j
class LevaDocDataFixture {

    LevaDocDataFixture(){
    }

    LevaDocDataFixture buildFixtureData(ProjectFixture projectFixture, Project project, IPipelineSteps steps, File tmpWorkspace){
        updateProjectFromFixture(projectFixture, project)
        updateStepsEnvFromFixture(projectFixture, steps, tmpWorkspace)

        return this
    }

    private updateProjectFromFixture(ProjectFixture projectFixture, Project project) {
        project.data.buildParams = buildJobParams(projectFixture)
        project.data.git =  buildGitData(projectFixture)
        project.data.openshift = [targetApiUrl:"https://openshift-sample"]

        // project.data.metadata = [ : ]
        project.data.metadata.id = projectFixture.project
        project.data.buildParams =  project.data.build

        project.data.openshift.targetApiUrl = "https://openshift-sample"
    }

    private updateStepsEnvFromFixture(ProjectFixture projectFixture, IPipelineSteps steps, File tmpWorkspace) {
        final String RUN_DISPLAY_URL = "https://jenkins-sample/blabla"
        final String BUILD_URL = "https://jenkinsHost.org/job/" +
            "${projectFixture.project}-cd/job/${projectFixture.project}-releasemanager/${projectFixture.buildNumber}"

        steps.env = [
            BUILD_ID             : "2022-01-22_23-59-59",
            WORKSPACE            : tmpWorkspace.absolutePath,
            RUN_DISPLAY_URL      : RUN_DISPLAY_URL,
            version              : projectFixture.version,
            configItem           : "Functional-Test",
            RELEASE_PARAM_VERSION: "3.0",
            BUILD_NUMBER         : "${projectFixture.buildNumber}",
            BUILD_URL            : BUILD_URL,
            JOB_NAME             : "${projectFixture.project}-cd/${projectFixture.project}-releasemanager"
        ]

    }

    private Map<String, String> buildJobParams(ProjectFixture projectFixture){
        String projectWithBuild = "${projectFixture.project}/${projectFixture.buildNumber}"
        return  [
                targetEnvironment: "dev",
                targetEnvironmentToken: "D",
                version: "${projectFixture.version}",
                configItem: "BI-IT-DEVSTACK",
                changeDescription: "UNDEFINED",
                changeId: "1.0",
                rePromote: "false",
                releaseStatusJiraIssueKey: projectFixture.releaseKey,
                testResultsURLs: buildTestResultsUrls(projectWithBuild),
                jenkinsLog: getJenkinsLogUrl(projectWithBuild)
        ]
    }

    private String getJenkinsLogUrl(String projectWithBuild) {
        "repository/leva-documentation/${projectWithBuild}/jenkins-job-log.zip"
    }

    private Map<String, String> buildTestResultsUrls(String projectWithBuild) {
        return [
                "Unit-backend": "repository/leva-documentation/${projectWithBuild}/unit-backend.zip",
                "Unit-frontend": "repository/leva-documentation/${projectWithBuild}/unit-frontend.zip",
                "Acceptance" : "repository/leva-documentation/${projectWithBuild}/acceptance.zip",
                'Installation' : "repository/leva-documentation/${projectWithBuild}/installation.zip",
                'Integration' : "repository/leva-documentation/${projectWithBuild}/integration.zip",
        ]
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

    void useExpectedComponentDocs(LeVADocumentUseCase useCase, ProjectFixture projectFixture) {
        useCase.project.repositories.each { repo ->
            if (!repo.data.documents) {
                repo.data.documents = [:]
            }
            if (DocTypeProjectFixtureWithComponent.notIsReleaseModule(repo)) {
                // see @org.ods.orchestration.usecase.DocGenUseCase#createOverallDocument -> unstashFilesIntoPath
                repo.data.documents[projectFixture.docType] = "/blablabla"
            }
        }
    }

}
