package org.ods.services

import org.ods.component.IContext
import org.ods.util.Logger
import vars.test_helper.PipelineSpockTestBase

class AWSServiceSpec extends PipelineSpockTestBase {

    private Map<String, Object> createAwsEnvironmentVars() {
        return [
            credentials: [
                key: 'AWS_ACCESS_KEY_ID',
                secret: 'AWS_SECRET_ACCESS_KEY'
            ],
            region: 'eu-west-1',
            eksCluster: 'my-eks-cluster'
        ]
    }

    def "awsLogin configures AWS credentials and region"() {
        given:
        def steps = Spy(util.PipelineSteps)
        steps.withCredentials(_, _) >> { args -> args[1].call() }
        def context = Mock(IContext)
        def awsEnvVars = createAwsEnvironmentVars()
        def service = new AWSService(steps, context, awsEnvVars, new Logger(steps, false))

        when:
        service.awsLogin()

        then:
        1 * steps.sh({ it.script == 'aws configure set aws_access_key_id $AWS_ACCESS_KEY_ID --profile default' && it.returnStatus == true }) >> 0
        1 * steps.sh({ it.script == 'aws configure set aws_secret_access_key $AWS_SECRET_ACCESS_KEY --profile default' && it.returnStatus == true }) >> 0
        1 * steps.sh({ it.script == 'aws configure set region eu-west-1 --profile default' && it.returnStatus == true }) >> 0
    }

    def "awsLogin reports error when command fails"() {
        given:
        def steps = Spy(util.PipelineSteps)
        steps.withCredentials(_, _) >> { args -> args[1].call() }
        steps.error(_) >> { throw new RuntimeException(it[0]) }
        def context = Mock(IContext)
        def awsEnvVars = createAwsEnvironmentVars()
        def service = new AWSService(steps, context, awsEnvVars, new Logger(steps, false))

        when:
        service.awsLogin()

        then:
        thrown(RuntimeException)
        1 * steps.sh({ it.script == 'aws configure set aws_access_key_id $AWS_ACCESS_KEY_ID --profile default' && it.returnStatus == true }) >> 1
    }

    def "setEKSCluster updates kubeconfig and creates namespace"() {
        given:
        def steps = Spy(util.PipelineSteps)
        steps.withCredentials(_, _) >> { args -> args[1].call() }
        def context = Mock(IContext) {
            getProjectId() >> 'myproject'
            getEnvironment() >> 'dev'
        }
        def awsEnvVars = createAwsEnvironmentVars()
        def service = new AWSService(steps, context, awsEnvVars, new Logger(steps, false))

        when:
        service.setEKSCluster()

        then:
        1 * steps.sh({ it.script == 'aws eks list-clusters' && it.returnStatus == true }) >> 0
        1 * steps.sh({ it.script == 'aws eks update-kubeconfig --region eu-west-1 --name my-eks-cluster' && it.returnStatus == true }) >> 0
        1 * steps.sh({ it.script == 'kubectl create namespace myproject-dev' && it.returnStatus == true }) >> 0
    }

    def "setEKSCluster does not report error when namespace already exists"() {
        given:
        def steps = Spy(util.PipelineSteps)
        steps.withCredentials(_, _) >> { args -> args[1].call() }
        def context = Mock(IContext) {
            getProjectId() >> 'myproject'
            getEnvironment() >> 'dev'
        }
        def awsEnvVars = createAwsEnvironmentVars()
        def service = new AWSService(steps, context, awsEnvVars, new Logger(steps, false))

        when:
        service.setEKSCluster()

        then:
        1 * steps.sh({ it.script == 'aws eks list-clusters' && it.returnStatus == true }) >> 0
        1 * steps.sh({ it.script == 'aws eks update-kubeconfig --region eu-west-1 --name my-eks-cluster' && it.returnStatus == true }) >> 0
        // namespace creation fails (already exists) - should NOT call error because showError=false
        1 * steps.sh({ it.script == 'kubectl create namespace myproject-dev' && it.returnStatus == true }) >> 1
        0 * steps.error(_)
    }

    def "credentials keys are lowercased in withCredentials call"() {
        given:
        def steps = Spy(util.PipelineSteps)
        steps.withCredentials(_, _) >> { args -> args[1].call() }
        steps.sh(_) >> 0
        def context = Mock(IContext)
        def awsEnvVars = [
            credentials: [
                key: 'MY_CUSTOM_KEY_ID',
                secret: 'MY_CUSTOM_SECRET_KEY'
            ],
            region: 'us-east-1',
            eksCluster: 'cluster-1'
        ]
        def service = new AWSService(steps, context, awsEnvVars, new Logger(steps, false))

        when:
        service.awsLogin()

        then:
        1 * steps.string(credentialsId: 'my_custom_key_id', variable: 'AWS_ACCESS_KEY_ID')
        1 * steps.string(credentialsId: 'my_custom_secret_key', variable: 'AWS_SECRET_ACCESS_KEY')
    }

}

