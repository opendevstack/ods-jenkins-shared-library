package org.ods.core.test.usecase

import org.apache.commons.io.FileUtils
import org.ods.core.test.LoggerStub
import org.ods.core.test.service.BitbucketReleaseManagerService
import org.ods.core.test.usecase.levadoc.fixture.LevaDocDataFixture
import org.ods.core.test.usecase.levadoc.fixture.ProjectFixture
import org.ods.orchestration.service.JiraService
import org.ods.orchestration.usecase.JiraUseCase
import org.ods.orchestration.util.MROPipelineUtil
import org.ods.orchestration.util.Project
import org.ods.services.GitService
import org.ods.util.IPipelineSteps

import java.nio.file.Paths

class ProjectFactory {

    private IPipelineSteps steps
    private GitService gitService
    private JiraService jiraService
    private LoggerStub loggerStub

    ProjectFactory(IPipelineSteps steps, GitService gitService, JiraService jiraService, LoggerStub loggerStub) {
        this.steps = steps
        this.gitService = gitService
        this.jiraService = jiraService
        this.loggerStub = loggerStub
    }

    Project getProject(ProjectFixture projectFixture, LevaDocDataFixture dataFixture) {
        Project project
        try {
            Project.METADATA_FILE_NAME = 'metadata.yml'
            steps.env = loadEnvData(projectFixture, dataFixture)

            project = new Project(steps, loggerStub, [:]).init("refs/heads/master")
            dataFixture.buildFixtureData(projectFixture, project)

            // project.data.metadata = [ : ]
            project.data.metadata.id = projectFixture.project
            project.data.buildParams =  project.data.build

            def util = new MROPipelineUtil(project, steps, gitService, loggerStub)
            def jiraUseCase = new JiraUseCase(project, steps, util, jiraService, loggerStub)
            project.load(gitService, jiraUseCase)

            project.data.openshift.targetApiUrl = "https://openshift-sample"
            project.data.jenkinsLog = project.data.build.jenkinsLog

            project.repositories.each { repo -> repo.metadata = loadMetadataFromDataFixture(repo) }

        } catch(RuntimeException e){
            loggerStub.error("setup error:${e.getMessage()}", e)
            throw e
        }
        return project
    }

    private def loadMetadataFromDataFixture(repo) {
        return  [
            id: repo.id,
            name: repo.name,
            description: "myDescription-A",
            supplier: "mySupplier-A",
            version: "myVersion-A",
            references: "myReferences-A"
        ]
    }

    private def loadEnvData(ProjectFixture projectFixture, LevaDocDataFixture dataFixture) {
        File tmpWorkspace = setTemporalWorkspace(projectFixture, dataFixture)
        return [
            BUILD_ID             : "2022-01-22_23-59-59",
            WORKSPACE            : tmpWorkspace.absolutePath,
            RUN_DISPLAY_URL      : "https://jenkins-sample/blabla",
            version              : projectFixture.version,
            configItem           : "Functional-Test",
            RELEASE_PARAM_VERSION: "3.0",
            BUILD_NUMBER         : projectFixture.buildNumber,
            BUILD_URL            : "https://jenkins-sample",
            JOB_NAME             : "ordgp-cd/ordgp-cd-release-master"
        ]
    }

    private File setTemporalWorkspace(ProjectFixture projectFixture, LevaDocDataFixture dataFixture) {
        File tmpWorkspace = new FileTreeBuilder(dataFixture.getTempFolder()).dir("workspace")
        System.setProperty("java.io.tmpdir", tmpWorkspace.absolutePath)
        File workspace = Paths.get("test/resources/workspace/${projectFixture.project}").toFile()

        boolean RECORD = Boolean.parseBoolean(System.properties["testRecordMode"] as String)
        if (RECORD) {
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
