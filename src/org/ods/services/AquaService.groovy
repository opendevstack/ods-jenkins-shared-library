package org.ods.services

import groovy.transform.TypeChecked
import org.ods.util.ILogger
import org.ods.util.IPipelineSteps

@TypeChecked
class AquaService {

    static final int AQUA_SUCCESS = 0
    static final int AQUA_OPERATIONAL_ERROR = 1
    static final int AQUA_POLICIES_ERROR = 4

    private final IPipelineSteps steps
    private final ILogger logger

    AquaService(IPipelineSteps steps, ILogger logger) {
        this.steps = steps
        this.logger = logger
    }

    int scanViaCli(String aquaUrl, String registry, String imageRef, String credentialsId, String reportFile) {
        logger.info "Starting to scan via Aqua CLI..."
        int status = AQUA_SUCCESS
        withCredentials(credentialsId) { username, password ->
            status = steps.sh(
                label: 'Scan via Aqua CLI',
                returnStatus: true,
                script: """
                  set +e && \
                  aquasec scan ${imageRef} \
                  --dockerless \
                  --register \
                  --text \
                  --htmlfile '${reportFile}' \
                  -w /tmp \
                  -U '${username}' \
                  -P '${password}' \
                  -H '${aquaUrl}' \
                  --registry '${registry}' && \
                  set -e
                """
            ) as int
        }
        return status
    }

    def withCredentials(String credentialsId, Closure block) {
        steps.withCredentials([
            steps.usernamePassword(
                credentialsId: credentialsId,
                usernameVariable: 'USERNAME',
                passwordVariable: 'PASSWORD'
            )
        ]) {
            block(steps.env.USERNAME, steps.env.PASSWORD)
        }
    }

}
