package org.ods.component

import org.ods.util.IPipelineSteps
import org.ods.util.Logger
import vars.test_helper.PipelineSpockTestBase

class ImageECRSpec extends PipelineSpockTestBase {

    private static final Map<String, Object> AWS_ENV = [
        account: '123456789012',
        region: 'eu-west-1',
    ]

    private static final Map<String, Object> DEFAULT_CONTEXT = [
        projectId: 'foo',
        cdProject: 'foo-cd',
        componentId: 'component-a',
        dockerRegistry: 'docker-registry.example.com',
        gitCommit: 'abc12345678',
    ]

    IPipelineSteps steps

    def setup() {
        steps = Mock(IPipelineSteps)
    }

    private ImageECR createImageECR(Map<String, Object> awsEnv = AWS_ENV, Map<String, Object> contextOverrides = [:]) {
        def script = loadScript('vars/withStage.groovy')
        def logger = new Logger(script, false)
        IContext context = new Context(script, DEFAULT_CONTEXT + contextOverrides, logger)
        // Stub the OC token call made in fetchOCToken
        steps.sh([script: 'oc whoami -t', returnStdout: true]) >> 'my-oc-token'
        def imageECR = new ImageECR(steps, context, logger, awsEnv)
        imageECR.fetchOCToken()
        return imageECR
    }

    def "constructor does not call shell commands"() {
        given:
        def script = loadScript('vars/withStage.groovy')
        def logger = new Logger(script, false)
        IContext context = new Context(script, DEFAULT_CONTEXT, logger)

        when:
        def imageECR = new ImageECR(steps, context, logger, AWS_ENV)

        then:
        imageECR != null
        0 * steps.sh(_)
    }

    def "fetchOCToken retrieves OC token"() {
        given:
        def script = loadScript('vars/withStage.groovy')
        def logger = new Logger(script, false)
        IContext context = new Context(script, DEFAULT_CONTEXT, logger)
        def imageECR = new ImageECR(steps, context, logger, AWS_ENV)

        when:
        imageECR.fetchOCToken()

        then:
        1 * steps.sh({ it.script == 'oc whoami -t' && it.returnStdout == true }) >> 'my-oc-token'
    }

    def "retagImages creates repository and copies image for each image"() {
        given:
        def imageECR = createImageECR()
        def images = ['image-a', 'image-b'] as Set

        when:
        imageECR.retagImages('target-project', images, 'source-tag', 'target-tag')

        then:
        // get AWS password called (at least once)
        _ * steps.sh({ it.script?.contains('aws ecr get-login-password') }) >> 'aws-password'

        // create-repository called for each image
        1 * steps.sh({
            it.script?.contains('aws ecr create-repository --repository-name image-a') &&
            it.script?.contains("--region ${AWS_ENV.region}") &&
            it.returnStatus == true
        }) >> 0
        1 * steps.sh({
            it.script?.contains('aws ecr create-repository --repository-name image-b') &&
            it.script?.contains("--region ${AWS_ENV.region}") &&
            it.returnStatus == true
        }) >> 0

        // skopeo copy called for each image
        1 * steps.sh({
            it.script?.contains('skopeo copy') &&
            it.script?.contains("foo-cd/image-a:source-tag") &&
            it.script?.contains("${AWS_ENV.account}.dkr.ecr.${AWS_ENV.region}.amazonaws.com/image-a:target-tag") &&
            it.returnStatus == true
        }) >> 0
        1 * steps.sh({
            it.script?.contains('skopeo copy') &&
            it.script?.contains("foo-cd/image-b:source-tag") &&
            it.script?.contains("${AWS_ENV.account}.dkr.ecr.${AWS_ENV.region}.amazonaws.com/image-b:target-tag") &&
            it.returnStatus == true
        }) >> 0
    }

    def "retagImages creates repository for a single image"() {
        given:
        def imageECR = createImageECR()
        def images = ['my-service'] as Set

        when:
        imageECR.retagImages('target-project', images, 'v1.0', 'v1.1')

        then:
        _ * steps.sh({ it.script?.contains('aws ecr get-login-password') }) >> 'aws-password'

        1 * steps.sh({
            it.script?.contains('aws ecr create-repository --repository-name my-service') &&
            it.returnStatus == true
        }) >> 0
        1 * steps.sh({
            it.script?.contains('skopeo copy') &&
            it.script?.contains('my-service:v1.0') &&
            it.script?.contains("${AWS_ENV.account}.dkr.ecr.${AWS_ENV.region}.amazonaws.com/my-service:v1.1") &&
            it.returnStatus == true
        }) >> 0
    }

    def "retagImages handles empty image set"() {
        given:
        def imageECR = createImageECR()
        def images = [] as Set

        when:
        imageECR.retagImages('target-project', images, 'tag1', 'tag2')

        then:
        // No shell commands for create-repository or skopeo copy should be issued
        0 * steps.sh({ it.script?.contains('aws ecr create-repository') })
        0 * steps.sh({ it.script?.contains('skopeo copy') })
    }

    def "getECRRegistry returns correct URL based on awsEnvironmentVars"() {
        given:
        def customAwsEnv = [account: '999888777666', region: 'us-east-1']
        def imageECR = createImageECR(customAwsEnv)
        def images = ['app'] as Set

        when:
        imageECR.retagImages('proj', images, 'src', 'dst')

        then:
        _ * steps.sh({ it.script?.contains('aws ecr get-login-password') }) >> 'pwd'

        1 * steps.sh({
            it.script?.contains('aws ecr create-repository') &&
            it.returnStatus == true
        }) >> 0
        1 * steps.sh({
            it.script?.contains('999888777666.dkr.ecr.us-east-1.amazonaws.com/app:dst') &&
            it.returnStatus == true
        }) >> 0
    }

    def "copyImage uses correct docker source with registry and cdProject"() {
        given:
        def imageECR = createImageECR()
        def images = ['my-app'] as Set

        when:
        imageECR.retagImages('proj', images, 'abc123', 'def456')

        then:
        _ * steps.sh({ it.script?.contains('aws ecr get-login-password') }) >> 'aws-pwd'

        1 * steps.sh({
            it.script?.contains('aws ecr create-repository') &&
            it.returnStatus == true
        }) >> 0
        1 * steps.sh({
            it.script?.contains("docker://docker-registry.example.com/foo-cd/my-app:abc123") &&
            it.script?.contains("docker://123456789012.dkr.ecr.eu-west-1.amazonaws.com/my-app:def456") &&
            it.returnStatus == true
        }) >> 0
    }
}
