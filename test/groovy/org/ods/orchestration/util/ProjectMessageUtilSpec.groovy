package org.ods.orchestration.util

import org.apache.http.client.utils.URIBuilder
import org.ods.orchestration.usecase.JiraUseCase
import org.ods.services.GitService
import org.ods.util.IPipelineSteps
import org.ods.util.Logger
import util.FixtureHelper
import util.SpecHelper

import static util.FixtureHelper.createProjectJiraData
import static util.FixtureHelper.createProjectJiraDataBugs
import static util.FixtureHelper.createProjectJiraDataDocs
import static util.FixtureHelper.createProjectJiraDataIssueTypes

class ProjectMessageUtilSpec extends SpecHelper  {
    GitService git
    JiraUseCase jiraUseCase
    IPipelineSteps steps
    Logger logger
    File metadataFile
    Project project

    def setup() {
        git = Mock(GitService)
        jiraUseCase = Mock(JiraUseCase)
        steps = Spy(util.PipelineSteps)
        steps.env.WORKSPACE = ""
        logger = Mock(Logger)

        metadataFile = new FixtureHelper().getResource("/project-metadata.yml")
        Project.METADATA_FILE_NAME = metadataFile.getAbsolutePath()

        project = createProject().init().load(git, jiraUseCase)
    }

    def createProject(Map<String, Closure> mixins = [:]) {
        Project project = Spy(constructorArgs: [steps, logger])

        if (mixins.containsKey("getGitURLFromPath")) {
            project.getGitURLFromPath(*_) >> { mixins["getGitURLFromPath"]() }
        } else {
            project.getGitURLFromPath(*_) >> { return new URIBuilder("https://github.com/my-org/my-pipeline-repo.git").build() }
        }

        if (mixins.containsKey("loadJiraData")) {
            project.loadJiraData(*_) >> { mixins["loadJiraData"]() }
        } else {
            project.loadJiraData(*_) >> { return createProjectJiraData() }
        }

        if (mixins.containsKey("loadJiraDataBugs")) {
            project.loadJiraDataBugs(*_) >> { mixins["loadJiraDataBugs"]() }
        } else {
            project.loadJiraDataBugs(*_) >> { return createProjectJiraDataBugs() }
        }

        if (mixins.containsKey("loadJiraDataDocs")) {
            project.loadJiraDataTrackingDocs(*_) >> { mixins["loadJiraDataDocs"]() }
        } else {
            project.loadJiraDataTrackingDocs(*_) >> { return createProjectJiraDataDocs() }
        }

        if (mixins.containsKey("loadJiraDataIssueTypes")) {
            project.loadJiraDataIssueTypes(*_) >> { mixins["loadJiraDataIssueTypes"]() }
        } else {
            project.loadJiraDataIssueTypes(*_) >> { return createProjectJiraDataIssueTypes() }
        }

        if (mixins.containsKey("loadJiraData")) {
            project.loadJiraData(*_) >> { mixins["loadJiraData"]() }
        }

        if (mixins.containsKey("getDocumentChapterData")) {
            project.getDocumentChapterData(*_) >> { mixins["getDocumentChapterData"]() }
        }

        return project
    }

    def "generate message for WIP issues"(){
        given:
        def expectedMessage = """
        Pipeline-generated documents are watermarked 'Work in Progress' since the following issues are work in progress:

        Epics: NET-124

        Mitigations: NET-123

        Requirements: NET-125

        Risks: NET-126

        Tech Specs: NET-128

        Tests: NET-140, NET-131, NET-142, NET-130, NET-141, NET-133, NET-144, NET-132, NET-143, NET-135, NET-134, NET-137, NET-136, NET-139, NET-138

        Please note that for a successful Deploy to D, the above-mentioned issues need to be in status Done.
        """.stripIndent().replaceAll("[\\t\\n\\r  ]+"," ").trim()

        when:
        def message = ProjectMessagesUtil.generateWIPIssuesMessage(project).stripIndent().replaceAll("[\\t\\n\\r  ]+"," ").trim()

        then:
        message == expectedMessage
    }

    def "generate message for WIP issues when deploying to dev"(){
        given:
        project.data.buildParams.version = "1.0"

        def expectedMessage = """
        The pipeline failed since the following issues are work in progress (no documents were generated):

        Epics: NET-124

        Mitigations: NET-123

        Requirements: NET-125

        Risks: NET-126

        Tech Specs: NET-128

        Tests: NET-140, NET-131, NET-142, NET-130, NET-141, NET-133, NET-144, NET-132, NET-143, NET-135, NET-134, NET-137, NET-136, NET-139, NET-138

        Please note that for a successful Deploy to D, the above-mentioned issues need to be in status Done.
        """.stripIndent().replaceAll("[\\t\\n\\r  ]+"," ").trim()

        when:
        def message = ProjectMessagesUtil.generateWIPIssuesMessage(project).stripIndent().replaceAll("[\\t\\n\\r  ]+"," ").trim()

        then:
        message == expectedMessage
    }
}
