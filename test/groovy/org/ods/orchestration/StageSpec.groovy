package org.ods.orchestration

import groovy.util.logging.Slf4j
import org.ods.core.test.LoggerStub
import org.ods.orchestration.util.Project
import org.ods.services.NexusService
import org.ods.util.ILogger
import org.ods.util.IPipelineSteps
import org.ods.util.PipelineSteps
import spock.lang.Specification
import spock.lang.Unroll

@Slf4j
class StageSpec extends Specification {

    private class ProjectForMocks extends Project {
        ProjectForMocks(IPipelineSteps steps, ILogger logger, Map config = [:]) {
            super(steps, logger, config)
        }
        @Override
        String getJiraProjectKey() {
            return "jiraProjectKey"
        }
    }

    @Unroll
    def "uploadTestResultsToNexus test" () {
        given:
        String type = typeAndRepo.type
        Map repo = typeAndRepo.repo

        PipelineSteps steps = Mock(PipelineSteps) {
            getEnv() >> { [
                WORKSPACE: "workspacePath",
                BUILD_NUMBER: "13"
            ]}
        }
        Project project = new ProjectForMocks(steps, new LoggerStub(log))
        project.data = [:]
        project.data.build = [:]
        project.data.buildParams = [:]
        project.data.buildParams.testResultsURLs = [:]

        Stage stage = Spy(new Stage(null, project, null, ''))

        String testReportsUnstashPath = "somewhere"
        String testResultsKey = stage.getTestResultsKey(type, repo)

        String testResultsUrl = "nexusDirectory/nexusSubdirectory/nexusFileName.zip_${testResultsKey}"
        NexusService nexusService = Mock(NexusService) {
            getNexusDirectory(_,_) >> {
                return "nexusDirectory"
            }
            uploadTestsResults(_, _, _, _, _) >> {
                return testResultsUrl
            }
        }

        when:
        stage.uploadTestResultsToNexus( nexusService, steps, repo, type,
            testReportsUnstashPath)

        then:
        project.data.buildParams.testResultsURLs[testResultsKey] != null
        project.data.buildParams.testResultsURLs[testResultsKey] == testResultsUrl

        where:
        typeAndRepo << [
            [ type: Project.TestType.UNIT, repo: [id:"someRepoKey"]],
            [ type: Project.TestType.ACCEPTANCE, repo: [id:"someRepoKey"]],
            [ type: Project.TestType.INSTALLATION, repo: [id:"someRepoKey"]],
            [ type: Project.TestType.INTEGRATION, repo: [id:"someRepoKey"]],
            // [ type: Project.TestType.UNIT, repo: [:]],
            [ type: Project.TestType.ACCEPTANCE, repo: [:]],
            [ type: Project.TestType.INSTALLATION, repo: [:]],
            [ type: Project.TestType.INTEGRATION, repo: [:]],
        ]
    }

    @Unroll
    def "getTestResultsKey test" () {
        given:
        String type = typeAndRepo.type
        Map repo = typeAndRepo.repo
        Stage stage = Spy(new Stage(null, null, null, ''))
        when:
        String result = stage.getTestResultsKey(type, repo)
        then:
        if (typeAndRepo.result != null) {
            typeAndRepo.result == result
        }
        where:
        typeAndRepo << [
            [ type: Project.TestType.UNIT, repo: [id:"someRepoKey"], result:"Unit-someRepoKey" ],
            [ type: Project.TestType.ACCEPTANCE, repo: [id:"someRepoKey"],
              result: Project.TestType.ACCEPTANCE.toLowerCase() ],
            [ type: Project.TestType.INSTALLATION, repo: [id:"someRepoKey"],
              result: Project.TestType.INSTALLATION.toLowerCase() ],
            [ type: Project.TestType.INTEGRATION, repo: [id:"someRepoKey"],
              result: Project.TestType.INTEGRATION.toLowerCase() ],
            // -------------------------------------------------------------------
            // [ type: Project.TestType.UNIT, repo: [:], result:null],
            [ type: Project.TestType.ACCEPTANCE, repo: [:],
              result: Project.TestType.ACCEPTANCE.toLowerCase() ],
            [ type: Project.TestType.INSTALLATION, repo: [:],
              result: Project.TestType.INSTALLATION.toLowerCase() ],
            [ type: Project.TestType.INTEGRATION, repo: [:],
              result: Project.TestType.INTEGRATION.toLowerCase() ],
        ]
    }

    def "getTestResultsKey test error when no repo id" () {
        given:
        String type = Project.TestType.UNIT
        Map repo = [:]
        Stage stage = Spy(new Stage(null, null, null, ''))

        when:
        String result = stage.getTestResultsKey(type, repo)

        then:
        def e = thrown(RuntimeException)
        e.message == "Cannot obtain repo id, needed for Unit tests."
    }

}
