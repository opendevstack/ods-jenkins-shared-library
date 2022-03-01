package org.ods.core.test.usecase.levadoc.fixture

import groovy.util.logging.Slf4j
import org.apache.commons.io.FileUtils
import org.ods.core.test.usecase.RepoDataBuilder
import org.ods.core.test.workspace.TestsReports
import org.ods.orchestration.usecase.JUnitTestReportsUseCase
import org.ods.orchestration.usecase.LeVADocumentUseCase
import org.ods.orchestration.util.Project
import org.ods.util.IPipelineSteps

@Slf4j
class LevaDocDataFixture {

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

    def buildGitData() {
        return  [
            commit: "1e84b5100e09d9b6c5ea1b6c2ccee8957391beec",
            releaseManagerRepo: "ofi2004-release",
            releaseManagerBranch: "refs/tags/CHG0066328",
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
        File tmpWorkspace = setTemporalWorkspace(projectFixture.project)
        return  [
            BUILD_ID:"2022-01-22_23-59-59",
            WORKSPACE: tmpWorkspace.absolutePath,
            RUN_DISPLAY_URL:"https://jenkins-sample/blabla",
            version: projectFixture.version,
            configItem: "Functional-Test",
            RELEASE_PARAM_VERSION: "3.0",
            BUILD_NUMBER: "666",
            BUILD_URL: "https://jenkins-sample",
            JOB_NAME: "ofi2004-cd/ofi2004-cd-release-master"
        ]
    }

    Map getInputParamsModule(ProjectFixture projectFixture, LeVADocumentUseCase useCase) {
        Map input = RepoDataBuilder.getRepoForComponent(projectFixture.component)
        input.data.tests << [unit: testReports(useCase.project, useCase.steps).getResults(projectFixture.component, "unit")]
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

    Map getAllResults(LeVADocumentUseCase useCase) {
        return testReports(useCase.project, useCase.steps).getAllResults(useCase.project.repositories)
    }

    private TestsReports testReports(Project project, IPipelineSteps steps) {
        def junitReportsUseCase = new JUnitTestReportsUseCase(project, steps)
        return new TestsReports(steps, junitReportsUseCase)
    }

    private File setTemporalWorkspace(String project) {
        File tmpWorkspace = new FileTreeBuilder(tempFolder).dir("workspace")
        System.setProperty("java.io.tmpdir", tmpWorkspace.absolutePath)
        FileUtils.copyDirectory(new File("test/resources/workspace/${project}"), tmpWorkspace)
        return tmpWorkspace
    }
}
