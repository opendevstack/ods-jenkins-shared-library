package org.ods.component

import org.ods.services.SnykService

class ScanWithSnykStage extends Stage {
    public final String STAGE_NAME = 'Snyk Security Scan'
    private SnykService snyk

    ScanWithSnykStage(def script, IContext context, Map config, SnykService snyk) {
        super(script, context, config)
        if (!config.containsKey('failOnVulnerabilities')) {
            config.failOnVulnerabilities = context.failOnSnykScanVulnerabilities
        }
        if (!config.organisation) {
            config.organisation = context.projectId
        }
        if (!config.projectName) {
            config.projectName = componentId
        }
        if (!config.buildFile) {
            config.buildFile = 'build.gradle'
        }
        if (!config.severityThreshold) {
            // low is the default, it is equal to not providing the option to snyk
            config.severityThreshold = 'low'
        } else {
            config.severityThreshold = config.severityThreshold.trim().toLowerCase()
        }
        this.snyk = snyk
    }

    def run() {
        if (!config.snykAuthenticationCode) {
            script.error "Option 'snykAuthenticationCode' is not set!"
        }
        def allowedSeverityThresholds = ['low', 'medium', 'high']
        if(!allowedSeverityThresholds.contains(config.severityThreshold)) {
            script.error "'${config.severityThreshold}' is not a valid value " +
                "for option 'severityThreshold'! Please use one of ${allowedSeverityThresholds}."
        }

        if (!snyk.version()) {
            script.error 'Snyk binary is not in $PATH'
        }

        if (!snyk.auth(config.snykAuthenticationCode)) {
            script.error 'Snyk auth failed'
        }

        script.echo "Scanning for vulnerabilities with " +
            "organisation=${config.organisation}, " +
            "projectName=${config.projectName}, " +
            "buildFile=${config.buildFile}, " +
            "failOnVulnerabilities=${config.failOnVulnerabilities}, " +
            "severityThreshold=${config.severityThreshold}."

        boolean noVulnerabilitiesFound

        def envVariables = [
            "NEXUS_HOST=${context.nexusHost}",
            "NEXUS_USERNAME=${context.nexusUsername}",
            "NEXUS_PASSWORD=${context.nexusPassword}"
        ]
        // nexus credentials are provided here because snyk runs build.gradle who needs them
        script.withEnv(envVariables) {
            noVulnerabilitiesFound = snyk.test(config.organisation, config.buildFile, config.severityThreshold)
            if (noVulnerabilitiesFound) {
                script.echo 'No vulnerabilities detected.'
            } else {
                script.echo 'Snyk test detected vulnerabilities.'
            }
        }

        if (!snyk.monitor(config.organisation, config.buildFile)) {
            script.error 'Snyk monitor failed'
        }

        generateAndArchiveReport(context.localCheckoutEnabled)

        if (!noVulnerabilitiesFound && config.failOnVulnerabilities) {
            script.error 'Snyk scan stage failed. See snyk report for details.'
        }
    }

    private generateAndArchiveReport(boolean archive) {
        def targetReport = "SCSR-${context.projectId}-${componentId}-${snyk.reportFile}"
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
        def snykScanStashPath = "scsr-report-${componentId}-${context.buildNumber}"
        context.addArtifactURI("snykScanStashPath", snykScanStashPath)

        script.stash(
            name: "${snykScanStashPath}",
            includes: 'artifacts/SCSR*',
            allowEmpty: true
        )
        context.addArtifactURI('SCSR', targetReport)
    }
}
