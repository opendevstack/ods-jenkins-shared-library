package org.ods.core.test.usecase.levadoc.fixture

import groovy.util.logging.Slf4j
import org.apache.commons.io.FileUtils
import org.ods.orchestration.usecase.LeVADocumentUseCase
import org.ods.orchestration.util.Project

@Slf4j
class LevaDocDataFixture {

    private final File tempFolder

    LevaDocDataFixture(File tempFolder){
        this.tempFolder = tempFolder
    }

    File getTempFolder() {
        return tempFolder
    }

    void buildFixtureData(ProjectFixture projectFixture, Project project){
        project.data.build = buildJobParams(projectFixture)
        project.data.git =  buildGitData(projectFixture)
        project.data.openshift = [targetApiUrl:"https://openshift-sample"]
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
                runDisplayUrl : "",
                releaseParamVersion : "3.0",
                buildId : "2022-01-22_23-59-59",
                buildURL : "https://jenkins-sample",
                jobName : "${projectFixture.project}-cd/${projectFixture.project}-releasemanager",
                testResultsURLs: buildTestResultsUrls(projectWithBuild),
                jenkinLog: getJenkinsLogUrl(projectWithBuild)
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

    private String copyPdfToTemp(ProjectFixture projectFixture, Map data) {
        def destPath = "${tempFolder}/reports/${projectFixture.component}"
        new File(destPath).mkdirs()
        File expected = testValidator.expectedDoc(projectFixture, data.build.buildId as String)
        FileUtils.copyFile(expected, new File("${destPath}/${expected.name}"))
        return expected.name.replaceFirst("pdf", "zip")
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

    /*
    void updateExpectedComponentDocs(ProjectData projectData, Map data, ProjectFixture projectFixture) {
        projectData.repositories.each {repo ->
            projectFixture.component = repo.id
            repo.data.documents = (repo.data.documents)?: [:]

            // see @DocGenUseCase#createOverallDocument -> unstashFilesIntoPath
            repo.data.documents[projectFixture.docType] =  copyPdfToTemp(projectFixture, data)
        }
        projectFixture.component = null
    }
    */
}
