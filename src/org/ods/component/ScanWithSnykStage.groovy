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
        if (config.branch) {
            config.eligibleBranches = config.branch.split(',')
        } else {
            config.eligibleBranches = ['*']
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
        if (!isEligibleBranch(config.eligibleBranches, context.gitBranch)) {
            logger.info "Skipping as branch '${context.gitBranch}' is not covered by the 'branch' option."
            return
        }

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
            "severityThreshold=${config.severityThreshold}."

        boolean noVulnerabilitiesFound

        def envVariables = [
            "NEXUS_HOST=${context.nexusHost}",
            "NEXUS_USERNAME=${context.nexusUsername}",
            "NEXUS_PASSWORD=${context.nexusPassword}",
        ]
        // nexus credentials are provided here because snyk runs build.gradle who needs them
        logger.startClocked("${config.projectName}-snyk-scan")
        script.withEnv(envVariables) {
            noVulnerabilitiesFound = snyk.test(config.organisation, config.buildFile, config.severityThreshold)
            if (noVulnerabilitiesFound) {
                script.echo 'No vulnerabilities detected.'
            } else {
                script.echo 'Snyk test detected vulnerabilities.'
            }
        }
        logger.debugClocked("${config.projectName}-snyk-scan")

        if (!snyk.monitor(config.organisation, config.buildFile)) {
            script.error 'Snyk monitor failed'
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
