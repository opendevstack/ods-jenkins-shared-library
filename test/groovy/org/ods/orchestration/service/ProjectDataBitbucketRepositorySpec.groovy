package org.ods.orchestration.service

import org.ods.orchestration.service.leva.ProjectDataBitbucketRepository
import org.ods.orchestration.util.Project
import org.ods.util.IPipelineSteps
import org.ods.util.Logger

import java.nio.file.Paths
import org.ods.util.PipelineSteps
import util.SpecHelper

import static util.FixtureHelper.createProject

class ProjectDataBitbucketRepositorySpec extends SpecHelper {

    Project project
    IPipelineSteps steps
    Logger logger

    def setup() {
        project = Spy(createProject())
        project.buildParams.targetEnvironment = "dev"
        project.buildParams.targetEnvironmentToken = "D"
        project.buildParams.version = "WIP"

        steps = Spy(PipelineSteps)
        logger = Mock(Logger)
        project.getOpenShiftApiUrl() >> 'https://api.dev-openshift.com'
    }

    def "save content"() {
        given:
        def steps = Spy(util.PipelineSteps)
        def levaPath = Paths.get(steps.env.WORKSPACE, ProjectDataBitbucketRepository.BASE_DIR)
        levaPath.toFile().mkdirs()
        def repo = Spy(ProjectDataBitbucketRepository, constructorArgs: [
            steps
        ])
        def jiraData = project.data.jira
        def version = "1.0"

        when:
        repo.save(jiraData, version)

        then:
        def file = readResource("${ProjectDataBitbucketRepository.BASE_DIR}/${version}.json")
        println(file)
    }

    def "load content"() {
        given:

        def levaPath = Paths.get(steps.env.WORKSPACE, LeVADocumentChaptersFileService.DOCUMENT_CHAPTERS_BASE_DIR)
        levaPath.toFile().mkdirs()

    }

    protected String readResource(String name) {
        given:
        def steps = Spy(util.PipelineSteps)
        def path = Paths.get(steps.env.WORKSPACE, ProjectDataBitbucketRepository.BASE_DIR)
        levaPath.toFile().mkdirs()
        def repo = Spy(ProjectDataBitbucketRepository, constructorArgs: [
            steps
        ])
        def jiraData = project.data.jira
        def version = "1.0"

        def file = Paths.get(path.toString(), "${version}.json")
        file << """
        {
        "productReleases": {
            "${version}": {
              "version": "${version}",
              "predecessors": [
                "0.1"
              ]
            }
          },
        "requirements" : {
            "REQ-1": { "key": "REQ-1", "description":"Not modified", "version":"0.1", 
              "risks": [  "RISK-1" ], "tests": ["TST-1"],"mitigations":["MIT-1","MIT-2"],"techSpec":[] },
            "REQ-2": { "key": "REQ-2", "succeeded by":["REQ-4"], "version":"0.1"},
            "REQ-3": { "key": "REQ-3", "canceled in": ["1.0"], "description":"To be canceled", "version":"0.1", 
              "risks": [ "RISK-3" ], "tests": ["TST-3"],"mitigations":[],"techSpec":[] },
            "REQ-4": { "key": "REQ-4", "description":"Changes 2", "version":"1.0", 
              "succeeds":[{"key":"REQ-2", "version":"0.1"}]
              "risks": [ "RISK-2" ], "tests": ["TST-2","TST-4"],"mitigations":["MIT-3"],"techSpec":["TSP-1"] }
        },
        "techSpecs" : {
            "TSP-1": { "key": "TSP-1", "description":"Not modified", "version":"0.1", 
              "risks": [], "tests": ["TST-4"],"mitigations":[],"requirements":["REQ-4"] },
        },
        "tests" : {
            
        }
        }
        """

    }
}
