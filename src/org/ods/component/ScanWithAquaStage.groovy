package org.ods.component

import org.ods.services.AquaService
import org.ods.services.BitbucketService
import org.ods.util.ILogger

class ScanWithAquaStage extends Stage {

    public final String STAGE_NAME = 'Aqua Security Scan'
    private final AquaService aqua
    private final BitbucketService bitbucket

    @SuppressWarnings('ParameterCount')
    ScanWithAquaStage(def script, IContext context, Map config, AquaService aqua, BitbucketService bitbucket,
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
        // TODO: find better name for scanMode
        if (!config.scanMode) {
            config.scanMode = 'cli'
        } else {
            config.scanMode = config.scanMode.trim().toLowerCase()
        }
        this.aqua = aqua
        this.bitbucket = bitbucket
    }

    protected run() {
        if (!isEligibleBranch(config.eligibleBranches, context.gitBranch)) {
            logger.info "Skipping as branch '${context.gitBranch}' is not covered by the 'branch' option."
            return
        }

        def possibleScanModes = ['cli', 'api']
        if (!possibleScanModes.contains(config.scanMode)) {
            script.error "'${config.scanMode}' is not a valid value " +
                "for option 'scanMode'! Please use one of ${possibleScanModes}."
        }

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
        }

        updateBitbucketBuildStatusForCommit()
        archiveReport(context.localCheckoutEnabled)
    }

    private scanViaApi(String imageRef) {
        String token = aqua.getApiToken()
        logger.startClocked("${config.projectName}-aqua-scan")
        logger.info aqua.scanViaApi(token, imageRef)
        logger.debugClocked("${config.projectName}-aqua-scan")

        logger.startClocked("${config.projectName}-aqua-scan")
        // TODO: pipe the json result into ${reportFile}
        logger.info aqua.retrieveScanResultViaApi(token, imageRef)
        logger.debugClocked("${config.projectName}-aqua-scan")
    }

    private scanViaCli(String imageRef) {
        logger.startClocked("${config.projectName}-aqua-scan")
        logger.info aqua.scanViaCli(imageRef)
        logger.debugClocked("${config.projectName}-aqua-scan")
    }

    private updateBitbucketBuildStatusForCommit() {
        // for now, we set the build status always to successful in the aqua stage
        def state = "SUCCESSFUL"
        def buildName = "${context.gitCommit.take(8)}"
        // TODO: improve description and change context.buildUrl to aqua scan url
        def description = "Build status from Aqua Security stage!"
        bitbucket.setBuildStatus(context.buildUrl, context.gitCommit, state, buildName, description)
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
