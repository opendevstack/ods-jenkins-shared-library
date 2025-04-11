package org.ods.orchestration.service

import groovy.json.JsonSlurperClassic
import org.ods.orchestration.service.leva.ProjectDataBitbucketRepository
import org.ods.orchestration.util.Project
import java.nio.file.Paths
import org.ods.util.Logger
import org.ods.util.PipelineSteps
import org.ods.util.IPipelineSteps
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
        //def levaPath = Paths.get(steps.env.WORKSPACE, ProjectDataBitbucketRepository.BASE_DIR)
        //levaPath.toFile().mkdirs()
        def repo = Spy(ProjectDataBitbucketRepository, constructorArgs: [
            steps
        ])
        def jiraData = project.data.jira
        def version = '1.0'

        when:
        repo.save(jiraData, version)

        then:
        1 * steps.writeFile(_)

    }

    def "load content"() {
        given:
        def steps = Spy(util.PipelineSteps)
        def repo = Spy(ProjectDataBitbucketRepository, constructorArgs: [
            steps
        ])
        def version = '1.0'
        def textfile = '{"project": "DEMO"}'
        def jsonObject = new JsonSlurperClassic().parseText(textfile)

        when:
        def result = repo.loadFile(version)

        then:
        1 * steps.readFile(file: "${ProjectDataBitbucketRepository.BASE_DIR}/${version}.json") >> textfile
        result == jsonObject


    }
}
