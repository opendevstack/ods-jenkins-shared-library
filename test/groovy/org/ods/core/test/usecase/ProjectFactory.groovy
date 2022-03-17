package org.ods.core.test.usecase

import org.ods.core.test.LoggerStub
import org.ods.core.test.usecase.levadoc.fixture.LevaDocDataFixture
import org.ods.core.test.usecase.levadoc.fixture.ProjectFixture
import org.ods.orchestration.service.JiraService
import org.ods.orchestration.usecase.JiraUseCase
import org.ods.orchestration.util.MROPipelineUtil
import org.ods.orchestration.util.Project
import org.ods.services.GitService
import org.ods.util.IPipelineSteps

class ProjectFactory {

    private IPipelineSteps steps
    private GitService gitService
    private JiraService jiraService
    private LoggerStub loggerStub

    private Project project

    ProjectFactory(IPipelineSteps steps, GitService gitService, JiraService jiraService, LoggerStub loggerStub) {
        this.steps = steps
        this.gitService = gitService
        this.jiraService = jiraService
        this.loggerStub = loggerStub
    }

    public Project getProject() {
        return project
    }

    ProjectFactory loadProject(ProjectFixture projectFixture, LevaDocDataFixture dataFixture) {
        try {
            project = buildProject(projectFixture, dataFixture)
            def util = new MROPipelineUtil(project, steps, gitService, loggerStub)
            def jiraUseCase = new JiraUseCase(project, steps, util, jiraService, loggerStub)
            project.load(gitService, jiraUseCase)
            project.data.openshift.targetApiUrl = "https://openshift-sample"
            project.data.build.testResultsURLs = [:]
            project.data.build.testResultsURLs = dataFixture.getTestResultsUrls()
            project.repositories.each { repo -> repo.metadata = dataFixture.loadMetadata(repo) }
        } catch(RuntimeException e){
            loggerStub.error("setup error:${e.getMessage()}", e)
            throw e
        }
        return this
    }

    private Project buildProject(ProjectFixture projectFixture, LevaDocDataFixture dataFixture) {
        Project.METADATA_FILE_NAME = 'metadata.yml'
        steps.env = dataFixture.loadEnvData(projectFixture)
        def project = new Project(steps, loggerStub, [:]).init("refs/heads/master")
        project.data.metadata.id = projectFixture.project
        project.data.buildParams =  dataFixture.buildParams(projectFixture)
        project.data.git = dataFixture.buildGitData(projectFixture)
        return project
    }

}
