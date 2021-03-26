package org.ods.component

import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode
import org.ods.services.AquaService
import org.ods.services.BitbucketService
import org.ods.services.OpenShiftService
import org.ods.util.ILogger

@TypeChecked
class ScanWithAquaStage extends Stage {

    public final String STAGE_NAME = 'Aqua Security Scan'
    private final AquaService aqua
    private final BitbucketService bitbucket
    private final OpenShiftService openShift
    private final ScanWithAquaOptions options

    @SuppressWarnings('ParameterCount')
    @TypeChecked(TypeCheckingMode.SKIP)
    ScanWithAquaStage(def script, IContext context, Map config, AquaService aqua, BitbucketService bitbucket,
                      OpenShiftService openShift, ILogger logger) {
        super(script, context, logger)
        if (!config.resourceName) {
            config.resourceName = context.componentId
        }
        this.options = new ScanWithAquaOptions(config)
        this.aqua = aqua
        this.bitbucket = bitbucket
        this.openShift = openShift
    }

    protected run() {
        Map connectionData = openShift.getConfigMapData(context.cdProject, "aqua")
        // base URL of Aqua server.
        String aquaUrl = connectionData['aqua.url']
        if (!aquaUrl) {
            steps.error "Please provide the URL of the Aqua platform!"
        }
        // name in Aqua of the registry that contains the image we want to scan
        String registry = connectionData['aqua.registry']
        if (!registry) {
            steps.error "Please provide the name of the registry that contains the image of interest!"
        }
        // name of the credentials that stores the username/password of a user with access
        // to the Aqua server identified by "aquaUrl", defaults to the cd-user
        String secretName = connectionData['aqua.secretName']
        String credentialsId = context.cdProject + "-"
        if (!secretName) {
            credentialsId = context.credentialsId
            logger.info("No custom secretName was specified, continuing with default " +
                "credentialsId '" + credentialsId + "'...")
        } else {
            credentialsId += secretName
        }
        // reference of the image that was build in this pipeline run
        String imageRef = getImageRef()
        if (!imageRef) {
            logger.info "Skipping as imageRef could not be retrieved. Possible reasons are:\n" +
                "-> The aqua stage runs before the image build stage and hence no new image was created yet.\n" +
                "-> The image build stage was not executed because the image was imported.\n" +
                "-> The aqua stage and the image build stage have different values for 'resourceName' set."
            return
        }

        String reportFile = "aqua-report.html"
        scanViaCli(aquaUrl, registry, imageRef, credentialsId, reportFile)
        createBitbucketCodeInsightReport(aquaUrl, registry, imageRef)
        archiveReport(!context.triggeredByOrchestrationPipeline, reportFile)
    }

    private String getImageRef() {
        // take the image ref of the image that is being build in the image build stage
        Map<String, String> buildInfo =
            context.getBuildArtifactURIs().builds[options.resourceName] as Map<String, String>
        if (buildInfo) {
            String imageRef = buildInfo.image
            return imageRef.substring(imageRef.indexOf("/") + 1)
        }
        return null
    }

    private scanViaCli(String aquaUrl, String registry, String imageRef, String credentialsId, String reportFile) {
        logger.startClocked(options.resourceName)
        aqua.scanViaCli(aquaUrl, registry, imageRef, credentialsId, reportFile)
        logger.infoClocked(options.resourceName, "Aqua scan (via CLI)")
    }

    private createBitbucketCodeInsightReport(String aquaUrl, String registry, String imageRef) {
        String aquaScanUrl = aquaUrl + "/#/images/" + registry + "/" + imageRef.replace("/", "%2F") + "/vulns"
        String title = "Aqua Security"
        String details = "Please visit the following link to review the Aqua Security scan report:"
        // for now, we set the result always to successful in the aqua stage
        String result = "PASS"
        bitbucket.createCodeInsightReport(aquaScanUrl, context.repoName, context.gitCommit, title, details, result)
    }

    private archiveReport(boolean archive, String reportFile) {
        String targetReport = "SCSR-${context.projectId}-${context.componentId}-${reportFile}"
        steps.sh(
            label: 'Create artifacts dir',
            script: 'mkdir -p artifacts'
        )
        steps.sh(
            label: 'Rename report to SCSR',
            script: "mv ${reportFile} artifacts/${targetReport}"
        )
        if (archive) {
            steps.archiveArtifacts(artifacts: 'artifacts/SCSR*')
        }
        String aquaScanStashPath = "scsr-report-${context.componentId}-${context.buildNumber}"
        context.addArtifactURI('aquaScanStashPath', aquaScanStashPath)

        steps.stash(
            name: "${aquaScanStashPath}",
            includes: 'artifacts/SCSR*',
            allowEmpty: true
        )
        context.addArtifactURI('SCSR', targetReport)
    }

}
