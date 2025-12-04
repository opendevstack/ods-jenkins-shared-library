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

    def "scan calls script.sh with correct params and uses correct token"() {
        given:
        def steps = GroovyMock(util.PipelineSteps)
        steps.tool('SonarScanner') >> '/opt/sonar-scanner'
        steps.withSonarQubeEnv(_, _) >> { String env, Closure block -> block() }
        steps.getSONAR_HOST_URL() >> 'https://sonarqube.example.com'
        steps.getSONAR_AUTH_TOKEN() >> 'public-token'
        steps.sh(_) >> { Map args ->
            if (args.returnStatus) {
                return 1 // which command fails, so tool() method is used
            }
            return null
        }
        def logger = Mock(org.ods.util.ILogger)
        logger.debugMode >> false
        def service = new SonarQubeService(steps, logger, "env")
        def optionsWithPrivateToken = [
            properties: ['sonar.projectKey': 'key', 'sonar.projectName': 'name'],
            gitCommit: "abcdef123456",
            pullRequestInfo: [:],
            sonarQubeEdition: "community",
            exclusions: "test/**",
            privateToken: "my-private-token"
        ]
        def optionsWithoutPrivateToken = [
            properties: ['sonar.projectKey': 'key', 'sonar.projectName': 'name'],
            gitCommit: "abcdef123456",
            pullRequestInfo: [:],
            sonarQubeEdition: "community",
            exclusions: "test/**",
            privateToken: ""
        ]

        when:
        service.scan(optionsWithPrivateToken)
        service.scan(optionsWithoutPrivateToken)

        then:
        2 * steps.sh(label: 'Set Java 17 for SonarQube scan', script: "source use-j17.sh")
        1 * steps.sh(label: 'Run SonarQube scan', script: { it.contains("/opt/sonar-scanner/bin/sonar-scanner") && it.contains("-Dsonar.projectKey=key") && it.contains("-Dsonar.projectName=name") && it.contains("-Dsonar.exclusions=test/**") && it.contains("-Dsonar.auth.token=my-private-token") })
        1 * steps.sh(label: 'Run SonarQube scan', script: { it.contains("/opt/sonar-scanner/bin/sonar-scanner") && it.contains("-Dsonar.projectKey=key") && it.contains("-Dsonar.projectName=name") && it.contains("-Dsonar.exclusions=test/**") && it.contains("-Dsonar.auth.token=public-token") })
    }

    def "generateReport calls script.sh with correct params and uses correct token"() {
        given:
        def steps = GroovyMock(util.PipelineSteps)
        steps.withSonarQubeEnv(_, _) >> { String env, Closure block -> block() }
        steps.getSONAR_HOST_URL() >> 'https://sonarqube.example.com'
        steps.getSONAR_AUTH_TOKEN() >> 'public-token'
        steps.sh(_) >> null
        def logger = Mock(org.ods.util.ILogger)
        logger.shellScriptDebugFlag >> ""
        def service = new SonarQubeService(steps, logger, "env")

        when:
        service.generateReport("projectKey", "my-private-token")
        service.generateReport("projectKey", "")

        then:
        1 * steps.sh(label: 'Generate Sonar Report', script: { 
            it.contains("java -jar /usr/local/sonar/sonar-report.jar") && 
            it.contains("https://sonarqube.example.com") &&
            it.contains("my-private-token") && 
            it.contains("projectKey") 
        })
        1 * steps.sh(label: 'Generate Sonar Report', script: { 
            it.contains("java -jar /usr/local/sonar/sonar-report.jar") && 
            it.contains("https://sonarqube.example.com") &&
            it.contains("public-token") && 
            it.contains("projectKey") 
        })
    }

    def "getQualityGateJSON calls script.sh with correct params and uses correct token"() {
        given:
        def steps = GroovyMock(util.PipelineSteps)
        steps.withSonarQubeEnv(_, _) >> { String env, Closure block -> block() }
        steps.getSONAR_HOST_URL() >> 'https://sonarqube.example.com'
        steps.getSONAR_AUTH_TOKEN() >> 'public-token'
        steps.sh(_ as Map) >> "{}"
        def logger = Mock(org.ods.util.ILogger)
        def service = new SonarQubeService(steps, logger, "env")

        when:
        service.getQualityGateJSON("projectKey", "developer", "branch", "prKey", "my-private-token")
        service.getQualityGateJSON("projectKey", "developer", "branch", "prKey", "")

        then:
        1 * steps.sh({ args ->
            args.label == 'Get status of quality gate' &&
            args.script.contains("curl") &&
            args.script.contains("--data-urlencode projectKey=projectKey") &&
            args.script.contains("--data-urlencode pullRequest=prKey") &&
            args.script.contains("-u my-private-token:") &&
            args.returnStdout == true
        })
        1 * steps.sh({ args ->
            args.label == 'Get status of quality gate' &&
            args.script.contains("curl") &&
            args.script.contains("--data-urlencode projectKey=projectKey") &&
            args.script.contains("--data-urlencode pullRequest=prKey") &&
            args.script.contains("-u public-token:") &&
            args.returnStdout == true
        })
    }

    def "getComputeEngineTaskJSON calls script.sh with correct params"() {
        given:
        def steps = GroovyMock(util.PipelineSteps)
        steps.withSonarQubeEnv(_, _) >> { String env, Closure block -> block() }
        steps.sh(_ as Map) >> "{}"
        def logger = Mock(org.ods.util.ILogger)
        def service = new SonarQubeService(steps, logger, "env")

        when:
        service.getComputeEngineTaskJSON("taskid", "token")

        then:
        1 * steps.sh({ args ->
            args.label == 'Get status of compute engine task' &&
            args.script.contains("curl") &&
            args.script.contains("api/ce/task?id=taskid") &&
            args.script.contains("-u token:") &&
            args.returnStdout == true
        })
    }

    def "generateAndStoreSonarQubeToken returns empty string if secret exists"() {
        given:
        def steps = GroovyMock(util.PipelineSteps)
        steps.sh(_) >> "some-secret"
        def logger = Mock(org.ods.util.ILogger)
        logger.info(_) >> null
        def service = new SonarQubeService(steps, logger, "env")

        expect:
        service.generateAndStoreSonarQubeToken("credId", "namespace", "secretName") == ""
    }

    def "getScannerBinary returns tool path if which fails"() {
        given:
        def steps = GroovyMock(util.PipelineSteps)
        steps.sh(_) >> 1
        steps.tool('SonarScanner') >> '/opt/sonar-scanner'
        def logger = Mock(org.ods.util.ILogger)
        def service = new SonarQubeService(steps, logger, "env")

        expect:
        service.getScannerBinary() == "/opt/sonar-scanner/bin/sonar-scanner"
    }
}
