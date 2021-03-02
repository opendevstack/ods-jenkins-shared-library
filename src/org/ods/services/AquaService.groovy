package org.ods.services

import org.ods.util.ILogger

@SuppressWarnings('ParameterCount')
class AquaService {

    private final def script
    private final ILogger logger

    // Base URL of Aqua server.
    private final String aquaUrl
    // Name of the credentials which stores the username/password of
    // a user with access to the Aqua server identified by "aquaUrl".
    private final String credentialsId
    // Name in Aqua of the registry that keeps the image to scan
    private final String registry
    // Name of the report file that contains the scan result
    private final String reportFile

    AquaService(def script, String aquaUrl, String credentialsId, String registry, String reportFile, ILogger logger) {
        this.script = script
        this.aquaUrl = aquaUrl
        this.credentialsId = credentialsId
        this.registry = registry
        this.reportFile = reportFile
        this.logger = logger
    }

    String scanViaCli(String imageRef) {
        logger.info "Starting to scan via Aqua CLI..."
        int status = 0
        withCredentials { username, password ->
            status = script.sh(
                label: 'Scan via Aqua CLI',
                returnStatus: true,
                script: """
                  scannercli scan ${imageRef} \
                  --dockerless \
                  --register \
                  --jsonfile ${reportFile} \
                  -U ${username} \
                  -P ${password} \
                  -H ${aquaUrl} \
                  --registry '${registry}'
                """
            )
        }
        // see possible status codes at https://docs.aquasec.com/docs/scanner-cmd-scan#section-return-codes
        if (status == 1) {
            script.error "An error occurred in processing the scan request " +
                "(e.g. invalid command line options, image not pulled, operational error)."
        }
        if (status == 4) {
            script.error "The image scanned failed at least one of the Image Assurance Policies specified."
        }
    }

    String scanViaApi(String token, String imageRef) {
        logger.info "Starting to scan via Aqua API..."
        return script.sh(
            label: 'Scan via Aqua API',
            returnStdout: true,
            script: """
              curl \\
              --request POST '${aquaUrl}/api/v1/scanner/registry/${registry}/image/${imageRef}/scan' \\
              --header 'Authorization: Bearer ${token}'
            """
        ).trim()
    }

    /**
     * Returns a session token as a string. By default, tokens are valid for 9 hours,
     * for further information please visit the official documentation at
     * https://docs.aquasec.com/reference#aqua-api-overview.
     *
     * @return the generated session token as a string
     */
    String getApiToken() {
        String res
        withCredentials { username, password ->
            res = script.sh(
                label: 'Get Aqua API token',
                returnStdout: true,
                script: """
                  curl \\
                  --request POST '${aquaUrl}/api/v1/login' \\
                  --header \"Content-Type: application/json\" \\
                  --data '{\"id\":\"${username}\",\"password\":\"${password}\"}' \\
                """
            ).trim()
        }
        try {
            def js = script.readJSON(text: res)
            return js['token']
        } catch (Exception ex) {
            logger.warn "Could not understand API response. Error was: ${ex}"
        }
        return null
    }

    String retrieveScanResultViaApi(String token, String imageRef) {
        logger.info "Retrieving Aqua scan result via API..."
        def index = 5
        while (index > 0) {
            if (getScanStatusViaApi(aquaUrl, token, registry, imageRef) == "Scanned") {
                return script.sh(
                    label: 'Get scan result via API',
                    returnStdout: true,
                    script: """
                      curl \\
                      --request GET '${aquaUrl}/api/v1/scanner/registry/${registry}/image/${imageRef}/scan_result' \\
                      --header 'Authorization: Bearer ${token}'
                    """
                ).trim()
            }
            sleep(5)
            index--
        }
        logger.warn "Aqua scan result did not come back early enough from server!"
        return ""
    }

    String getScanStatusViaApi(String token, String imageRef) {
        def res = script.sh(
            label: 'Get scan status via API',
            returnStdout: true,
            script: """
              curl \\
              --request GET '${aquaUrl}/api/v1/scanner/registry/${registry}/image/${imageRef}/status' \\
              --header 'Authorization: Bearer ${token}'
            """
        ).trim()
        try {
            def js = script.readJSON(text: res)
            return js['status']
        } catch (Exception ex) {
            logger.warn "Could not understand API response. Error was: ${ex}"
        }
        return null
    }

    def withCredentials(Closure block) {
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

    String getReportFile() {
        reportFile
    }

}
