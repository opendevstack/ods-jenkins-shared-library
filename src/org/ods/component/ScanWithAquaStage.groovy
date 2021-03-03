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
        if (config.scanMode) {
            config.scanMode = config.scanMode.trim().toLowerCase()
        } else {
            config.scanMode = 'cli'
        }
        // Name of the credentials which stores the username/password of
        // a user with access to the Aqua server identified by "aquaUrl".
        if (!config.credentialsId) {
            config.credentialsId = context.projectId + '-cd-aqua'
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
        // Base URL of Aqua server.
        if (!config.aquaUrl) {
            script.error "Please provide the URL of the Aqua platform!"
        }
        // Name in Aqua of the registry that contains the image we want to scan
        if (!config.registry) {
            script.error "Please provide the name of the registry that contains the image of interest!"
        }

        // TODO: improve receiving of image reference
        // take exact image ref
        //String imageRef = context.getBuildArtifactURIs().get("OCP Docker image").substring(imageRef.indexOf("/") + 1)
        // take latest image, e.g. "aqua-test/be-spring-aqua:latest"
        String imageRef = config.organisation + "//" + config.projectName + ":latest"

        switch (config.scanMode) {
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

    private scanViaApi(String aquaUrl, String registry, String imageRef, String credentialsId) {
        String token = aqua.getApiToken(aquaUrl, credentialsId)
        logger.startClocked(config.projectName, "start of aqua scan (via API)")
        logger.info aqua.scanViaApi(aquaUrl, registry, token, imageRef)
        // TODO: pipe the json result into ${reportFile}
        logger.info aqua.retrieveScanResultViaApi(aquaUrl, registry, token, imageRef)
        logger.debugClocked(config.projectName, "end of aqua scan (via API)")
    }

    private scanViaCli(String aquaUrl, String registry, String imageRef, String credentialsId) {
        logger.startClocked(config.projectName, "start of aqua scan (via CLI)")
        aqua.scanViaCli(aquaUrl, registry, imageRef, credentialsId)
        logger.debugClocked(config.projectName, "start of aqua scan (via CLI)")
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
