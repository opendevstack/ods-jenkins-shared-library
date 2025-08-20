package org.ods.component

import org.ods.services.ServiceRegistry
import org.ods.services.GitService
import org.ods.services.OpenShiftService
import org.ods.util.ILogger
import vars.test_helper.PipelineSpockTestBase
import spock.lang.Unroll

class PipelineSpec extends PipelineSpockTestBase {

    def pipeline
    def logger
    
    def setup() {
        logger = Mock(ILogger)
        helper.registerAllowedMethod('podAnnotation', [ Map ], { Map config ->
            return [key: config.key, value: config.value]
        })
        helper.registerAllowedMethod('node', [String.class, Closure.class], null)
        helper.registerAllowedMethod('error', [String.class], { String message ->
            throw new RuntimeException(message)
        })
        
        // Initialize binding.variables.env for tests that need it
        binding.variables.env = [:]
        binding.variables.currentBuild = [result: null]
        
        // Create a mock script object with the required methods
        def mockScript = [
            podAnnotation: { Map podConfig -> return [key: podConfig.key, value: podConfig.value] },
            readFile: { Map args -> 
                if (args.file == 'Jenkinsfile') {
                    return """
                    def call() {
                        odsComponentStageScanWithSonar context
                    }
                    """
                }
                return ""
            },
            error: { String message ->
                throw new RuntimeException(message)
            },
            node: { String nodeSpec, Closure closure ->
                closure.call()
            },
            wrap: { Map wrapperConfig, Closure closure ->
                closure.call()  // Just execute the closure without actual wrapping
            },
            stage: { String stageName, Closure closure ->
                return closure.call()  // Return the result of the closure execution
            },
            scm: null,  // Mock scm object
            env: binding.variables.env,   // Use env from binding  
            sh: { Map args -> return "" },  // Mock sh method
            currentBuild: binding.variables.currentBuild,  // Add currentBuild reference
            checkout: { Object scm -> return scm },  // Mock checkout method
            containerTemplate: { Map options -> return options },  // Mock containerTemplate method
            podTemplate: { Map options, Closure closure -> closure.call() },  // Mock podTemplate method
            fileExists: { String path -> return false },  // Mock fileExists method
            dir: { String path, Closure closure -> closure.call() },  // Mock dir method
            findFiles: { Map config -> return [] },  // Mock findFiles method - returns empty list
            emailext: { Map args -> /* Mock emailext method */ }  // Mock emailext method
        ]
        pipeline = new Pipeline(mockScript, logger)
    }

    def cleanup() {
        ServiceRegistry.instance.clear()
    }

    def "check for SonarQube stage in pipeline script"() {
        given:
        // Create a script with mock readFile method that returns sonar stage
        def mockScriptWithSonar = [
            podAnnotation: { Map config -> return [key: config.key, value: config.value] },
            readFile: { Map args -> 
                """
                def call() {
                    odsComponentStageScanWithSonar context
                }
                """
            },
            error: { String message ->
                throw new RuntimeException(message)
            },
            node: { String nodeSpec, Closure closure ->
                closure.call()
            },
            scm: null,  // Mock scm object
            env: [:],   // Mock env object  
            sh: { Map args -> return "" }  // Mock sh method
        ]
        def pipelineWithSonar = new Pipeline(mockScriptWithSonar, logger)

        when:
        def result = pipelineWithSonar.invokeMethod('checkForSonarStageInPipeline', [] as Object[])

        then:
        result == true
    }

    def "check for SonarQube stage handles commented code"() {
        given:
        // Create a script with mock readFile method that returns commented sonar stage
        def mockScriptWithComments = [
            podAnnotation: { Map config -> return [key: config.key, value: config.value] },
            readFile: { Map args -> 
                """
                def call() {
                    // odsComponentStageScanWithSonar context
                    /* odsComponentStageScanWithSonar context */
                    echo 'hello'
                }
                """
            },
            error: { String message ->
                throw new RuntimeException(message)
            },
            node: { String nodeSpec, Closure closure ->
                closure.call()
            },
            scm: null,  // Mock scm object
            env: [:],   // Mock env object  
            sh: { Map args -> return "" }  // Mock sh method
        ]
        def pipelineWithComments = new Pipeline(mockScriptWithComments, logger)

        when:
        def result = pipelineWithComments.invokeMethod('checkForSonarStageInPipeline', [] as Object[])

        then:
        result == false
    }

    def "remove commented code handles various comment styles"() {
        given:
        def content = """
            // Single line comment
            code1
            /* Block comment */
            code2
            /* Multi-line
               comment */
            code3
            code4 // Inline comment
        """

        when:
        def result = pipeline.invokeMethod('removeCommentedCode', content)

        then:
        !result.contains('Single line comment')
        !result.contains('Block comment')
        !result.contains('Multi-line')
        !result.contains('// Inline comment')  // Inline comment should be removed
        result.contains('code1')
        result.contains('code2')
        result.contains('code3')
        result.contains('code4') // code4 should remain, only the comment after // should be removed
    }

    def "execute handles multi-repo build setup correctly"() {
        given:
        def config = [:]
        binding.variables.env = [
            MULTI_REPO_BUILD: 'true',
            MULTI_REPO_ENV: 'dev',
            NOTIFY_BB_BUILD: 'true'
        ]
        // Update the pipeline's script env to match
        pipeline.script.env = binding.variables.env

        when:
        pipeline.setupForMultiRepoBuild(config)

        then:
        !config.localCheckoutEnabled
        !config.displayNameUpdateEnabled
        !config.ciSkipEnabled
        !config.notifyNotGreen
        config.sonarQubeBranch == '*'
        config.environment == 'dev'
        config.bitbucketNotificationEnabled
    }

