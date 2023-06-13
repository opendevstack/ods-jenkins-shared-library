package org.ods.services

import groovy.transform.TypeChecked
import org.ods.util.ILogger
import org.ods.util.IPipelineSteps

@TypeChecked
class TrivyService {

    static final int TRIVY_SUCCESS = 0
    static final int TRIVY_OPERATIONAL_ERROR = 1

    private final IPipelineSteps steps
    private final ILogger logger

    TrivyService(IPipelineSteps steps, ILogger logger) {
        this.steps = steps
        this.logger = logger
    }

    @SuppressWarnings('ParameterCount')
    int scanViaCli(String scanners, String vulType, String format, String flags,
        String reportFile, String openshiftDomain, String nexusRepository ) {
        logger.info "Starting to scan via Trivy CLI..."
        int status = TRIVY_SUCCESS
        status = steps.sh(
            label: 'Scan via Trivy CLI',
            returnStatus: true,
            script: """
                set +e && \
                trivy fs  \
                --db-repository ${nexusRepository}.${openshiftDomain}/aquasecurity/trivy-db \
                --java-db-repository ${nexusRepository}.${openshiftDomain}/aquasecurity/trivy-java-db \
                --cache-dir /tmp/.cache \
                --scanners ${scanners} \
                --vuln-type ${vulType} \
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
                trivy sbom ${reportFile} \
                set -e
            """
        )

        return status
    }

}
