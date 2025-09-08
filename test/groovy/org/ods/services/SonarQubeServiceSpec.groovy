package org.ods.services

import org.ods.util.Logger
import vars.test_helper.PipelineSpockTestBase

class SonarQubeServiceSpec extends PipelineSpockTestBase {

    def "constructor sets fields"() {
        given:
        def steps = Spy(util.PipelineSteps)
        def logger = Mock(org.ods.util.ILogger)
        def env = "sonar-env"

        when:
        def service = new SonarQubeService(steps, logger, env)

        then:
        service.sonarQubeEnv == env
        service.logger == logger
        service.script == steps
    }

    def "readProperties delegates to script"() {
        given:
        def steps = GroovyMock(util.PipelineSteps)
        steps.readProperties([file: 'sonar-project.properties']) >> [foo: 'bar']
        def logger = Mock(org.ods.util.ILogger)
        def service = new SonarQubeService(steps, logger, "env")
        
        when:
        def result = service.readProperties()
        
        then:
        result == [foo: 'bar']
    }

    def "readTask delegates to script"() {
        given:
        def steps = GroovyMock(util.PipelineSteps)
        steps.readProperties([file: '.scannerwork/report-task.txt']) >> [projectKey: 'key']
        def logger = Mock(org.ods.util.ILogger)
        def service = new SonarQubeService(steps, logger, "env")
        
        when:
        def result = service.readTask()
        
        then:
        result == [projectKey: 'key']
    }

    def "getComputeEngineTaskResult returns status in uppercase"() {
        given:
        def steps = Spy(util.PipelineSteps)
        steps.readJSON(text: _) >> [task: [status: 'successful']]
        def logger = Mock(org.ods.util.ILogger)
        def service = Spy(SonarQubeService, constructorArgs: [steps, logger, "env"])
        service.getComputeEngineTaskJSON(_) >> '{"task":{"status":"successful"}}'

        expect:
        service.getComputeEngineTaskResult("id", "") == "SUCCESSFUL"
    }

    def "getSonarQubeHostUrl returns host url"() {
        given:
        def steps = Spy(util.PipelineSteps)
        def logger = Mock(org.ods.util.ILogger)
        def service = new SonarQubeService(steps, logger, "env")

        expect:
        service.getSonarQubeHostUrl() == "https://sonarqube.example.com"
    }

    def "scan calls script.sh with correct params"() {
        given:
        def steps = GroovyMock(util.PipelineSteps)
        steps.tool('SonarScanner') >> '/opt/sonar-scanner'
        steps.withSonarQubeEnv(_, _) >> { String env, Closure block ->
            block()
        }
        steps.sh(_) >> { Map args ->
            if (args.returnStatus) {
                return 1 // which command fails, so tool() method is used
            }
            return null
        }
        def logger = Mock(org.ods.util.ILogger)
        logger.debugMode >> false
        def service = new SonarQubeService(steps, logger, "env")
        def options = [
            properties: ['sonar.projectKey': 'key', 'sonar.projectName': 'name'],
            gitCommit: "abcdef123456",
            pullRequestInfo: [:],
            sonarQubeEdition: "community",
            exclusions: "test/**",
            privateToken: ""
        ]

        when:
        service.scan(options)

        then:
        1 * steps.sh(label: 'Set Java 17 for SonarQube scan', script: "source use-j17.sh")
        1 * steps.sh(label: 'Run SonarQube scan', script: { it.contains("/opt/sonar-scanner/bin/sonar-scanner") && it.contains("-Dsonar.projectKey=key") && it.contains("-Dsonar.projectName=name") && it.contains("-Dsonar.exclusions=test/**") })
    }
}
