package org.ods.component

import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode
import org.ods.services.BitbucketService
import org.ods.services.InfrastructureService
import org.ods.util.ILogger
import org.ods.util.GitCredentialStore

@TypeChecked
class InfrastructureStage extends Stage {

    static final String STAGE_NAME = 'Infrastructure as Code'
    private final BitbucketService bitbucket

    private final InfrastructureService infrastructure
    private final InfrastructureOptions options

    private final Boolean stackdeploy = false

    private final String workspaceKeyPrefix = ''

    private final String awsAccessKeyId = ''
    private final String awsSecretAccessKey = ''
    private final String awsRegion = ''

    private final String awsAccessKeyIdTesting = ''
    private final String awsSecretAccessKeyTesting = ''
    private final String awsRegionTesting = ''

    private final String tfBackendS3Key = ''
    private final Map tfvarsjson = null

    // private final Object environmentFile = Object()
    // private final Object environmentFileTesting = Object()



    @SuppressWarnings('ParameterCount')
    @TypeChecked(TypeCheckingMode.SKIP)
    InfrastructureStage(def script, IContext context, Map config, InfrastructureService infrastructure,
                        BitbucketService bitbucket, ILogger logger) {
        super(script, context, logger)
        this.infrastructure = infrastructure
        this.bitbucket = bitbucket
        if (!config.resourceName) {
            config.resourceName = context.componentId
        }
        if (!config.envPath) {
            config.envPath = "./environments"
        }
        this.options = new InfrastructureOptions(config)
        this.stackdeploy = false

        // TODO CHECK FILES EXISTS
        if (context.environment) {
            environmentFile         = readYaml file: "${options.envPath}/${context.environment}.yml"
            environmentFileTesting  = readYaml file: "${options.envPath}/testing.yml"
            this.stackdeploy = true

            this.awsAccessKeyId      = environmentFile.credentials.key.toLowerCase()
            this.awsSecretAccessKey  = environmentFile.credentials.secret.toLowerCase()
            this.awsRegion           = environmentFile.region.toLowerCase()

            this.awsAccessKeyIdTesting     = environmentFileTesting.credentials.key.toLowerCase()
            this.awsSecretAccessKeyTesting = environmentFileTesting.credentials.secret.toLowerCase()
            this.awsRegionTesting          = environmentFileTesting.region.toLowerCase()

            this.workspaceKeyPrefix        = environmentFile.account
            this.tfBackendS3Key            = "${environmentFile.account}/${context.projectId}/${context.componentId}/${context.environment}"

            // handle environment specific variables
            // copy json from ${options.envPath}/${context.environment}.auto.tfvars.json to /
            if (fileExists("${options.envPath}/${context.environment}.json")) {
                withEnv(["VARIABLESFILE=${options.envPath}/${context.environment}.json"])
                {
                    def statusVarEnv = sh(script: '''
                        cp $VARIABLESFILE env.auto.tfvars.json && echo "Variables file $VARIABLESFILE" ''',
                    returnStatus: true)
                    if (statusVarEnv != 0) {
                        error "Can not copy json file!"
                    }
                }
            }

            bitbucket.withTokenCredentials { String username, String pw ->
                GitCredentialStore.configureAndStore(this, context.bitbucketUrl, username, pw)
            }

            def status = runMake("create-tfvars", null, null, null, null, null, null)
            if (status != 0) {
                error "Creation of tfvars failed!"
            }
            this.tfvarsjson = readJSON file: '${options.envPath}/terraform.tfvars.json'
        }

    }

    protected run() {
        String errorMessages = ''

        int returnTCode = runMake("test", awsAccessKeyIdTesting, awsSecretAccessKeyTesting, workspaceKeyPrefix, tfBackendS3Key, null, awsRegionTesting)
        // if (![AquaService.AQUA_SUCCESS, AquaService.AQUA_POLICIES_ERROR].contains(returnCode)) {
        //     errorMessages += "<li>Error executing Aqua CLI</li>"
        // }
        // If stackdeploy
        if (stackdeploy) {
            try {
                int returnPCode = runMake("plan", awsAccessKeyId, awsSecretAccessKey, workspaceKeyPrefix, tfBackendS3Key, tfvarsjson['meta_environment'], awsRegion)
                int returnDCode = runMake("deploy", awsAccessKeyId, awsSecretAccessKey, workspaceKeyPrefix, tfBackendS3Key, tfvarsjson['meta_environment'], awsRegion)
                int returnSCode = runMake("smoke-test", awsAccessKeyId, awsSecretAccessKey, workspaceKeyPrefix, tfBackendS3Key, tfvarsjson['meta_environment'], awsRegion)
                int returnRCode = runMake("install-report", null, null, null, null, null, null)
                // TODO HANDLE ERROR CASES AND MAYBE STASH REPORTS
                stash(name: "installation-test-reports-junit-xml-${context.componentId}-${context.buildNumber}", includes: 'build/test-results/test/default.xml' , allowEmpty: true)
                stash(name: "changes-${context.componentId}-${context.buildNumber}", includes: 'reports/install/tf_created.log' , allowEmpty: true)
                stash(name: "target-${context.componentId}-${context.buildNumber}", includes: 'reports/install/aws_deploy_account.log' , allowEmpty: true)
                stash(name: "state-${context.componentId}-${context.buildNumber}", includes: 'reports/install/tf_show.log' , allowEmpty: true)
            } catch (err) {
                logger.warn("Error archiving the Aqua reports due to: ${err}")
                errorMessages += "<li>Error archiving Aqua reports</li>"
            }
        } else {
            // createBitbucketCodeInsightReport(errorMessages)
        }

        // notifyAquaProblem(alertEmails, errorMessages)
        return
    }

    @SuppressWarnings('ParameterCount')
    private int runMake(String rule, String awsAccessKeyId, String awsSecretAccessKey, String tfBackendPrefix, String tfBackendS3Key, String workspace, String region) {
        logger.startClocked(options.resourceName)
        int returnCode = infrastructure.runMake(rule, awsAccessKeyId, awsSecretAccessKey, tfBackendPrefix, tfBackendS3Key, workspace, region)

        switch (returnCode) {
            // case AquaService.AQUA_SUCCESS:
            //     logger.info "Finished scan via Aqua CLI successfully!"
            //     break
            // case AquaService.AQUA_OPERATIONAL_ERROR:
            //     logger.info "An error occurred in processing the scan request " +
            //         "(e.g. invalid command line options, image not pulled, operational error)."
            //     break
            // case AquaService.AQUA_POLICIES_ERROR:
            //     logger.info "The image scanned failed at least one of the Image Assurance Policies specified."
            //     break
            default:
                logger.info "An unknown return code was returned: ${returnCode}"
        }
        logger.infoClocked(options.resourceName, "Infrastructure as Code (via Make)")
        return returnCode
    }

}
