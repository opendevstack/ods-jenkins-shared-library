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
    int runMakeWithEnv(String rule, Map environmentVars, String tfBackendS3Key, String workspace) {
        logger.info "Running 'make ${rule}'..."
        int status = 500
        steps.withEnv(setEnv(environmentVars, tfBackendS3Key, workspace))
        {
            withCredentials(
                    environmentVars.credentials.key.toLowerCase(),
                    environmentVars.credentials.secret.toLowerCase()
                ) {
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
            }
        }
        return status
    }

    @SuppressWarnings('ParameterCount')
    int runMake(String rule) {
        logger.info "Running 'make ${rule}'..."
        int status = 500
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

    List<String> setEnv(Map environmentVars, String tfBackendS3Key, String workspace) {
        def env = [
            "TF_BACKEND_PREFIX=${environmentVars.account}",
            "AWS_DEFAULT_REGION=${environmentVars.region.toLowerCase()}"
        ]
        if (tfBackendS3Key) {
            env << "TF_BACKEND_S3KEY=${tfBackendS3Key}"
        }
        if (workspace) {
            env << "TF_WORKSPACE=${workspace}"
        }
        return env
    }

    def withCredentials(String awsAccessKeyId, String awsSecretAccessKey, Closure block) {
        steps.withCredentials([
            steps.string(credentialsId: awsAccessKeyId, variable: 'AWS_ACCESS_KEY_ID'),
            steps.string(credentialsId: awsSecretAccessKey, variable: 'AWS_SECRET_ACCESS_KEY')
        ]) {
            block(steps.env.AWS_ACCESS_KEY_ID, steps.env.AWS_SECRET_ACCESS_KEY)
        }
    }

}
