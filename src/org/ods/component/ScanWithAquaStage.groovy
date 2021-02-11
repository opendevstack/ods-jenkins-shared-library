package org.ods.component

import org.ods.services.AquaService
import org.ods.util.ILogger

class ScanWithAquaStage extends Stage {

    public final String STAGE_NAME = 'Aqua Security Scan'
    private final AquaService aqua

    ScanWithAquaStage(def script, IContext context, Map config, AquaService aqua,
                      ILogger logger) {
        super(script, context, config, logger)
        if (!config.organisation) {
            config.organisation = context.projectId
        }
        if (!config.projectName) {
            config.projectName = context.componentId
        }
        if (config.branch) {
            config.eligibleBranches = config.branch.split(',')
        } else {
            config.eligibleBranches = ['*']
        }
        // TODO: find better name than scanMode
        if (!config.scanMode) {
            config.scanMode = 'cli'
        } else {
            config.scanMode = config.scanMode.trim().toLowerCase()
        }
        /*
        if (!config.containsKey('failOnVulnerabilities')) {
            config.failOnVulnerabilities = context.failOnSnykScanVulnerabilities
        }
         */
        /*
        if (config.severityThreshold) {
            config.severityThreshold = config.severityThreshold.trim().toLowerCase()
        } else {
            // low is the default, it is equal to not providing the option to snyk
            config.severityThreshold = 'low'
        }
         */
        this.aqua = aqua
    }

    protected run() {
        if (!isEligibleBranch(config.eligibleBranches, context.gitBranch)) {
            logger.info "Skipping as branch '${context.gitBranch}' is not covered by the 'branch' option."
            return
        }
        // TODO: find better name for 'checkApiQueueBeforeCli'
        def possibleScanModes = ['cli', 'api', 'checkapiqueuebeforecli']
        if (!possibleScanModes.contains(config.scanMode)) {
            script.error "'${config.scanMode}' is not a valid value " +
                "for option 'scanMode'! Please use one of ${possibleScanModes}."
        }

        // TODO: check possible severity thresholds for aqua
        /*
        def allowedSeverityThresholds = ['low', 'medium', 'high']
        if (!allowedSeverityThresholds.contains(config.severityThreshold)) {
            script.error "'${config.severityThreshold}' is not a valid value " +
                "for option 'severityThreshold'! Please use one of ${allowedSeverityThresholds}."
        }
         */

        // TODO: improve receiving of image reference
        // take exact image ref
        //String imageRef = context.getBuildArtifactURIs().get("OCP Docker image").substring(imageRef.indexOf("/") + 1)
        // take latest image, e.g. "aqua-test/be-spring-aqua:latest"
        String imageRef = config.organisation + "//" + config.projectName + ":latest"


        switch(config.scanMode) {
            case 'api':
                scanViaApi(imageRef)
                break
            case 'cli':
                scanViaCli(imageRef)
                break
            case 'checkapiqueuebeforecli':
                // TODO: not implemented yet, idea would be to first check queue
                // of ongoing api scans and if to loaded, to it via cli..
                // int queueCount = aqua.retrieveQueueCount()
                // if (queueCount <= 5) {
                //     scanViaApi(imageRef)
                // } else {
                //     scanViaCli(imageRef)
                // }
                break
        }

        archiveReport(context.localCheckoutEnabled)

        // TODO: check options for aqua to let pipeline stop when vulnerabilities were found
        /*
        if (!noVulnerabilitiesFound && config.failOnVulnerabilities) {
            script.error 'Snyk scan stage failed. See snyk report for details.'
        }
         */
    }

    private scanViaApi(String imageRef) {
        String token = aqua.getApiToken()
        logger.startClocked("${config.projectName}-aqua-scan")
        // TODO: consider adding severity thresholds to stop pipeline or send warning etc.
        logger.info aqua.scanViaApi(token, imageRef)
        logger.debugClocked("${config.projectName}-aqua-scan")

        logger.startClocked("${config.projectName}-aqua-scan")
        // TODO: consider piping the json result into a report file to be able to upload it to other places
        logger.info aqua.retrieveScanResultViaApi(token, imageRef)
        logger.debugClocked("${config.projectName}-aqua-scan")
    }

    private scanViaCli(String imageRef) {
        logger.startClocked("${config.projectName}-aqua-scan")
        // TODO: consider adding severity thresholds to stop pipeline or send warning etc.
        logger.info aqua.scanViaCli(imageRef)
        logger.debugClocked("${config.projectName}-aqua-scan")
    }

    private archiveReport(boolean archive) {
        String targetReport = "SCSR-${context.projectId}-${context.componentId}-${aqua.reportFile}"
        script.sh(
            label: 'Create artifacts dir',
            script: 'mkdir -p artifacts/SCSR'
        )
        script.sh(
            label: 'Rename report to SCSR',
            script: "mv ${aqua.reportFile} artifacts/${targetReport}"
        )
        if (archive) {
            script.archiveArtifacts(artifacts: 'artifacts/SCSR*')
        }
        String aquaScanStashPath = "scsr-report-${context.componentId}-${context.buildNumber}"
        context.addArtifactURI('aquaScanStashPath', aquaScanStashPath)

        script.stash(
            name: "${aquaScanStashPath}",
            includes: 'artifacts/SCSR*',
            allowEmpty: true
        )
        context.addArtifactURI('SCSR', targetReport)
    }

}
