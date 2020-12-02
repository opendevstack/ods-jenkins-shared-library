package org.ods.component

import org.ods.services.SnykService
import org.ods.util.ILogger

class ScanWithSnykStage extends Stage {

    public final String STAGE_NAME = 'Snyk Security Scan'
    private final SnykService snyk

    ScanWithSnykStage(def script, IContext context, Map config, SnykService snyk,
        ILogger logger) {
        super(script, context, config, logger)
        if (!config.containsKey('failOnVulnerabilities')) {
            config.failOnVulnerabilities = context.failOnSnykScanVulnerabilities
        }
        if (!config.organisation) {
            config.organisation = context.projectId
        }
        if (!config.projectName) {
            config.projectName = context.componentId
        }
        if (!config.buildFile) {
            config.buildFile = 'build.gradle'
        }
        if (config.severityThreshold) {
            config.severityThreshold = config.severityThreshold.trim().toLowerCase()
        } else {
            // low is the default, it is equal to not providing the option to snyk
            config.severityThreshold = 'low'
        }
        this.snyk = snyk
    }

    protected run() {
        if (!config.snykAuthenticationCode) {
            script.error "Option 'snykAuthenticationCode' is not set!"
        }
        def allowedSeverityThresholds = ['low', 'medium', 'high']
        if (!allowedSeverityThresholds.contains(config.severityThreshold)) {
            script.error "'${config.severityThreshold}' is not a valid value " +
                "for option 'severityThreshold'! Please use one of ${allowedSeverityThresholds}."
        }

        if (!snyk.version()) {
            script.error 'Snyk binary is not in $PATH'
        }

        if (!snyk.auth(config.snykAuthenticationCode)) {
            script.error 'Snyk auth failed'
        }

        logger.info 'Scanning for vulnerabilities with ' +
            "organisation=${config.organisation}, " +
            "projectName=${config.projectName}, " +
            "buildFile=${config.buildFile}, " +
            "failOnVulnerabilities=${config.failOnVulnerabilities}, " +
            "severityThreshold=${config.severityThreshold}," +
            "additionalOptions=${config.additionalOptions.toMapString()}."

        boolean noVulnerabilitiesFound

        // Nexus credentials are provided as env variables because Snyk may need to
        // execute the build file (e.g. build.gradle) to figure out dependencies.
        def envVariables = [
            "NEXUS_HOST=${context.nexusHost}",
            "NEXUS_USERNAME=${context.nexusUsername}",
            "NEXUS_PASSWORD=${context.nexusPassword}",
        ]
        script.withEnv(envVariables) {
            logger.startClocked("${config.projectName}-snyk-scan")
            noVulnerabilitiesFound = snyk.test(config.organisation,
                config.buildFile,
                config.severityThreshold,
                config.additionalOptions)
            if (noVulnerabilitiesFound) {
                logger.info 'No vulnerabilities detected.'
            } else {
                logger.warn 'Snyk test detected vulnerabilities.'
            }
            logger.debugClocked("${config.projectName}-snyk-scan")

            logger.startClocked("${config.projectName}-snyk-monitor")
            if (!snyk.monitor(config.organisation, config.buildFile)) {
                script.error 'Snyk monitor failed'
            }
            logger.debugClocked("${config.projectName}-snyk-monitor")
        }

        generateAndArchiveReport(context.localCheckoutEnabled)

        if (!noVulnerabilitiesFound && config.failOnVulnerabilities) {
            script.error 'Snyk scan stage failed. See snyk report for details.'
        }
    }

    private generateAndArchiveReport(boolean archive) {
        def targetReport = "SCSR-${context.projectId}-${context.componentId}-${snyk.reportFile}"
        script.sh(
            label: 'Create artifacts dir',
            script: 'mkdir -p artifacts/SCSR'
        )
        script.sh(
            label: 'Rename report to SCSR',
            script: "mv ${snyk.reportFile} artifacts/${targetReport}"
        )
        if (archive) {
            script.archiveArtifacts(artifacts: 'artifacts/SCSR*')
        }
        def snykScanStashPath = "scsr-report-${context.componentId}-${context.buildNumber}"
        context.addArtifactURI('snykScanStashPath', snykScanStashPath)

        script.stash(
            name: "${snykScanStashPath}",
            includes: 'artifacts/SCSR*',
            allowEmpty: true
        )
        context.addArtifactURI('SCSR', targetReport)
    }

}