    @Unroll
    def "execute handles agent pod config with image=#image, tag=#tag"() {
        given:
        def config = [:]
        if (image) config.image = image
        if (tag) config.imageStreamTag = tag

        when:
        pipeline.invokeMethod('prepareAgentPodConfig', config)

        then:
        config.podServiceAccount == 'jenkins'
        config.alwaysPullImage
        config.resourceRequestMemory == '1Gi'
        config.resourceLimitMemory == '2Gi'
        config.resourceRequestCpu == '100m'
        config.resourceLimitCpu == '1'
        config.podLabel.startsWith('pod-')
        config.annotations.size() == 1
        config.annotations[0].key == 'cluster-autoscaler.kubernetes.io/safe-to-evict'
        config.annotations[0].value == 'false'

        where:
        image           | tag
        'maven:latest'  | null
        null           | 'maven:latest'
    }

    def "execute fails when neither image nor imageStreamTag is provided"() {
        given:
        def config = [:]

        when:
        pipeline.invokeMethod('prepareAgentPodConfig', config)

        then:
        def exception = thrown(RuntimeException)
        exception.message == "One of 'image', 'imageStreamTag' or 'podContainers' is required"
    }

    def "execute amends project and component from git origin"() {
        given:
        def config = [:]
        
        // Create a mock script that will return git URL when GitService tries to get origin
        def mockGitScript = [
            podAnnotation: { Map podConfig -> return [key: podConfig.key, value: podConfig.value] },
            readFile: { Map args -> return "" },
            error: { String message -> throw new RuntimeException(message) },
            node: { String nodeSpec, Closure closure -> closure.call() },
            scm: null,
            env: [JOB_NAME: 'my-project/my-project-component'],
            sh: { Map args -> 
                if (args.script == 'git config --get remote.origin.url') {
                    return 'https://github.com/my-project/my-project-component.git'
                }
                return ""
            },
            currentBuild: binding.variables.currentBuild
        ]
        def gitPipeline = new Pipeline(mockGitScript, logger)

        when:
        gitPipeline.invokeMethod('amendProjectAndComponentFromOrigin', config)

        then:
        config.projectId == 'my-project'
        config.componentId == 'component'
        config.repoName == 'my-project-component'
    }

    def "execute handles build errors gracefully"() {
        given:
        def config = [projectId: 'test', componentId: 'test']
        def error = new RuntimeException("Build failed")
        binding.variables.currentBuild = [result: null]

        when:
        pipeline.execute(config) { throw error }

        then:
        thrown(RuntimeException)
    }

    def "execute skips build on ci skip comment"() {
        given:
        def config = [
            projectId: 'test', 
            componentId: 'test',
            image: 'maven:latest',  // Add required image config
            branchToEnvironmentMapping: [:],
            localCheckoutEnabled: false  // Prevent registry.clear() from removing our mock
        ]
        def gitService = Mock(GitService)
        gitService.ciSkipInCommitMessage >> true
        ServiceRegistry.instance.add(GitService, gitService)
        binding.variables.currentBuild = [result: null]
        binding.variables.env = [
            // MULTI_REPO_BUILD: 'false',  // Remove this to avoid triggering setupForMultiRepoBuild
            MULTI_REPO_ENV: 'dev',  // Add the required env variable
            BITBUCKET_URL: 'http://bitbucket.example.com',
            NEXUS_URL: 'http://nexus.example.com',
            NEXUS_USERNAME: 'testuser',
            NEXUS_PASSWORD: 'testpass',
            JOB_NAME: 'test-job',
            BUILD_NUMBER: '1',
            BUILD_TAG: 'test-build-1',
            OPENSHIFT_API_URL: 'https://openshift.example.com',
            BUILD_URL: 'http://jenkins.example.com/job/test-job/1/'
        ]
        pipeline.script.env = binding.variables.env
        pipeline.script.currentBuild = binding.variables.currentBuild  // Update script reference

        when:
        def result = pipeline.execute(config) { /* stages */ }

        then:
        binding.variables.currentBuild.result == 'NOT_BUILT'
        result == pipeline
    }

    def "execute configures basic context correctly"() {
        given:
        def config = [
            projectId: 'my-project',
            componentId: 'my-component',
            image: 'maven:latest',
            branchToEnvironmentMapping: [:]
        ]
        binding.variables.currentBuild = [result: null]
        binding.variables.env = [
            // MULTI_REPO_BUILD: 'false',  // Remove this to avoid triggering setupForMultiRepoBuild
            MULTI_REPO_ENV: 'dev',  // Add the required env variable
            BITBUCKET_URL: 'http://bitbucket.example.com',
            NEXUS_URL: 'http://nexus.example.com',
            NEXUS_USERNAME: 'testuser',
            NEXUS_PASSWORD: 'testpass',
            JOB_NAME: 'my-project-cd/my-project-cd-my-component',  // Match expected pattern: cdProject/cdProject-componentId
            BUILD_NUMBER: '1',
            BUILD_TAG: 'test-build-1',
            OPENSHIFT_API_URL: 'https://openshift.example.com',
            BUILD_URL: 'http://jenkins.example.com/job/test-job/1/'
        ]
        pipeline.script.env = binding.variables.env

        when:
        pipeline.execute(config) { context ->
            assert context.componentId == 'my-component'
            assert context.projectId == 'my-project'
        }

        then:
        noExceptionThrown()
    }
}
