package org.ods.services

import groovy.transform.TypeChecked
import org.ods.util.ILogger
import org.ods.util.IPipelineSteps

@TypeChecked
class AquaService {

    private final IPipelineSteps steps
    private final ILogger logger

    AquaService(IPipelineSteps steps, ILogger logger) {
        this.steps = steps
        this.logger = logger
    }

    int scanViaCli(String aquaUrl, String registry, String imageRef, String credentialsId, String reportFile) {
        logger.info "Starting to scan via Aqua CLI..."
        int status = 0
        withCredentials(credentialsId) { username, password ->
            status = steps.sh(
                label: 'Scan via Aqua CLI',
                returnStatus: true,
                script: """
                  scannercli scan ${imageRef} \
                  --dockerless \
                  --register \
                  --text \
                  --htmlfile '${reportFile}' \
                  -U '${username}' \
                  -P '${password}' \
                  -H '${aquaUrl}' \
                  --registry '${registry}'
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
