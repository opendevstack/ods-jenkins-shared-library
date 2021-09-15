package org.ods.services

import org.ods.util.ILogger
import org.ods.util.IPipelineSteps
import groovy.transform.TypeChecked

@TypeChecked
class InfrastructureService {

    private final IPipelineSteps steps
    private final ILogger logger

    InfrastructureService(IPipelineSteps steps, ILogger logger) {
        this.steps = steps
        this.logger = logger
    }

    @SuppressWarnings('ParameterCount')
    int runMake(String rule, String awsAccessKeyId, String awsSecretAccessKey, String tfBackendPrefix, String tfBackendS3Key, String workspace, String region) {
        logger.info "Running 'make ${rule}'..."
        // int status = AQUA_SUCCESS
        withEnv(setEnv(tfBackendPrefix, tfBackendS3Key, workspace, region))
        {
            withCredentials([
                string(credentialsId: awsAccessKeyId, variable: 'AWS_ACCESS_KEY_ID'),
                string(credentialsId: awsSecretAccessKey, variable: 'AWS_SECRET_ACCESS_KEY')
            ]){
                status = steps.sh(
                    label: 'Infrastructure Makefile',
                    returnStatus: true,
                    script: """
                        set +e && \
                        make ${rule} && \
                        set -e
                    """
                ) as int
            }
        }
        return status
    }

    def setEnv(String tfBackendPrefix, String tfBackendS3Key, String workspace, String region) {
        def env = [
            "TF_BACKEND_PREFIX=${tfBackendPrefix}",
            "TF_BACKEND_S3KEY=${tfBackendS3Key}"
        ]

        if (workspace) {
            env << "TF_WORKSPACE=${workspace}"
        }
        if (region) {
            env << "AWS_DEFAULT_REGION=${region}"
        }
        return env
    }

}
