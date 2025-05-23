package org.ods.orchestration

import org.ods.orchestration.scheduler.LeVADocumentScheduler
import org.ods.orchestration.usecase.JiraUseCase
import org.ods.orchestration.util.MROPipelineUtil
import org.ods.orchestration.util.PipelinePhaseLifecycleStage
import org.ods.orchestration.util.Project
import org.ods.services.ServiceRegistry
import org.ods.util.ILogger
import org.ods.util.IPipelineSteps
import org.ods.util.Logger

import util.PipelineSteps
import util.SpecHelper

import static util.FixtureHelper.createProject

class BuildStageSpec extends SpecHelper {

    static String TAILOR_FAILURE_LOG_MESSAGE = """Delivery failed since the following Bitbucket repositories contain errors:

Repository: net-golang
Branch: master

Repository: net-other
Branch: master

Repository: net-third
Branch: master

ERROR: We detected an undesired configuration drift. A drift occurs when changes in a target environment are not covered by configuration files in Git (regarded as the source of truth). Resulting differences may be due to manual changes in the configuration of the target environment or automatic changes performed by OpenShift/Kubernetes.

We found drifts for the following components: "golang", "third".

Please follow these steps to resolve and restart your deployment:

\t1. See the logs above to review the differences we found.
\t2. Please update your configuration stored in Bitbucket or the configuration in the target environment as needed so that they match."""

    static String TAILOR_FAILURE_JIRA_COMMENT = """Delivery failed since the following Bitbucket repositories contain errors:

Repository: net-golang
Branch: master

Repository: net-other
Branch: master

Repository: net-third
Branch: master

ERROR: We detected an undesired configuration drift. A drift occurs when changes in a target environment are not covered by configuration files in Git (regarded as the source of truth). Resulting differences may be due to manual changes in the configuration of the target environment or automatic changes performed by OpenShift/Kubernetes.

We found drifts for the following components: "golang", "third".

Please follow these steps to resolve and restart your deployment:

\t1. Follow the link below to review the differences we found.
\t2. Please update your configuration stored in Bitbucket or the configuration in the target environment as needed so that they match."""

    Project project
    BuildStage buildStage
    IPipelineSteps script
    MROPipelineUtil util
    JiraUseCase jira
    LeVADocumentScheduler levaDocScheduler
    ILogger logger

    def phase = MROPipelineUtil.PipelinePhases.BUILD

    def setup() {
        script = new PipelineSteps()
        levaDocScheduler = Mock(LeVADocumentScheduler)
        project = Spy(createProject())
        util = Mock(MROPipelineUtil)
        jira = Mock(JiraUseCase)
        logger = new Logger(script, true)
        createService()
        buildStage = Spy(new BuildStage(script, project, project.repositories, null))
    }

    ServiceRegistry createService() {
        def registry = ServiceRegistry.instance

        registry.add(IPipelineSteps, script)
        registry.add(LeVADocumentScheduler, levaDocScheduler)
        registry.add(MROPipelineUtil, util)
        registry.add(JiraUseCase, jira)
        registry.add(Logger, logger)

        return registry
    }

    def "successful execution"() {
        when:
        buildStage.run()

        then:
        1 * levaDocScheduler.run(phase, PipelinePhaseLifecycleStage.POST_START)
        1 * levaDocScheduler.run(phase, PipelinePhaseLifecycleStage.PRE_END)
    }

    def "unit test errors in WIP version doesn't break the stage"() {
        given:
        project.hasFailingTests = true
        project.data.buildParams.version = project.BUILD_PARAM_VERSION_DEFAULT

        when:
        buildStage.run()

        then:
        1 * levaDocScheduler.run(phase, PipelinePhaseLifecycleStage.POST_START)
        1 * levaDocScheduler.run(phase, PipelinePhaseLifecycleStage.PRE_END)
        1 * util.failBuild(_,_)
    }

    def "unit test errors in X version break the stage"() {
        given:
        project.hasFailingTests = true

        when:
        buildStage.run()

        then:
        1 * levaDocScheduler.run(phase, PipelinePhaseLifecycleStage.POST_START)
        1 * levaDocScheduler.run(phase, PipelinePhaseLifecycleStage.PRE_END)
        1 * util.failBuild(_,_)
        IllegalStateException ex = thrown()
        ex.message == 'Delivery failed since the following Bitbucket repositories contain errors:\n\n'
    }

    def "tailor failure logs correct message and adds correct Jira comment"() {
        given:
        project.data.buildParams.version = testVersion
        def testRepos = [
            [id       : "golang", defaultBranch: "master", type: "ods",
             data     : [openshift  : [builds   : [], deployments: [:], tailorFailure: true,
                                       documents: [:]],
                         failedStage: "odsPipeline error"],
             doInstall: true],
            [id       : "other", defaultBranch: "master", type: "ods",
             data     : [openshift  : [builds   : [], deployments: [:],
                                       documents: [:]],
                         failedStage: "odsPipeline error"],
             doInstall: true],
            [id       : "third", defaultBranch: "master", type: "ods",
             data     : [openshift  : [builds   : [], deployments: [:], tailorFailure: true,
                                       documents: [:]],
                         failedStage: "odsPipeline error"],
             doInstall: true]]
        buildStage = Spy(new BuildStage(script, project, testRepos, null))

        when:
        buildStage.run()

        then:
        1 * util.failBuild(TAILOR_FAILURE_LOG_MESSAGE, false)
        IllegalStateException ex = thrown()
        ex.message == TAILOR_FAILURE_JIRA_COMMENT

        where:
        testVersion     |   _
        "1.0"           |   _
        "WIP"           |   _
    }

    def "tailor failure repositories corner cases do not fail the build"() {
        given:
        buildStage = Spy(new BuildStage(script, project, cornerCaseRepos, null))

        when:
        buildStage.run()

        then:
        0 * util.failBuild(_)

        where:
        cornerCaseRepos     |   _
        []                  |   _
        null                |   _
    }


}
