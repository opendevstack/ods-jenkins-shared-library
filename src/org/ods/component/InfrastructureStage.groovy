package org.ods.component

import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode
import org.ods.services.InfrastructureService
import org.ods.util.ILogger

@TypeChecked
class InfrastructureStage extends Stage {

    static final String STAGE_NAME = 'AWS Infrastructure as Code (IaC)'
    private final def script

    private final InfrastructureService infrastructure
    private final InfrastructureOptions options

    private final Boolean stackDeploy
    private final String tfBackendS3Key
    private final Map tfVars
    private final Map environmentVars
    private final Map environmentVarsTesting

    @TypeChecked(TypeCheckingMode.SKIP)
    @SuppressWarnings('ParameterCount')
    InfrastructureStage(def script, IContext context, Map config,
                        InfrastructureService infrastructure, ILogger logger,
                        def environmentVars, def environmentVarsTesting, def tfVars) {
        super(script, context, logger)

        this.script = script
        this.options = new InfrastructureOptions(config)
        this.infrastructure = infrastructure
        this.stackDeploy = true
        this.environmentVarsTesting = environmentVarsTesting
        this.environmentVars = null
        this.tfVars = null
        this.tfBackendS3Key = null

        if (!context.environment) {
            this.stackDeploy = false
        }
        if (this.stackDeploy) {
            this.environmentVars = environmentVars
            this.tfBackendS3Key =
                "${environmentVars.account}/${context.projectId}/${context.componentId}/${context.environment}"
            this.tfVars = tfVars
        }
    }

    @TypeChecked(TypeCheckingMode.SKIP)
    protected run() {
        if (runMakeWithEnv("test", environmentVarsTesting, tfBackendS3Key, null as String) != 0) {
            script.error("AWS IaC - Testing stage failed!")
        }
        if (stackDeploy) {
            if (runMakeWithEnv("plan", environmentVars, tfBackendS3Key, tfVars['meta_environment'] as String) != 0) {
                script.error("AWS IaC - Plan stage failed!")
            }
            if (runMakeWithEnv("deploy", environmentVars, tfBackendS3Key, tfVars['meta_environment'] as String) != 0) {
                script.error("AWS IaC - Deploy stage failed!")
            }
            if (runMakeWithEnv("smoke-test",
                              environmentVars, tfBackendS3Key, tfVars['meta_environment'] as String) != 0) {
                script.error("AWS IaC - Smoke-test stage failed!")
            }
            if (runMake("install-report") != 0) {
                script.error("AWS IaC - Report stage failed!")
            }

            script.stash(
                name: "installation-test-reports-junit-xml-${context.componentId}-${context.buildNumber}",
                includes: 'build/test-results/test/default.xml',
                allowEmpty: true)
            script.stash(
                name: "changes-${context.componentId}-${context.buildNumber}",
                includes: 'reports/install/tf_created.log',
                allowEmpty: true)
            script.stash(
                name: "target-${context.componentId}-${context.buildNumber}",
                includes: 'reports/install/aws_deploy_account.log',
                allowEmpty: true)
            script.stash(
                name: "state-${context.componentId}-${context.buildNumber}",
                includes: 'reports/install/tf_show.log',
                allowEmpty: true)
        }
        return
    }

    private int runMake(String rule) {
        logger.startClocked(options.resourceName)
        int returnCode = infrastructure.runMake(rule)
        logResult(rule, returnCode)
        logger.infoClocked(options.resourceName, "AWS Infrastructure as Code (via Makefile)")
        return returnCode
    }

    private int runMakeWithEnv(String rule, Map environmentVars, String tfBackendS3Key, String workspace) {
        logger.startClocked(options.resourceName)
        int returnCode = infrastructure.runMakeWithEnv(rule, environmentVars, tfBackendS3Key, workspace)
        logResult(rule, returnCode)
        logger.infoClocked(options.resourceName, "AWS Infrastructure as Code (via Makefile)")
        return returnCode
    }

    private logResult(String rule, int returnCode) {
        switch (returnCode) {
            case 0:
                logger.info "Finished AWS IaC make ${rule} successfully!"
                break
            case 1:
                logger.info "AWS IaC make ${rule} failed. See logs for further information."
                break
            default:
                logger.info "AWS IaC make ${rule} unknown return code: ${returnCode}"
        }
    }

}
