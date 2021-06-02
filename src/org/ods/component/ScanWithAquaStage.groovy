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
    static final String AQUA_CONFIG_MAP_NAME = "aqua"
    static final String AQUA_GENERAL_CONFIG_MAP_PROJECT = "ods"
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
        Map configurationAquaCluster = openShift.getConfigMapData(AQUA_GENERAL_CONFIG_MAP_PROJECT, AQUA_CONFIG_MAP_NAME)
        Map configurationAquaProject = openShift.getConfigMapData(context.cdProject, AQUA_CONFIG_MAP_NAME)
        String errorMessages = ''

        if (!configurationAquaProject.containsKey('enabled')) {
            // If not exist key, is enabled
            configurationAquaProject.put('enabled', true)
            logger.info "Not parameter 'enabled' at project level. Default enabled"
        }
        // addresses form Aqua advises mails.
        String alertEmails = configurationAquaCluster['alertEmails']
        if (!alertEmails) {
            logger.info "Please provide the alert emails of the Aqua platform!"
        }

        // base URL of Aqua server.
        String url = configurationAquaCluster['url']
        if (!url) {
            logger.info "Please provide the URL of the Aqua platform!"
            errorMessages += "<li>Provide the Aqua url of platform</li>"
        }
        // name in Aqua of the registry that contains the image we want to scan
        String registry = configurationAquaCluster['registry']
        if (!registry) {
            logger.info "Please provide the name of the registry that contains the image of interest!"
            errorMessages += "<li>Provide the name of the registry to use in Aqua</li>"
        }
        // name of the credentials that stores the username/password of a user with access
        // to the Aqua server identified by "aquaUrl", defaults to the cd-user
        String secretName = configurationAquaCluster['secretName']
        String credentialsId = context.cdProject + "-"
        if (!secretName) {
            credentialsId = context.credentialsId
            logger.info("No custom secretName was specified in the aqua ConfigMap, continuing with default " +
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
            errorMessages += "<li>Skipping as imageRef could not be retrieved</li>"
            notifyAquaProblem(alertEmails, errorMessages)
            return
        }

        boolean enabledInCluster = Boolean.valueOf(configurationAquaCluster['enabled'].toString())
        boolean enabledInProject = Boolean.valueOf(configurationAquaProject['enabled'].toString())
        if (enabledInCluster && enabledInProject) {
            String reportFile = "aqua-report.html"
            int returnCode = scanViaCli(url, registry, imageRef, credentialsId, reportFile)
            if (AquaService.AQUA_SUCCESS != returnCode) {
                errorMessages += "<li>Error executing Aqua CLI</li>"
            }
            // If report exists
            if ([AquaService.AQUA_SUCCESS, AquaService.AQUA_POLICIES_ERROR].contains(returnCode)) {
                createBitbucketCodeInsightReport(url, registry, imageRef, returnCode)
                archiveReport(!context.triggeredByOrchestrationPipeline, reportFile)
            } // TODO errors in BB y Reports
        } else {
            def message = ''
            if(!enabledInCluster && !enabledInProject) {
                message = "Skipping Aqua scan because is not enabled nor cluster " +
                    "in ${AQUA_GENERAL_CONFIG_MAP_PROJECT} project, nor project level in 'aqua' ConfigMap"
            } else if (enabledInCluster) {
                message =  "Skipping Aqua scan because is not enabled at project level in 'aqua' ConfigMap"
            } else {
                message "Skipping Aqua scan because is not enabled at cluster level in 'aqua' " +
                    "ConfigMap in ${AQUA_GENERAL_CONFIG_MAP_PROJECT} project"
            }
            logger.info message
            errorMessages += "<li>${message}</li>"
        }
        notifyAquaProblem(alertEmails, errorMessages)
        return
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

    private int scanViaCli(String aquaUrl, String registry, String imageRef, String credentialsId, String reportFile) {
        logger.startClocked(options.resourceName)
        int returnCode = aqua.scanViaCli(aquaUrl, registry, imageRef, credentialsId, reportFile)
        // see possible return codes at https://docs.aquasec.com/docs/scanner-cmd-scan#section-return-codes
        switch (returnCode) {
            case AquaService.AQUA_SUCCESS:
                logger.info "Finished scan via Aqua CLI successfully!"
                break
            case AquaService.AQUA_OPERATIONAL_ERROR:
                logger.info "An error occurred in processing the scan request " +
                    "(e.g. invalid command line options, image not pulled, operational error)."
                break
            case AquaService.AQUA_POLICIES_ERROR:
                logger.info "The image scanned failed at least one of the Image Assurance Policies specified."
                break
            default:
                logger.info "An unknown return code was returned: ${returnCode}"
        }
        logger.infoClocked(options.resourceName, "Aqua scan (via CLI)")
        return returnCode
    }

    private createBitbucketCodeInsightReport(String aquaUrl, String registry, String imageRef, int returnCode) {
        String aquaScanUrl = aquaUrl + "/#/images/" + registry + "/" + imageRef.replace("/", "%2F") + "/vulns"
        String title = "Aqua Security"
        String details = "Please visit the following link to review the Aqua Security scan report:"
        // for now, we set the result always to successful in the aqua stage
        String result = returnCode == 0 ? "PASS" : "FAIL"
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

    private void notifyAquaProblem(String recipients = '', String message = '') {
        String subject = "Build $context.componentId on project $context.projectId had some problems with Aqua!"
        String body = "<p>$subject</p> <p>URL : <a href=\"$context.buildUrl\">$context.buildUrl</a></p> <ul>$message</ul>"

        if (message) {
            script.emailext(
                body: body, mimeType: 'text/html',
                replyTo: '$script.DEFAULT_REPLYTO', subject: subject,
                to: recipients
            )
        }
    }
}
