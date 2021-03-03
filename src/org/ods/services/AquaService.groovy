package org.ods.services

import groovy.json.JsonOutput
import org.ods.util.ILogger

@SuppressWarnings('ParameterCount')
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
        withCredentials(credentialsId) {username, password ->
            status = script.sh(
                label: 'Scan via Aqua CLI',
                returnStatus: true,
                script: """
                  scannercli scan ${imageRef} \
                  --dockerless \
                  --register \
                  --jsonfile '${reportFile}' \
                  -U ${username} \
                  -P ${password} \
                  -H ${aquaUrl} \
                  --registry '${registry}'
                """
            )
        }
        // see possible status codes at https://docs.aquasec.com/docs/scanner-cmd-scan#section-return-codes
        if (status == 0) {
            logger.info "Finished scan via Aqua CLI successfully!"
        }
        if (status == 1) {
            script.error "An error occurred in processing the scan request " +
                "(e.g. invalid command line options, image not pulled, operational error)."
        }
        if (status == 4) {
            script.error "The image scanned failed at least one of the Image Assurance Policies specified."
        }
    }


    /**
     * Starts the scan process on a remote scanner by using the Aqua API.
     */
    void initiateScanViaApi(String aquaUrl, String registry, String token, String imageRef) {
        imageRef = imageRef.replace("/", "%2F")
        logger.info "Starting to scan via Aqua API..."
        String response = script.sh(
            label: 'Scan via Aqua API',
            returnStdout: true,
            script: """
              curl \\
              --request POST '${aquaUrl}/api/v1/scanner/registry/${registry}/image/${imageRef}/scan' \\
              --header 'Authorization: Bearer ${token}'
            """
        ).trim()
        logger.info(response)
    }

    /**
     * Returns a session token as a string. By default, tokens are valid for 9 hours,
     * for further information please visit the official documentation at
     * https://docs.aquasec.com/reference#aqua-api-overview.
     *
     * @return the generated session token as a string
     */
    String getApiToken(String aquaUrl, String credentialsId) {
        String response
        withCredentials(credentialsId) { username, password ->
            response = script.sh(
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
            def js = script.readJSON(text: response)
            return js['token']
        } catch (Exception ex) {
            logger.warn "Could not understand API response. Error was: ${ex}"
        }
        return null
    }

    void getScanResultViaApi(String aquaUrl, String registry, String token, String imageRef, String reportFile) {
        imageRef = imageRef.replace("/", "%2F")
        logger.info "Retrieving Aqua scan result via API..."
        int requestAttempts = 5
        int millisecondsBetweenAttempts = 5000
        while (requestAttempts > 0) {
            if (getScanStatusViaApi(aquaUrl, registry, token, imageRef) == "Scanned") {
                String response = script.sh(
                    label: 'Get scan result via API',
                    returnStdout: true,
                    script: """
                      curl \\
                      --request GET '${aquaUrl}/api/v1/scanner/registry/${registry}/image/${imageRef}/scan_result' \\
                      --header 'Authorization: Bearer ${token}'
                    """
                ).trim()
                script.writeFile(
                    file: reportFile,
                    text: JsonOutput.prettyPrint(JsonOutput.toJson(response))
                )
                return
            }
            sleep(millisecondsBetweenAttempts)
            requestAttempts--
        }
        String timePassed = String.valueOf(requestAttempts * millisecondsBetweenAttempts)
        logger.warn "Aqua scan result did not come back early enough from server " +
            "(waited for " + timePassed + " milliseconds)!"
    }

    String getScanStatusViaApi(String aquaUrl, String registry, String token, String imageRef) {
        imageRef = imageRef.replace("/", "%2F")
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
