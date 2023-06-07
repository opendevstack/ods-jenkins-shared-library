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
    int scanViaCli(String scanners, String vulType, String format, String reportFile) {
        logger.info "Starting to scan via Trivy CLI..."
        int status = TRIVY_SUCCESS
        status = steps.sh(
            label: 'Scan via Trivy CLI',
            returnStatus: true,
            script: """
                set +e && \
                trivy fs  \
                --cache-dir /tmp/.cache \
                --scanners ${scanners} \
                --vuln-type ${vulType} \
                --format ${format} \
                --output ${reportFile} \
                --license-full \
                . && \
                set -e
            """
        ) as int
        return status
    }

}
