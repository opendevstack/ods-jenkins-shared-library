package org.ods.component

import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode
import org.ods.services.SnykService
import org.ods.util.ILogger

@TypeChecked
class ScanWithSnykStage extends Stage {

    public final String STAGE_NAME = 'Snyk Security Scan'
    private final SnykService snyk
    private final ScanWithSnykOptions options

    @TypeChecked(TypeCheckingMode.SKIP)
    ScanWithSnykStage(
        def script,
        IContext context,
        Map<String, Object> config,
        SnykService snyk,
        ILogger logger) {
        super(script, context, logger)
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

        this.options = new ScanWithSnykOptions(config)
        this.snyk = snyk
    }

    protected run() {
        if (!options.snykAuthenticationCode) {
            steps.error "Option 'snykAuthenticationCode' is not set!"
        }
        def allowedSeverityThresholds = ['low', 'medium', 'high']
        if (!allowedSeverityThresholds.contains(options.severityThreshold)) {
            steps.error "'${options.severityThreshold}' is not a valid value " +
                "for option 'severityThreshold'! Please use one of ${allowedSeverityThresholds}."
        }

        if (!snyk.version()) {
            steps.error 'Snyk binary is not in $PATH'
        }

        if (!snyk.auth(options.snykAuthenticationCode)) {
            steps.error 'Snyk auth failed'
        }
        if (logger.debugMode) {
            if (options.additionalFlags) {
                options.additionalFlags += '-d'
            } else {
                options.additionalFlags = ['-d']
            }
        }
        logger.info 'Scanning for vulnerabilities with ' +
            "organisation=${options.organisation}, " +
            "projectName=${options.projectName}, " +
            "buildFile=${options.buildFile}, " +
            "failOnVulnerabilities=${options.failOnVulnerabilities}, " +
            "severityThreshold=${options.severityThreshold}, " +
            "additionalFlags=${options.additionalFlags ? options.additionalFlags.toListString() : ''}."

        boolean noVulnerabilitiesFound

        // Nexus credentials are provided as env variables because Snyk may need to
        // execute the build file (e.g. build.gradle) to figure out dependencies.
        List<String> envVariables = [
            "NEXUS_HOST=${context.nexusHost}".toString(),
            "NEXUS_USERNAME=${context.nexusUsername}".toString(),
            "NEXUS_PASSWORD=${context.nexusPassword}".toString(),
        ]
        steps.withEnv(envVariables) {
            logger.startClocked("${options.projectName}-snyk-scan")
            noVulnerabilitiesFound = snyk.test(options.organisation,
                options.buildFile,
                options.severityThreshold,
                options.additionalFlags)
            if (noVulnerabilitiesFound) {
                logger.info 'No vulnerabilities detected.'
            } else {
                logger.warn 'Snyk test detected vulnerabilities.'
            }
            logger.debugClocked("${options.projectName}-snyk-scan", (null as String))

            logger.startClocked("${options.projectName}-snyk-monitor")
            if (!snyk.monitor(options.organisation, options.buildFile, options.additionalFlags)) {
                steps.error 'Snyk monitor failed'
            }
            logger.debugClocked("${options.projectName}-snyk-monitor", (null as String))
        }

        generateAndArchiveReport(!context.triggeredByOrchestrationPipeline)

        if (!noVulnerabilitiesFound && options.failOnVulnerabilities) {
            steps.error 'Snyk scan stage failed. See snyk report for details.'
        }
    }

    private generateAndArchiveReport(boolean archive) {
        def targetReport = "SCSR-${context.projectId}-${context.componentId}-${snyk.reportFile}"
        steps.sh(
            label: 'Create artifacts dir',
            script: 'mkdir -p artifacts/SCSR'
        )
        steps.sh(
            label: 'Rename report to SCSR',
            script: "mv ${snyk.reportFile} artifacts/${targetReport}"
        )
        if (archive) {
            steps.archiveArtifacts(artifacts: 'artifacts/SCSR*')
        }
        def snykScanStashPath = "scsr-report-${context.componentId}-${context.buildNumber}"
        context.addArtifactURI('snykScanStashPath', snykScanStashPath)

        steps.stash(
            name: "${snykScanStashPath}",
            includes: 'artifacts/SCSR*',
            allowEmpty: true
        )
        context.addArtifactURI('SCSR', targetReport)
    }

}
