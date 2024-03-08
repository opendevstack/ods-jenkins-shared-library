package org.ods.component

import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode
import org.ods.services.InfrastructureService
import org.ods.util.ILogger

@TypeChecked
class InfrastructureStage extends Stage {

    static final String STAGE_NAME = 'Infrastructure as Code (IaC)'
    private final def script

    private final InfrastructureService infrastructure
    private final InfrastructureOptions options

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
        this.environmentVarsTesting = environmentVarsTesting
        this.environmentVars = null
        this.tfVars = null
        this.tfBackendS3Key = null

        if (!!context.environment) {
            this.environmentVars = environmentVars
            this.tfBackendS3Key =
                "${environmentVars.account}/${context.projectId}/${context.componentId}/${context.environment}"
            this.tfVars = tfVars
        }
    }

    // called from odsComponentStageInfrustructure execute
    @TypeChecked(TypeCheckingMode.SKIP)
    protected run() {
        if (!context.environment) {
            logger.warn('Skipping image import because of empty (target) environment ...')
            return
        }
        if (runMakeStage("test", this.options.cloudProvider,
                          environmentVarsTesting, tfBackendS3Key, null as String) != 0) {
            script.error("IaC - Testing stage failed!")
        }
        if (!!context.environment) {
            if (runMakeStage("plan", this.options.cloudProvider,
                              environmentVars, tfBackendS3Key, tfVars['meta_environment'] as String) != 0) {
                script.error("IaC - Plan stage failed!")
            }
            if (runMakeStage("deploy", this.options.cloudProvider,
                              environmentVars, tfBackendS3Key, tfVars['meta_environment'] as String) != 0) {
                script.error("IaC - Deploy stage failed!")
            }
            if (runMakeStage("deployment-test", this.options.cloudProvider,
                              environmentVars, tfBackendS3Key, tfVars['meta_environment'] as String) != 0) {
                script.error("IaC - Deployment-Test stage failed!")
            }
            if (runMakeStage("install-report", this.options.cloudProvider, [:], null as String, null as String) != 0) {
                script.error("IaC - Report stage failed!")
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

    @TypeChecked(TypeCheckingMode.SKIP)
    private int runMakeStage(
                    String rule,
                    String cloudProvider,
                    Map environmentVars,
                    String tfBackendS3Key,
                    String workspace) {
        script.stage("${cloudProvider} IaC - ${rule}") {
            logger.startClocked(options.resourceName)
            int returnCode = infrastructure.runMake(rule, environmentVars, tfBackendS3Key, workspace)
            logResult(rule, returnCode)
            logger.infoClocked(options.resourceName, "Infrastructure as Code (via Makefile)")
            return returnCode
        }
    }

    private logResult(String rule, int returnCode) {
        switch (returnCode) {
            case 0:
                logger.info "Finished IaC make ${rule} successfully!"
                break
            case 1:
                logger.info "IaC make ${rule} failed. See logs for further information."
                break
            default:
                logger.info "IaC make ${rule} unknown return code: ${returnCode}"
        }
    }

}
