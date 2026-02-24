package org.ods.services

import groovy.transform.TypeChecked
import org.ods.util.ILogger
import org.ods.util.IPipelineSteps

@TypeChecked
class TrivyService {

    static final int TRIVY_SUCCESS = 0
    static final int TRIVY_FAIL = 1

    private final IPipelineSteps steps
    private final ILogger logger

    TrivyService(IPipelineSteps steps, ILogger logger) {
        this.steps = steps
        this.logger = logger
    }

    @SuppressWarnings('ParameterCount')
    int scan(String resourceName, String scanners, String pkgType, String format, String flags,
        String reportFile, String nexusRepository, String openshiftDomain) {
        logger.startClocked(resourceName)
        logger.info "Starting to scan via Trivy CLI..."
        int returnCode = steps.sh(
            label: 'Scan via Trivy CLI',
            returnStatus: true,
            script: """
                set +e && \
                trivy fs  \
                --db-repository ${nexusRepository}.${openshiftDomain}/aquasecurity/trivy-db \
                --java-db-repository ${nexusRepository}.${openshiftDomain}/aquasecurity/trivy-java-db \
                --cache-dir /tmp/.cache \
                --scanners ${scanners} \
                --pkg-types ${pkgType} \
                --format ${format} \
                --output ${reportFile} \
                --license-full \
                ${flags} \
                . && \
                set -e
            """
        ) as int
        steps.sh(
            label: 'Read SBOM with Trivy CLI',
            returnStatus: true,
            script: """
                set +e && \
                trivy sbom \
                --cache-dir /tmp/.cache \
                ${reportFile} && \
                set -e
            """
        )
        switch (returnCode) {
            case TRIVY_SUCCESS:
                logger.info "Finished scan via Trivy CLI successfully!"
                break
            case TRIVY_FAIL:
                logger.info(
                    "An error occurred while processing the Trivy scan request " +
                    "(e.g. invalid command line options, operational error, or " +
                    "severity threshold exceeded when using the --exit-code flag)."
                )
                break
            default:
                logger.info "An unknown return code was returned: ${returnCode}"
        }
        logger.infoClocked(resourceName, "Trivy scan (via CLI)")
        return returnCode
    }

}
