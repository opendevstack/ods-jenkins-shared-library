package org.ods.orchestration

import org.ods.PipelineScript
import org.ods.orchestration.scheduler.LeVADocumentScheduler
import org.ods.orchestration.usecase.JiraUseCase
import org.ods.orchestration.util.MROPipelineUtil
import org.ods.orchestration.util.PipelinePhaseLifecycleStage
import org.ods.orchestration.util.Project
import org.ods.services.ServiceRegistry
import org.ods.util.ILogger
import org.ods.util.IPipelineSteps
import org.ods.util.Logger
import org.ods.util.PipelineSteps
import util.SpecHelper

import static util.FixtureHelper.createProject

class BuildStageSpec extends SpecHelper {
    Project project
    BuildStage buildStage
    IPipelineSteps steps
    PipelineScript script
    MROPipelineUtil util
    JiraUseCase jira
    LeVADocumentScheduler levaDocScheduler
    ILogger logger

    def phase = MROPipelineUtil.PipelinePhases.BUILD

    def setup() {
        script = new PipelineScript()
        steps = Mock(PipelineSteps)
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

        registry.add(PipelineSteps, steps)
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
        1 * util.failBuild(_)
    }

    def "unit test errors in X version break the stage"() {
        given:
        project.hasFailingTests = true

        when:
        buildStage.run()

        then:
        1 * levaDocScheduler.run(phase, PipelinePhaseLifecycleStage.POST_START)
        1 * levaDocScheduler.run(phase, PipelinePhaseLifecycleStage.PRE_END)
        1 * util.failBuild(_)
        IllegalStateException ex = thrown()
        ex.message == 'Failing build as repositories contain errors!\nFailed repositories: \n'
    }

    def "find all repos with tailor deployment failure comma separated"() {
        given:
        def allFailedRepos = [
            [id:"golang", branch:"master", type:"ods",
             data:[openshift:[builds:[], deployments:[:], tailorFailure:true,
                              documents:[:]],
                   failedStage:"odsPipeline error"],
             doInstall:true],
            [id:"other", branch:"master", type:"ods",
             data:[openshift:[builds:[], deployments:[:],
                              documents:[:]],
                   failedStage:"odsPipeline error"],
             doInstall:true],
            [id:"third", branch:"master", type:"ods",
             data:[openshift:[builds:[], deployments:[:], tailorFailure:true,
                              documents:[:]],
                   failedStage:"odsPipeline error"],
             doInstall:true]]

        when:
        def nominalResult = buildStage.findAllReposWithTailorDeploymentFailureCommaSeparated(allFailedRepos)

        then:
        nominalResult == "\"golang\", \"third\""

        when:
        def result = buildStage.findAllReposWithTailorDeploymentFailureCommaSeparated(testRepos)

        then:
        result == expected

        where:
        testRepos           |           expected
        null                |           ""
        []                  |           ""
    }

    def "find all repos with tailor deployment warning comma separated"() {
        given:
        def repos = [
            [id:"golang", branch:"master", type:"ods",
             doInstall:true,
             tailorWarning: true],
            [id:"other", branch:"master", type:"ods",
             doInstall:true,
             tailorWarning: true],
            [id:"third", branch:"master", type:"ods",
             doInstall:true]
        ]

        when:
        def nominalResult = buildStage.findAllReposWithTailorDeploymentWarningCommaSeparated(repos)

        then:
        nominalResult == "\"golang\", \"other\""

        when:
        def result = buildStage.findAllReposWithTailorDeploymentWarningCommaSeparated(testRepos)

        then:
        result == expected

        where:
        testRepos           |           expected
        null                |           ""
        []                  |           ""

    }

    def "sanitize failed repositories"() {
        given:
        def allFailedRepos = [
            [id:"golang", branch:"master", type:"ods",
             data:[openshift:[builds:[], deployments:[:], tailorFailure:true,
                              documents:[:]],
                   failedStage:"odsPipeline error"],
             doInstall:true],
            [id:"other", branch:"master", type:"ods",
             data:[openshift:[builds:[], deployments:[:],
                              documents:[:]],
                   failedStage:"odsPipeline error"],
             doInstall:true],
            [id:"third", branch:"master", type:"ods",
             data:[openshift:[builds:[], deployments:[:], tailorFailure:true,
                              documents:[:]],
                   failedStage:"odsPipeline error"],
             doInstall:true]]

        when:
        def allResult = buildStage.sanitizeFailedRepos(allFailedRepos)

        then:
        allResult == "1.\tRepository id: golang\n" +
            "\tBranch: master\n" +
            "\tRepository type: ods\n" +
            "\n" +
            "2.\tRepository id: other\n" +
            "\tBranch: master\n" +
            "\tRepository type: ods\n" +
            "\n" +
            "3.\tRepository id: third\n" +
            "\tBranch: master\n" +
            "\tRepository type: ods"

        when:
        def result = buildStage.sanitizeFailedRepos(testRepos)

        then:
        result == expected

        where:
        testRepos           |           expected
        null                |           ""
        []                  |           ""
    }
}
