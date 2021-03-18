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
        // name of the credentials that stores the username/password of
        // a user with access to the Aqua server identified by "aquaUrl".
        if (!config.credentialsId) {
            config.credentialsId = context.projectId + '-cd-aqua'
        }
        // BuildOpenShiftImageStage puts the imageRef into a map with the
        // resourceName as key, to get the imageRef we need this key
        if (!config.resourceName) {
            config.resourceName = context.componentId
        }
        this.aqua = aqua
        this.bitbucket = bitbucket
    }

    protected run() {
        if (!isEligibleBranch(config.eligibleBranches, context.gitBranch)) {
            logger.info "Skipping as branch '${context.gitBranch}' is not covered by the 'branch' option."
            return
        }
        // base URL of Aqua server.
        if (!config.aquaUrl) {
            script.error "Please provide the URL of the Aqua platform!"
        }
        // name in Aqua of the registry that contains the image we want to scan
        if (!config.registry) {
            script.error "Please provide the name of the registry that contains the image of interest!"
        }

        String imageRef = getImageRef()
        String reportFile = "aqua-report.json"
        scanViaCli(config.aquaUrl, config.registry, imageRef, config.credentialsId, reportFile)
        createBitbucketCodeInsightReport(config.aquaUrl, config.registry, imageRef, reportFile)
        archiveReport(context.localCheckoutEnabled, reportFile)
    }

    private String getImageRef() {
        // take the image ref of the image that is being build in the image build stage
        Map<String, String> buildInfo = context.getBuildArtifactURIs().builds.get(config.resourceName)
        if (buildInfo) {
            String imageRef = buildInfo.image
            return imageRef.substring(imageRef.indexOf("/") + 1)
        } else {
            // if no imageRef could be received, take latest image, e.g. "foo-test/be-bar:latest"
            logger.warn("imageRef could not be retrieved - please ensure this Aqua stage runs " +
                "after the image build stage and that they both have the same resourceName " +
                "value set! Continuing with " + config.projectName + ":latest ...")
            // TODO currently hardcoded to 'dev' as environment since config.environment is null here?
            return config.organisation + "-" + "dev" + "/" + config.projectName + ":latest"
        }
    }

    private scanViaCli(String aquaUrl, String registry, String imageRef, String credentialsId, String reportFile) {
        logger.startClocked(config.projectName)
        aqua.scanViaCli(aquaUrl, registry, imageRef, credentialsId, reportFile)
        logger.infoClocked(config.projectName, "Aqua scan (via CLI)")
    }

    private createBitbucketCodeInsightReport(String aquaUrl, String registry, String imageRef, String reportFile) {
        String aquaScanUrl = aquaUrl + "/#/images/" + registry + "/" + imageRef.replace("/", "%2F") + "/vulns"
        String title = "Aqua Security"
        String details = "Please visit the following link to review the Aqua Security scan report:"
        // for now, we set the result always to successful in the aqua stage
        String result = "PASS"
        bitbucket.createCodeInsightReport(aquaScanUrl, context.repoName, context.gitCommit, title, details, result)
    }

    private archiveReport(boolean archive, String reportFile) {
        String targetReport = "SCSR-${context.projectId}-${context.componentId}-${reportFile}"
        script.sh(
            label: 'Create artifacts dir',
            script: 'mkdir -p artifacts'
        )
        script.sh(
            label: 'Rename report to SCSR',
            script: "mv ${reportFile} artifacts/${targetReport}"
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
