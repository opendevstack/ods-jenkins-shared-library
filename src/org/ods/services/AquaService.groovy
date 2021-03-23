package org.ods.services

import org.ods.util.ILogger

class AquaService {

    private final def script
    private final ILogger logger

    AquaService(def script, ILogger logger) {
        this.script = script
        this.logger = logger
    }

    void scanViaCli(String aquaUrl, String registry, String imageRef, String credentialsId, String reportFile) {
        logger.info "Starting to scan via Aqua CLI..."
        int status = 0
        withCredentials(credentialsId) { username, password ->
            status = script.sh(
                label: 'Scan via Aqua CLI',
                returnStatus: true,
                script: """
                  scannercli scan ${imageRef} \
                  --dockerless \
                  --register \
                  --htmlfile '${reportFile}' \
                  -U ${username} \
                  -P ${password} \
                  -H ${aquaUrl} \
                  --registry '${registry}'
                """
            )
        }
        // see possible status codes at https://docs.aquasec.com/docs/scanner-cmd-scan#section-return-codes
        switch (status) {
            case 0:
                logger.info "Finished scan via Aqua CLI successfully!"
                break
            case 1:
                script.error "An error occurred in processing the scan request " +
                    "(e.g. invalid command line options, image not pulled, operational error)."
                break
            case 4:
                script.error "The image scanned failed at least one of the Image Assurance Policies specified."
                break
            default:
                script.error "An unknown status code was returned!"
        }
    }

    def withCredentials(String credentialsId, Closure block) {
        script.withCredentials([
            script.usernamePassword(
                credentialsId: credentialsId,
                usernameVariable: 'USERNAME',
                passwordVariable: 'PASSWORD'
            )
        ]) {
            block(script.env.USERNAME, script.env.PASSWORD)
        }
    }

}
