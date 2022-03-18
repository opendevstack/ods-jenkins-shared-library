package org.ods.core.test.usecase.levadoc.fixture

import groovy.util.logging.Slf4j
import org.apache.commons.io.FileUtils
import org.ods.core.test.service.BitbucketReleaseManagerService
import org.ods.core.test.usecase.RepoDataBuilder
import org.ods.orchestration.usecase.LeVADocumentUseCase

import java.nio.file.Paths

@Slf4j
class LevaDocDataFixture {

    private static final boolean RECORD = Boolean.parseBoolean(System.properties["testRecordMode"] as String)

    private final File tempFolder

    LevaDocDataFixture(File tempFolder){
        this.tempFolder = tempFolder
    }

    Map buildParams(ProjectFixture projectFixture){
        return [
            changeDescription: "changeDescription",
            changeId: "changeId",
            configItem:  "BI-IT-DEVSTACK",
            releaseStatusJiraIssueKey: projectFixture.releaseKey,
            targetEnvironment:  "dev",
            targetEnvironmentToken: "D",
            version: projectFixture.version,
            rePromote: false,
        ]
    }

    def buildGitData(ProjectFixture projectFixture) {
        return  [
            commit: "1e84b5100e09d9b6c5ea1b6c2ccee8957391beec",
            url: "https://bitbucket-dev.biscrum.com/scm/ordgp/ordgp-releasemanager.git",
            releaseManagerRepo: projectFixture.releaseManagerRepo,
            releaseManagerBranch: "refs/heads/master",
            baseTag: "ods-generated-v3.0-3.0-0b11-D",
            targetTag: "ods-generated-v3.0-3.0-0b11-D",
            author: "s2o",
            message: "Swingin' The Bottle",
            time: "2021-04-20T14:58:31.042152",
        ]
    }

    def loadMetadata(repo) {
        return  [
            id: repo.id,
            name: repo.name,
            description: "myDescription-A",
            supplier: "mySupplier-A",
            version: "myVersion-A",
            references: "myReferences-A"
        ]
    }

    def loadEnvData(ProjectFixture projectFixture){
        File tmpWorkspace = setTemporalWorkspace(projectFixture)
        return  [
            BUILD_ID:"2022-01-22_23-59-59",
            WORKSPACE: tmpWorkspace.absolutePath,
            RUN_DISPLAY_URL:"https://jenkins-sample/blabla",
            version: projectFixture.version,
            configItem: "Functional-Test",
            RELEASE_PARAM_VERSION: "3.0",
            BUILD_NUMBER: "666",
            BUILD_URL: "https://jenkins-sample",
            JOB_NAME: "ordgp-cd/ordgp-cd-release-master"
        ]
    }

    Map<String, String> getTestResultsUrls(ProjectFixture projectFixture) {
        return [
            "Unit": "https://nexus-odsalpha.inh-odsapps.eu.boehringer.com/repository/leva-documentation/${projectFixture.project}/${projectFixture.buildNumber}/unit-backend.zip",
            "Acceptance" : "https://nexus-odsalpha.inh-odsapps.eu.boehringer.com/repository/leva-documentation/${projectFixture.project}/${projectFixture.buildNumber}/acceptance.zip",
            'Installation' : "https://nexus-odsalpha.inh-odsapps.eu.boehringer.com/repository/leva-documentation/${projectFixture.project}/${projectFixture.buildNumber}/installation.zip",
            'Integration' : "https://nexus-odsalpha.inh-odsapps.eu.boehringer.com/repository/leva-documentation/${projectFixture.project}/${projectFixture.buildNumber}/integration.zip",
        ]
    }

    String getJenkinsLogUrl(ProjectFixture projectFixture) {
        "https://nexus-odsalpha.inh-odsapps.eu.boehringer.com/repository/leva-documentation/${projectFixture.project}/${projectFixture.buildNumber}/jenkins-job-log.zip"
    }

    Map getInputParamsModule(ProjectFixture projectFixture, LeVADocumentUseCase useCase) {
        Map input = RepoDataBuilder.getRepoForComponent(projectFixture.component)
        return input
    }

    void useExpectedComponentDocs(LeVADocumentUseCase useCase, ProjectFixture projectFixture) {
        useCase.project.repositories.each {repo ->
            if (!repo.data.documents){
                repo.data.documents = [:]
            }
            if (DocTypeProjectFixtureWithComponent.notIsReleaseModule(repo)){
                // see @org.ods.orchestration.usecase.DocGenUseCase#createOverallDocument -> unstashFilesIntoPath
                repo.data.documents[projectFixture.docType] =  "/blablabla"
            }
        }
    }

    private File setTemporalWorkspace(ProjectFixture projectFixture) {
        File tmpWorkspace = new FileTreeBuilder(tempFolder).dir("workspace")
        System.setProperty("java.io.tmpdir", tmpWorkspace.absolutePath)
        File workspace =  Paths.get("test/resources/workspace/${projectFixture.project}").toFile()
        if (RECORD){
            workspace.mkdirs()
            downloadReleaseManagerRepo(projectFixture, workspace)
        }
         FileUtils.copyDirectory(workspace, tmpWorkspace)
        return tmpWorkspace
    }

    private downloadReleaseManagerRepo(ProjectFixture projectFixture, File tempFolder) {
        new BitbucketReleaseManagerService().downloadRepo(
            projectFixture.project,
            projectFixture.releaseManagerRepo,
            projectFixture.releaseManagerBranch,
            tempFolder.absolutePath)
    }

}
