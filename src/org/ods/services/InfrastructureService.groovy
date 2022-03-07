package org.ods.services

import org.ods.util.ILogger
import org.ods.util.IPipelineSteps
import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode

@TypeChecked
class InfrastructureService {

    private final IPipelineSteps steps
    private final ILogger logger
    static final String CLOUD_PROVIDER_AWS = 'AWS'
    static final List CLOUD_PROVIDERS = [
        CLOUD_PROVIDER_AWS,
    ]

    InfrastructureService(IPipelineSteps steps, ILogger logger) {
        this.steps = steps
        this.logger = logger
    }

    @TypeChecked(TypeCheckingMode.SKIP)
    public int runMake(String rule, Map environmentVars = [:], String tfBackendS3Key = null, String workspace = null) {
        logger.info "Running 'make ${rule}'..."

        int status = 1
        if (environmentVars.isEmpty()) {
            status = runMakeStep(rule)
        } else {
            steps.withEnv(setEnv(environmentVars, tfBackendS3Key, workspace))
            {
                withCredentials((environmentVars.credentials.key as String).toLowerCase(),
                                (environmentVars.credentials.secret as String).toLowerCase()) {
                    status = runMakeStep(rule)
                }
            }
        }
        return status
    }

    private int runMakeStep(String rule) {
        int status
        status = steps.sh(
            label: 'Infrastructure Makefile',
            returnStatus: true,
            script: """
                set +e && \
                eval \"\$(rbenv init -)\" && \
                make ${rule} && \
                set -e
            """
        ) as int
        return status
    }

    private List<String> setEnv(Map environmentVars, String tfBackendS3Key, String workspace) {
        List<String> env = [
            "TF_BACKEND_PREFIX=${environmentVars.account}".toString(),
            "AWS_DEFAULT_REGION=${environmentVars.region.toString().toLowerCase()}".toString()
        ]
        if (tfBackendS3Key) {
            env << "TF_BACKEND_S3KEY=${tfBackendS3Key}".toString()
        }
        if (workspace) {
            env << "TF_WORKSPACE=${workspace}".toString()
        }
        return env
    }

    private withCredentials(String awsAccessKeyId, String awsSecretAccessKey, Closure block) {
        steps.withCredentials([
            steps.string(credentialsId: awsAccessKeyId, variable: 'AWS_ACCESS_KEY_ID'),
            steps.string(credentialsId: awsSecretAccessKey, variable: 'AWS_SECRET_ACCESS_KEY')
        ]) {
            block(steps.env.AWS_ACCESS_KEY_ID, steps.env.AWS_SECRET_ACCESS_KEY)
        }
    }

}
