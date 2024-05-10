package org.ods.orchestration.scheduler

import groovy.util.logging.Slf4j
import org.ods.core.test.LoggerStub
import org.ods.services.JenkinsService
import org.ods.services.NexusService
import org.ods.services.GitService
import org.ods.services.OpenShiftService
import org.ods.orchestration.service.*
import org.ods.orchestration.usecase.*
import org.ods.orchestration.util.*
import org.ods.util.ILogger
import org.ods.util.IPipelineSteps
import org.ods.util.Logger

import spock.lang.*

import static util.FixtureHelper.*

import util.*

@Slf4j
class LeVADocumentSchedulerSpec extends SpecHelper {

    static def PROJECT_GAMP_1
    static def PROJECT_GAMP_3
    static def PROJECT_GAMP_3_ODS_SAAS
    static def PROJECT_GAMP_4
    static def PROJECT_GAMP_5
    static def PROJECT_GAMP_5_WITHOUT_JIRA
    static def PROJECT_GAMP_5_WITHOUT_REPOS

    static def REPO_ODS_CODE
    static def REPO_ODS_SERVICE
    static def REPO_ODS_TEST

    static def REPO_TYPE_ODS_SAAS_SERVICE

    ILogger logger =  new LoggerStub(log)

    def setupSpec() {
        def project = createProject()

        REPO_ODS_CODE = project.repositories[0]
        REPO_ODS_CODE.type = MROPipelineUtil.PipelineConfig.REPO_TYPE_ODS_CODE

        REPO_ODS_SERVICE = project.repositories[1]
        REPO_ODS_SERVICE.type = MROPipelineUtil.PipelineConfig.REPO_TYPE_ODS_SERVICE

        REPO_ODS_TEST = project.repositories[2]
        REPO_ODS_TEST.type = MROPipelineUtil.PipelineConfig.REPO_TYPE_ODS_TEST

        REPO_TYPE_ODS_SAAS_SERVICE = project.repositories[3]
        REPO_TYPE_ODS_SAAS_SERVICE.type = MROPipelineUtil.PipelineConfig.REPO_TYPE_ODS_SAAS_SERVICE

        PROJECT_GAMP_1 = createProject()
        PROJECT_GAMP_1.data.metadata.capabilities = [[LeVADocs: [GAMPCategory: "1"]]]

        PROJECT_GAMP_3 = createProject()
        PROJECT_GAMP_3.data.metadata.capabilities = [[LeVADocs: [GAMPCategory: "3"]]]

        PROJECT_GAMP_3_ODS_SAAS = createProject()
        PROJECT_GAMP_3_ODS_SAAS.data.metadata.capabilities = [[LeVADocs: [GAMPCategory: "3"]]]
        PROJECT_GAMP_3_ODS_SAAS.repositories = [["id":"saas", "type":MROPipelineUtil.PipelineConfig.REPO_TYPE_ODS_SAAS_SERVICE as String]]

        PROJECT_GAMP_4 = createProject()
        PROJECT_GAMP_4.data.metadata.capabilities = [[LeVADocs: [GAMPCategory: "4"]]]

        PROJECT_GAMP_5 = createProject()
        PROJECT_GAMP_5.data.metadata.capabilities = [[LeVADocs: [GAMPCategory: "5"]]]

        PROJECT_GAMP_5_WITHOUT_JIRA = createProject()
        PROJECT_GAMP_5_WITHOUT_JIRA.data.metadata.capabilities = [[LeVADocs: [GAMPCategory: "5"]]]
        PROJECT_GAMP_5_WITHOUT_JIRA.services.jira = null

        PROJECT_GAMP_5_WITHOUT_REPOS = createProject()
        PROJECT_GAMP_5_WITHOUT_REPOS.data.metadata.capabilities = [[LeVADocs: [GAMPCategory: "5"]]]
        PROJECT_GAMP_5_WITHOUT_REPOS.repositories = []
    }

    @Unroll
    def "is document applicable for GAMP category 1"() {
        given:
        def project = PROJECT_GAMP_1

        def steps = Spy(util.PipelineSteps)
        def util = Mock(MROPipelineUtil)
        def usecase = Mock(LeVADocumentUseCase)
        def logger = Mock(Logger)
        def bbt = Mock(BitbucketTraceabilityUseCase)

        def scheduler = Spy(new LeVADocumentScheduler(project, steps, util, usecase, logger))

        expect:
        scheduler.isDocumentApplicable(documentType as String, phase, stage, repo) == result

        where:
        documentType                        | repo | phase                                   | stage                                                         || result
        // CSD: Configuration Specification
        DocumentType.CSD | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CSD | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CSD | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CSD | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || true
        DocumentType.CSD | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CSD | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CSD | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CSD | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CSD | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CSD | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CSD | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CSD | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CSD | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CSD | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CSD | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CSD | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CSD | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CSD | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CSD | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CSD | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CSD | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CSD | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CSD | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CSD | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false

        DocumentType.CSD | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CSD | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CSD | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CSD | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CSD | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CSD | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CSD | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CSD | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CSD | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CSD | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CSD | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CSD | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CSD | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CSD | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CSD | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CSD | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CSD | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CSD | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CSD | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CSD | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CSD | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CSD | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CSD | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CSD | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CSD | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CSD | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CSD | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CSD | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CSD | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CSD | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CSD | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CSD | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CSD | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CSD | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CSD | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CSD | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CSD | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CSD | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CSD | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CSD | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CSD | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CSD | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CSD | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CSD | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CSD | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CSD | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CSD | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CSD | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CSD | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CSD | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CSD | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CSD | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CSD | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CSD | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CSD | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CSD | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CSD | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CSD | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CSD | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CSD | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CSD | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CSD | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CSD | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CSD | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CSD | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CSD | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CSD | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CSD | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CSD | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CSD | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CSD | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CSD | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false

        // DTP: Software Development Testing Plan
        DocumentType.DTP | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTP | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTP | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTP | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTP | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTP | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTP | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTP | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTP | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTP | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTP | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTP | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTP | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTP | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTP | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTP | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTP | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTP | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTP | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTP | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTP | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTP | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTP | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTP | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false

        DocumentType.DTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false

        // DTR: Software Development Testing Report
        DocumentType.DTR | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTR | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTR | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTR | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTR | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTR | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTR | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTR | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTR | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTR | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTR | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTR | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTR | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTR | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTR | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTR | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTR | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTR | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTR | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTR | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTR | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTR | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTR | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTR | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false

        DocumentType.DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false

        // CFTP: Combined Functional and Requirements Testing Plan
        DocumentType.CFTP | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTP | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTP | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTP | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTP | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || true
        DocumentType.CFTP | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTP | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTP | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTP | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTP | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTP | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTP | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTP | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTP | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTP | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTP | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTP | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTP | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTP | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTP | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTP | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTP | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTP | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTP | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false

        DocumentType.CFTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false

        // TRC: Combined Functional and Requirements Testing Plan
        DocumentType.TRC | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TRC | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TRC | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TRC | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TRC | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TRC | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TRC | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TRC | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TRC | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TRC | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TRC | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TRC | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TRC | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TRC | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TRC | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TRC | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TRC | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TRC | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TRC | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TRC | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TRC | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TRC | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TRC | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TRC | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false

        DocumentType.TRC | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TRC | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TRC | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TRC | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TRC | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TRC | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TRC | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TRC | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TRC | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TRC | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TRC | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TRC | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TRC | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TRC | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TRC | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TRC | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TRC | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TRC | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TRC | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TRC | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TRC | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TRC | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TRC | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TRC | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TRC | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TRC | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TRC | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TRC | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TRC | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TRC | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TRC | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TRC | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TRC | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TRC | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TRC | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TRC | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TRC | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TRC | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TRC | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TRC | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TRC | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TRC | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TRC | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TRC | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TRC | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TRC | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TRC | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TRC | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TRC | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TRC | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TRC | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TRC | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TRC | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TRC | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TRC | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TRC | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TRC | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TRC | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TRC | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TRC | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TRC | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TRC | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TRC | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TRC | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TRC | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TRC | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TRC | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TRC | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TRC | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TRC | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TRC | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TRC | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false

        // CFTR: Combined Functional and Requirements Testing Report
        DocumentType.CFTR | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTR | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTR | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTR | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTR | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTR | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTR | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTR | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTR | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTR | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTR | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTR | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTR | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTR | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTR | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTR | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || true
        DocumentType.CFTR | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTR | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTR | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTR | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTR | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTR | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTR | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTR | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false

        DocumentType.CFTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false

        // IVP: Configuration and Installation Testing Plan
        DocumentType.IVP | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVP | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVP | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVP | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVP | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || true
        DocumentType.IVP | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVP | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVP | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVP | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVP | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVP | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVP | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVP | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVP | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVP | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVP | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVP | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVP | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVP | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVP | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVP | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVP | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVP | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVP | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false

        DocumentType.IVP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false

        // IVR: Configuration and Installation Testing Report
        DocumentType.IVR | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVR | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVR | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVR | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVR | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVR | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVR | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVR | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVR | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVR | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVR | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVR | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVR | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVR | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVR | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVR | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || true
        DocumentType.IVR | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVR | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVR | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVR | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVR | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVR | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVR | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVR | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false

        DocumentType.IVR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false

        // RA: Risk Assessment
        DocumentType.RA | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.RA | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.RA | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.RA | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.RA | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || true
        DocumentType.RA | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.RA | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.RA | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.RA | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.RA | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.RA | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.RA | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.RA | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.RA | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.RA | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.RA | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.RA | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.RA | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.RA | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.RA | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.RA | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.RA | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.RA | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.RA | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false

        DocumentType.RA | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.RA | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.RA | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.RA | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.RA | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.RA | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.RA | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.RA | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.RA | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.RA | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.RA | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.RA | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.RA | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.RA | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.RA | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.RA | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.RA | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.RA | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.RA | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.RA | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.RA | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.RA | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.RA | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.RA | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.RA | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.RA | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.RA | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.RA | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.RA | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.RA | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.RA | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.RA | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.RA | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.RA | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.RA | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.RA | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.RA | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.RA | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.RA | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.RA | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.RA | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.RA | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.RA | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.RA | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.RA | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.RA | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.RA | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.RA | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.RA | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.RA | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.RA | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.RA | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.RA | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.RA | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.RA | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.RA | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.RA | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.RA | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.RA | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.RA | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.RA | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.RA | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.RA | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.RA | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.RA | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.RA | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.RA | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.RA | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.RA | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.RA | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.RA | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.RA | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false

        // SSDS: Software Design Specification
        DocumentType.SSDS | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.SSDS | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.SSDS | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.SSDS | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.SSDS | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || true
        DocumentType.SSDS | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.SSDS | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.SSDS | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.SSDS | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.SSDS | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.SSDS | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.SSDS | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.SSDS | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.SSDS | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.SSDS | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.SSDS | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.SSDS | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.SSDS | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.SSDS | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.SSDS | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.SSDS | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.SSDS | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.SSDS | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.SSDS | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false

        DocumentType.SSDS | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.SSDS | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.SSDS | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.SSDS | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.SSDS | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.SSDS | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.SSDS | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.SSDS | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.SSDS | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.SSDS | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.SSDS | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.SSDS | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.SSDS | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.SSDS | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.SSDS | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.SSDS | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.SSDS | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.SSDS | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.SSDS | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.SSDS | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.SSDS | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.SSDS | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.SSDS | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.SSDS | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.SSDS | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.SSDS | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.SSDS | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.SSDS | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.SSDS | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.SSDS | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.SSDS | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.SSDS | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.SSDS | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.SSDS | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.SSDS | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.SSDS | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.SSDS | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.SSDS | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.SSDS | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.SSDS | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.SSDS | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.SSDS | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.SSDS | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.SSDS | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.SSDS | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.SSDS | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.SSDS | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.SSDS | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.SSDS | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.SSDS | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.SSDS | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.SSDS | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.SSDS | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.SSDS | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.SSDS | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.SSDS | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.SSDS | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.SSDS | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.SSDS | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.SSDS | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.SSDS | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.SSDS | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.SSDS | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.SSDS | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.SSDS | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.SSDS | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.SSDS | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.SSDS | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.SSDS | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.SSDS | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.SSDS | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.SSDS | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false

        // TIP: Technical Installation Plan
        DocumentType.TIP | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIP | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIP | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIP | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIP | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || true
        DocumentType.TIP | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIP | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIP | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIP | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIP | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIP | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIP | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIP | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIP | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIP | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIP | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIP | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIP | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIP | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIP | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIP | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIP | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIP | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIP | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false

        DocumentType.TIP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false

        // TIR: Technical Installation Report
        DocumentType.TIR | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIR | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIR | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIR | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIR | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIR | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIR | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIR | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIR | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIR | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIR | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIR | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIR | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIR | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIR | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIR | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIR | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIR | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIR | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIR | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIR | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIR | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIR | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIR | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false

        DocumentType.TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || true
        DocumentType.TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || true
        DocumentType.TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false

        // OVERALL_DTR: Overall Software Development Testing Report
        DocumentType.OVERALL_DTR | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_DTR | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_DTR | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_DTR | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_DTR | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_DTR | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_DTR | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_DTR | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_DTR | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_DTR | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_DTR | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_DTR | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_DTR | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_DTR | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_DTR | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_DTR | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_DTR | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_DTR | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_DTR | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_DTR | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_DTR | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_DTR | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_DTR | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_DTR | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false

        DocumentType.OVERALL_DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false

        // OVERALL_TIR: Overall Technical Installation Report
        DocumentType.OVERALL_TIR | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_TIR | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_TIR | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_TIR | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_TIR | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_TIR | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_TIR | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_TIR | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_TIR | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_TIR | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_TIR | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_TIR | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_TIR | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_TIR | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_TIR | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_TIR | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_TIR | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_TIR | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_TIR | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_TIR | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_TIR | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_TIR | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_TIR | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_TIR | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || true

        DocumentType.OVERALL_TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
    }

    @Unroll
    def "is document applicable for GAMP category 3"() {
        given:
        def project = PROJECT_GAMP_3

        def steps = Spy(util.PipelineSteps)
        def util = Mock(MROPipelineUtil)
        def usecase = Mock(LeVADocumentUseCase)
        def logger = Mock(Logger)
        def bbt = Mock(BitbucketTraceabilityUseCase)

        def scheduler = Spy(new LeVADocumentScheduler(project, steps, util, usecase, logger))

        expect:
        scheduler.isDocumentApplicable(documentType as String, phase, stage, repo) == result

        where:
        documentType                        | repo | phase                                   | stage                                                         || result
        // CSD: Configuration Specification
        DocumentType.CSD | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CSD | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CSD | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CSD | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || true
        DocumentType.CSD | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CSD | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CSD | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CSD | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CSD | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CSD | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CSD | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CSD | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CSD | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CSD | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CSD | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CSD | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CSD | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CSD | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CSD | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CSD | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CSD | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START          || false
        DocumentType.CSD | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CSD | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CSD | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false

        DocumentType.CSD | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CSD | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CSD | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CSD | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CSD | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CSD | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CSD | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CSD | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CSD | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CSD | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CSD | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CSD | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CSD | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CSD | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CSD | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CSD | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CSD | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CSD | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CSD | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CSD | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CSD | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CSD | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CSD | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CSD | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CSD | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CSD | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CSD | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CSD | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CSD | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CSD | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CSD | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CSD | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CSD | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CSD | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CSD | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CSD | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CSD | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CSD | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CSD | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CSD | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CSD | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CSD | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CSD | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CSD | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CSD | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CSD | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CSD | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CSD | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CSD | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CSD | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CSD | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CSD | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CSD | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CSD | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CSD | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CSD | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CSD | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CSD | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CSD | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CSD | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CSD | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CSD | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CSD | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CSD | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CSD | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CSD | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CSD | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CSD | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CSD | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CSD | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CSD | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CSD | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false

        // DTP: Software Development Testing Plan
        DocumentType.DTP | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTP | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTP | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTP | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTP | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTP | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTP | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTP | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTP | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTP | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTP | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTP | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTP | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTP | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTP | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTP | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTP | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTP | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTP | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTP | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTP | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTP | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTP | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTP | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false

        DocumentType.DTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false

        // DTR: Software Development Testing Report
        DocumentType.DTR | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTR | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTR | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTR | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTR | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTR | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTR | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTR | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTR | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTR | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTR | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTR | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTR | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTR | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTR | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTR | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTR | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTR | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTR | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTR | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTR | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTR | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTR | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTR | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false

        DocumentType.DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false

        // CFTP: Combined Functional and Requirements Testing Plan
        DocumentType.CFTP | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTP | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTP | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTP | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTP | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || true
        DocumentType.CFTP | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTP | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTP | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTP | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTP | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTP | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTP | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTP | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTP | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTP | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTP | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTP | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTP | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTP | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTP | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTP | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTP | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTP | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTP | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false

        DocumentType.CFTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false

        // TRC: Combined Functional and Requirements Testing Plan
        DocumentType.TRC | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TRC | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TRC | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TRC | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TRC | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || true
        DocumentType.TRC | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TRC | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TRC | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TRC | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TRC | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TRC | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TRC | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TRC | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TRC | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TRC | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TRC | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TRC | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TRC | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TRC | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TRC | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TRC | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TRC | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TRC | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TRC | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false

        DocumentType.TRC | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TRC | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TRC | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TRC | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TRC | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TRC | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TRC | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TRC | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TRC | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TRC | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TRC | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TRC | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TRC | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TRC | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TRC | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TRC | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TRC | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TRC | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TRC | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TRC | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TRC | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TRC | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TRC | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TRC | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TRC | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TRC | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TRC | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TRC | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TRC | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TRC | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TRC | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TRC | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TRC | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TRC | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TRC | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TRC | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TRC | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TRC | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TRC | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TRC | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TRC | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TRC | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TRC | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TRC | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TRC | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TRC | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TRC | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TRC | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TRC | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TRC | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TRC | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TRC | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TRC | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TRC | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TRC | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TRC | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TRC | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TRC | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TRC | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TRC | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TRC | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TRC | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TRC | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TRC | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TRC | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TRC | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TRC | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TRC | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TRC | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TRC | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TRC | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TRC | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false

        // CFTR: Combined Functional and Requirements Testing Report
        DocumentType.CFTR | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTR | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTR | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTR | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTR | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTR | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTR | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTR | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTR | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTR | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTR | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTR | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTR | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTR | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTR | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTR | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || true
        DocumentType.CFTR | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTR | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTR | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTR | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTR | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTR | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTR | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTR | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false

        DocumentType.CFTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false

        // IVP: Configuration and Installation Testing Plan
        DocumentType.IVP | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVP | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVP | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVP | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVP | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || true
        DocumentType.IVP | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVP | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVP | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVP | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVP | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVP | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVP | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVP | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVP | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVP | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVP | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVP | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVP | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVP | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVP | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVP | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVP | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVP | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVP | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false

        DocumentType.IVP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false

        // IVR: Configuration and Installation Testing Report
        DocumentType.IVR | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVR | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVR | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVR | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVR | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVR | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVR | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVR | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVR | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVR | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVR | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVR | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVR | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVR | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVR | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVR | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || true
        DocumentType.IVR | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVR | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVR | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVR | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVR | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVR | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVR | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVR | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false

        DocumentType.IVR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false

        // RA: Risk Assessment
        DocumentType.RA | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.RA | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.RA | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.RA | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.RA | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || true
        DocumentType.RA | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.RA | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.RA | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.RA | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.RA | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.RA | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.RA | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.RA | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.RA | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.RA | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.RA | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.RA | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.RA | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.RA | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.RA | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.RA | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.RA | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.RA | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.RA | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false

        DocumentType.RA | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.RA | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.RA | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.RA | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.RA | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.RA | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.RA | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.RA | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.RA | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.RA | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.RA | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.RA | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.RA | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.RA | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.RA | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.RA | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.RA | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.RA | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.RA | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.RA | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.RA | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.RA | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.RA | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.RA | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.RA | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.RA | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.RA | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.RA | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.RA | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.RA | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.RA | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.RA | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.RA | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.RA | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.RA | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.RA | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.RA | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.RA | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.RA | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.RA | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.RA | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.RA | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.RA | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.RA | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.RA | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.RA | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.RA | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.RA | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.RA | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.RA | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.RA | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.RA | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.RA | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.RA | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.RA | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.RA | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.RA | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.RA | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.RA | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.RA | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.RA | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.RA | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.RA | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.RA | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.RA | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.RA | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.RA | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.RA | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.RA | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.RA | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.RA | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.RA | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false

        // SSDS: Software Design Specification
        DocumentType.SSDS | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.SSDS | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.SSDS | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.SSDS | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.SSDS | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || true
        DocumentType.SSDS | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.SSDS | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.SSDS | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.SSDS | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.SSDS | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.SSDS | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.SSDS | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.SSDS | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.SSDS | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.SSDS | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.SSDS | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.SSDS | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.SSDS | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.SSDS | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.SSDS | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.SSDS | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.SSDS | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.SSDS | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.SSDS | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false

        DocumentType.SSDS | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.SSDS | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.SSDS | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.SSDS | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.SSDS | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.SSDS | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.SSDS | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.SSDS | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.SSDS | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.SSDS | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.SSDS | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.SSDS | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.SSDS | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.SSDS | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.SSDS | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.SSDS | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.SSDS | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.SSDS | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.SSDS | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.SSDS | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.SSDS | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.SSDS | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.SSDS | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.SSDS | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.SSDS | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.SSDS | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.SSDS | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.SSDS | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.SSDS | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.SSDS | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.SSDS | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.SSDS | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.SSDS | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.SSDS | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.SSDS | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.SSDS | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.SSDS | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.SSDS | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.SSDS | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.SSDS | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.SSDS | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.SSDS | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.SSDS | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.SSDS | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.SSDS | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.SSDS | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.SSDS | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.SSDS | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.SSDS | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.SSDS | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.SSDS | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.SSDS | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.SSDS | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.SSDS | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.SSDS | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.SSDS | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.SSDS | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.SSDS | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.SSDS | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.SSDS | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.SSDS | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.SSDS | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.SSDS | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.SSDS | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.SSDS | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.SSDS | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.SSDS | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.SSDS | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.SSDS | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.SSDS | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.SSDS | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.SSDS | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false

        // TIP: Technical Installation Plan
        DocumentType.TIP | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIP | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIP | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIP | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIP | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || true
        DocumentType.TIP | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIP | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIP | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIP | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIP | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIP | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIP | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIP | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIP | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIP | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIP | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIP | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIP | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIP | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIP | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIP | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIP | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIP | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIP | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false

        DocumentType.TIP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false

        // TIR: Technical Installation Report
        DocumentType.TIR | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIR | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIR | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIR | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIR | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIR | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIR | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIR | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIR | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIR | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIR | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIR | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIR | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIR | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIR | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIR | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIR | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIR | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIR | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIR | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIR | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIR | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIR | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIR | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false

        DocumentType.TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || true
        DocumentType.TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || true
        DocumentType.TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false

        // OVERALL_DTR: Overall Software Development Testing Report
        DocumentType.OVERALL_DTR | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_DTR | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_DTR | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_DTR | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_DTR | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_DTR | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_DTR | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_DTR | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_DTR | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_DTR | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_DTR | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_DTR | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_DTR | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_DTR | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_DTR | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_DTR | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_DTR | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_DTR | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_DTR | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_DTR | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_DTR | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_DTR | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_DTR | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_DTR | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false

        DocumentType.OVERALL_DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false

        // OVERALL_TIR: Overall Technical Installation Report
        DocumentType.OVERALL_TIR | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_TIR | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_TIR | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_TIR | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_TIR | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_TIR | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_TIR | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_TIR | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_TIR | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_TIR | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_TIR | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_TIR | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_TIR | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_TIR | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_TIR | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_TIR | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_TIR | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_TIR | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_TIR | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_TIR | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_TIR | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_TIR | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_TIR | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_TIR | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || true

        DocumentType.OVERALL_TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
    }

    @Unroll
    def "is document applicable for GAMP category 4"() {
        given:
        def project = PROJECT_GAMP_4

        def steps = Spy(util.PipelineSteps)
        def util = Mock(MROPipelineUtil)
        def usecase = Mock(LeVADocumentUseCase)
        def logger = Mock(Logger)
        def bbt = Mock(BitbucketTraceabilityUseCase)

        def scheduler = Spy(new LeVADocumentScheduler(project, steps, util, usecase, logger))

        expect:
        scheduler.isDocumentApplicable(documentType as String, phase, stage, repo) == result

        where:
        documentType                        | repo | phase                                   | stage                                                         || result
        // CSD: Configuration Specification
        DocumentType.CSD | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CSD | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CSD | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CSD | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || true
        DocumentType.CSD | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CSD | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CSD | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CSD | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CSD | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CSD | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CSD | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CSD | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CSD | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CSD | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CSD | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CSD | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CSD | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CSD | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CSD | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CSD | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CSD | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CSD | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CSD | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CSD | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false

        DocumentType.CSD | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CSD | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CSD | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CSD | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CSD | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CSD | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CSD | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CSD | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CSD | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CSD | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CSD | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CSD | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CSD | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CSD | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CSD | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CSD | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CSD | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CSD | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CSD | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CSD | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CSD | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CSD | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CSD | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CSD | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CSD | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CSD | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CSD | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CSD | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CSD | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CSD | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CSD | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CSD | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CSD | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CSD | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CSD | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CSD | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CSD | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CSD | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CSD | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CSD | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CSD | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CSD | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CSD | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CSD | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CSD | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CSD | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CSD | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CSD | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CSD | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CSD | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CSD | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CSD | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CSD | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CSD | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CSD | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CSD | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CSD | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CSD | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CSD | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CSD | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CSD | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CSD | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CSD | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CSD | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CSD | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CSD | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CSD | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CSD | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CSD | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CSD | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CSD | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CSD | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false

        // DTP: Software Development Testing Plan
        DocumentType.DTP | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTP | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTP | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTP | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTP | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTP | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTP | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTP | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTP | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTP | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTP | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTP | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTP | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTP | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTP | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTP | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTP | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTP | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTP | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTP | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTP | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTP | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTP | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTP | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false

        DocumentType.DTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false

        // DTR: Software Development Testing Report
        DocumentType.DTR | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTR | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTR | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTR | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTR | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTR | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTR | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTR | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTR | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTR | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTR | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTR | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTR | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTR | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTR | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTR | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTR | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTR | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTR | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTR | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTR | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTR | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTR | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTR | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false

        DocumentType.DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false

        // CFTP: Combined Functional and Requirements Testing Plan
        DocumentType.CFTP | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTP | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTP | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTP | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTP | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || true
        DocumentType.CFTP | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTP | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTP | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTP | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTP | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTP | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTP | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTP | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTP | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTP | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTP | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTP | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTP | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTP | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTP | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTP | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTP | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTP | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTP | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false

        DocumentType.CFTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false

        // TRC: Combined Functional and Requirements Testing Plan
        DocumentType.TRC | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TRC | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TRC | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TRC | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TRC | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || true
        DocumentType.TRC | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TRC | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TRC | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TRC | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TRC | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TRC | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TRC | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TRC | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TRC | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TRC | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TRC | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TRC | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TRC | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TRC | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TRC | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TRC | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TRC | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TRC | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TRC | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false

        DocumentType.TRC | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TRC | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TRC | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TRC | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TRC | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TRC | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TRC | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TRC | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TRC | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TRC | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TRC | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TRC | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TRC | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TRC | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TRC | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TRC | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TRC | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TRC | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TRC | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TRC | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TRC | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TRC | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TRC | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TRC | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TRC | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TRC | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TRC | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TRC | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TRC | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TRC | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TRC | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TRC | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TRC | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TRC | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TRC | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TRC | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TRC | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TRC | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TRC | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TRC | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TRC | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TRC | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TRC | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TRC | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TRC | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TRC | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TRC | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TRC | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TRC | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TRC | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TRC | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TRC | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TRC | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TRC | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TRC | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TRC | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TRC | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TRC | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TRC | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TRC | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TRC | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TRC | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TRC | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TRC | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TRC | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TRC | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TRC | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TRC | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TRC | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TRC | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TRC | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TRC | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false

        // CFTR: Combined Functional and Requirements Testing Report
        DocumentType.CFTR | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTR | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTR | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTR | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTR | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTR | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTR | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTR | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTR | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTR | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTR | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTR | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTR | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTR | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTR | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTR | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || true
        DocumentType.CFTR | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTR | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTR | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTR | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTR | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTR | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTR | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTR | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false

        DocumentType.CFTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false

        // IVP: Configuration and Installation Testing Plan
        DocumentType.IVP | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVP | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVP | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVP | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVP | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || true
        DocumentType.IVP | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVP | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVP | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVP | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVP | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVP | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVP | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVP | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVP | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVP | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVP | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVP | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVP | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVP | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVP | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVP | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVP | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVP | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVP | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false

        DocumentType.IVP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false

        // IVR: Configuration and Installation Testing Report
        DocumentType.IVR | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVR | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVR | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVR | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVR | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVR | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVR | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVR | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVR | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVR | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVR | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVR | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVR | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVR | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVR | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVR | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || true
        DocumentType.IVR | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVR | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVR | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVR | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVR | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVR | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVR | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVR | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false

        DocumentType.IVR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false

        // RA: Risk Assessment
        DocumentType.RA | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.RA | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.RA | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.RA | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.RA | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || true
        DocumentType.RA | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.RA | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.RA | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.RA | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.RA | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.RA | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.RA | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.RA | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.RA | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.RA | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.RA | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.RA | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.RA | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.RA | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.RA | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.RA | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.RA | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.RA | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.RA | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false

        DocumentType.RA | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.RA | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.RA | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.RA | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.RA | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.RA | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.RA | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.RA | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.RA | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.RA | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.RA | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.RA | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.RA | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.RA | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.RA | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.RA | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.RA | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.RA | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.RA | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.RA | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.RA | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.RA | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.RA | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.RA | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.RA | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.RA | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.RA | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.RA | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.RA | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.RA | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.RA | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.RA | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.RA | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.RA | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.RA | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.RA | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.RA | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.RA | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.RA | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.RA | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.RA | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.RA | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.RA | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.RA | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.RA | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.RA | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.RA | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.RA | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.RA | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.RA | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.RA | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.RA | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.RA | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.RA | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.RA | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.RA | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.RA | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.RA | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.RA | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.RA | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.RA | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.RA | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.RA | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.RA | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.RA | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.RA | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.RA | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.RA | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.RA | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.RA | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.RA | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.RA | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false

        // SSDS: Software Design Specification
        DocumentType.SSDS | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.SSDS | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.SSDS | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.SSDS | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.SSDS | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || true
        DocumentType.SSDS | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.SSDS | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.SSDS | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.SSDS | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.SSDS | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.SSDS | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.SSDS | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.SSDS | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.SSDS | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.SSDS | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.SSDS | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.SSDS | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.SSDS | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.SSDS | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.SSDS | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.SSDS | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.SSDS | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.SSDS | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.SSDS | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false

        DocumentType.SSDS | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.SSDS | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.SSDS | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.SSDS | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.SSDS | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.SSDS | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.SSDS | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.SSDS | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.SSDS | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.SSDS | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.SSDS | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.SSDS | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.SSDS | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.SSDS | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.SSDS | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.SSDS | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.SSDS | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.SSDS | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.SSDS | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.SSDS | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.SSDS | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.SSDS | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.SSDS | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.SSDS | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.SSDS | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.SSDS | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.SSDS | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.SSDS | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.SSDS | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.SSDS | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.SSDS | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.SSDS | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.SSDS | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.SSDS | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.SSDS | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.SSDS | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.SSDS | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.SSDS | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.SSDS | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.SSDS | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.SSDS | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.SSDS | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.SSDS | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.SSDS | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.SSDS | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.SSDS | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.SSDS | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.SSDS | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.SSDS | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.SSDS | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.SSDS | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.SSDS | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.SSDS | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.SSDS | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.SSDS | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.SSDS | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.SSDS | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.SSDS | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.SSDS | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.SSDS | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.SSDS | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.SSDS | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.SSDS | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.SSDS | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.SSDS | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.SSDS | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.SSDS | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.SSDS | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.SSDS | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.SSDS | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.SSDS | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.SSDS | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false

        // TIP: Technical Installation Plan
        DocumentType.TIP | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIP | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIP | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIP | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIP | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || true
        DocumentType.TIP | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIP | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIP | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIP | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIP | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIP | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIP | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIP | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIP | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIP | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIP | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIP | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIP | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIP | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIP | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIP | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIP | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIP | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIP | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false

        DocumentType.TIP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false

        // TIR: Technical Installation Report
        DocumentType.TIR | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIR | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIR | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIR | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIR | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIR | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIR | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIR | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIR | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIR | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIR | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIR | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIR | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIR | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIR | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIR | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIR | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIR | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIR | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIR | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIR | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIR | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIR | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIR | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false

        DocumentType.TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || true
        DocumentType.TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || true
        DocumentType.TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false

        // OVERALL_DTR: Overall Software Development Testing Report
        DocumentType.OVERALL_DTR | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_DTR | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_DTR | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_DTR | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_DTR | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_DTR | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_DTR | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_DTR | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_DTR | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_DTR | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_DTR | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_DTR | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_DTR | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_DTR | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_DTR | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_DTR | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_DTR | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_DTR | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_DTR | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_DTR | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_DTR | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_DTR | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_DTR | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_DTR | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false

        DocumentType.OVERALL_DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false

        // OVERALL_TIR: Overall Technical Installation Report
        DocumentType.OVERALL_TIR | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_TIR | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_TIR | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_TIR | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_TIR | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_TIR | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_TIR | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_TIR | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_TIR | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_TIR | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_TIR | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_TIR | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_TIR | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_TIR | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_TIR | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_TIR | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_TIR | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_TIR | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_TIR | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_TIR | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_TIR | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_TIR | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_TIR | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_TIR | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || true

        DocumentType.OVERALL_TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
    }

    @Unroll
    def "is document applicable for GAMP category 5"() {
        given:
        def project = PROJECT_GAMP_5

        def steps = Spy(util.PipelineSteps)
        def util = Mock(MROPipelineUtil)
        def usecase = Mock(LeVADocumentUseCase)
        def logger = Mock(Logger)
        def bbt = Mock(BitbucketTraceabilityUseCase)

        def scheduler = Spy(new LeVADocumentScheduler(project, steps, util, usecase, logger))


        expect:
        scheduler.isDocumentApplicable(documentType as String, phase, stage, repo) == result

        where:
        documentType                        | repo | phase                                   | stage                                                         || result
        // CSD: Configuration Specification
        DocumentType.CSD | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CSD | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CSD | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CSD | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || true
        DocumentType.CSD | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CSD | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CSD | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CSD | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CSD | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CSD | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CSD | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CSD | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CSD | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CSD | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CSD | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CSD | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CSD | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CSD | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CSD | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CSD | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CSD | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CSD | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CSD | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CSD | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false

        DocumentType.CSD | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CSD | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CSD | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CSD | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CSD | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CSD | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CSD | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CSD | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CSD | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CSD | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CSD | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CSD | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CSD | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CSD | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CSD | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CSD | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CSD | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CSD | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CSD | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CSD | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CSD | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CSD | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CSD | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CSD | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CSD | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CSD | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CSD | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CSD | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CSD | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CSD | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CSD | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CSD | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CSD | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CSD | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CSD | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CSD | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CSD | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CSD | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CSD | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CSD | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CSD | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CSD | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CSD | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CSD | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CSD | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CSD | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CSD | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CSD | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CSD | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CSD | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CSD | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CSD | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CSD | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CSD | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CSD | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CSD | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CSD | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CSD | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CSD | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CSD | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CSD | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CSD | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CSD | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CSD | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CSD | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CSD | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CSD | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CSD | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CSD | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CSD | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CSD | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CSD | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false

        // DTP: Software Development Testing Plan
        DocumentType.DTP | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTP | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTP | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTP | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTP | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || true
        DocumentType.DTP | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTP | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTP | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTP | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTP | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTP | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTP | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTP | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTP | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTP | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTP | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTP | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTP | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTP | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTP | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTP | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTP | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTP | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTP | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false

        DocumentType.DTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false

        // DTR: Software Development Testing Report
        DocumentType.DTR | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTR | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTR | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTR | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTR | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTR | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTR | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTR | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTR | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTR | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTR | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTR | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTR | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTR | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTR | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTR | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTR | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTR | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTR | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTR | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTR | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTR | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTR | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTR | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false

        DocumentType.DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || true
        DocumentType.DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false

        // CFTP: Combined Functional and Requirements Testing Plan
        DocumentType.CFTP | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTP | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTP | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTP | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTP | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || true
        DocumentType.CFTP | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTP | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTP | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTP | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTP | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTP | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTP | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTP | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTP | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTP | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTP | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTP | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTP | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTP | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTP | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTP | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTP | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTP | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTP | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false

        DocumentType.CFTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false

        // TRC: Combined Functional and Requirements Testing Plan
        DocumentType.TRC | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TRC | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TRC | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TRC | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TRC | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || true
        DocumentType.TRC | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TRC | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TRC | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TRC | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TRC | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TRC | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TRC | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TRC | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TRC | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TRC | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TRC | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TRC | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TRC | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TRC | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TRC | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TRC | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TRC | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TRC | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TRC | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false

        DocumentType.TRC | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TRC | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TRC | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TRC | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TRC | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TRC | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TRC | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TRC | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TRC | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TRC | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TRC | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TRC | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TRC | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TRC | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TRC | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TRC | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TRC | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TRC | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TRC | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TRC | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TRC | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TRC | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TRC | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TRC | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TRC | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TRC | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TRC | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TRC | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TRC | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TRC | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TRC | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TRC | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TRC | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TRC | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TRC | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TRC | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TRC | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TRC | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TRC | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TRC | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TRC | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TRC | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TRC | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TRC | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TRC | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TRC | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TRC | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TRC | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TRC | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TRC | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TRC | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TRC | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TRC | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TRC | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TRC | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TRC | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TRC | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TRC | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TRC | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TRC | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TRC | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TRC | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TRC | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TRC | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TRC | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TRC | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TRC | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TRC | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TRC | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TRC | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TRC | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TRC | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false

        // CFTR: Combined Functional and Requirements Testing Report
        DocumentType.CFTR | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTR | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTR | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTR | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTR | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTR | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTR | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTR | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTR | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTR | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTR | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTR | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTR | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTR | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTR | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTR | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || true
        DocumentType.CFTR | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTR | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTR | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTR | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTR | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTR | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTR | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTR | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false

        DocumentType.CFTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.CFTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.CFTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.CFTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.CFTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false

        // IVP: Configuration and Installation Testing Plan
        DocumentType.IVP | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVP | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVP | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVP | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVP | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || true
        DocumentType.IVP | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVP | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVP | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVP | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVP | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVP | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVP | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVP | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVP | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVP | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVP | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVP | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVP | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVP | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVP | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVP | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVP | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVP | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVP | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false

        DocumentType.IVP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false

        // IVR: Configuration and Installation Testing Report
        DocumentType.IVR | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVR | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVR | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVR | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVR | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVR | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVR | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVR | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVR | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVR | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVR | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVR | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVR | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVR | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVR | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVR | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || true
        DocumentType.IVR | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVR | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVR | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVR | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVR | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVR | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVR | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVR | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false

        DocumentType.IVR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.IVR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.IVR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.IVR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.IVR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false

        // RA: Risk Assessment
        DocumentType.RA | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.RA | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.RA | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.RA | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.RA | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || true
        DocumentType.RA | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.RA | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.RA | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.RA | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.RA | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.RA | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.RA | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.RA | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.RA | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.RA | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.RA | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.RA | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.RA | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.RA | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.RA | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.RA | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.RA | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.RA | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.RA | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false

        DocumentType.RA | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.RA | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.RA | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.RA | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.RA | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.RA | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.RA | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.RA | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.RA | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.RA | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.RA | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.RA | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.RA | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.RA | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.RA | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.RA | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.RA | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.RA | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.RA | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.RA | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.RA | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.RA | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.RA | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.RA | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.RA | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.RA | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.RA | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.RA | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.RA | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.RA | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.RA | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.RA | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.RA | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.RA | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.RA | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.RA | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.RA | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.RA | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.RA | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.RA | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.RA | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.RA | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.RA | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.RA | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.RA | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.RA | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.RA | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.RA | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.RA | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.RA | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.RA | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.RA | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.RA | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.RA | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.RA | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.RA | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.RA | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.RA | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.RA | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.RA | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.RA | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.RA | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.RA | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.RA | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.RA | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.RA | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.RA | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.RA | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.RA | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.RA | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.RA | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.RA | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false

        // SSDS: Software Design Specification
        DocumentType.SSDS | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.SSDS | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.SSDS | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.SSDS | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.SSDS | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || true
        DocumentType.SSDS | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.SSDS | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.SSDS | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.SSDS | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.SSDS | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.SSDS | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.SSDS | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.SSDS | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.SSDS | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.SSDS | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.SSDS | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.SSDS | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.SSDS | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.SSDS | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.SSDS | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.SSDS | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.SSDS | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.SSDS | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.SSDS | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false

        DocumentType.SSDS | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.SSDS | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.SSDS | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.SSDS | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.SSDS | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.SSDS | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.SSDS | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.SSDS | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.SSDS | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.SSDS | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.SSDS | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.SSDS | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.SSDS | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.SSDS | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.SSDS | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.SSDS | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.SSDS | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.SSDS | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.SSDS | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.SSDS | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.SSDS | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.SSDS | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.SSDS | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.SSDS | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.SSDS | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.SSDS | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.SSDS | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.SSDS | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.SSDS | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.SSDS | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.SSDS | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.SSDS | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.SSDS | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.SSDS | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.SSDS | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.SSDS | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.SSDS | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.SSDS | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.SSDS | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.SSDS | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.SSDS | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.SSDS | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.SSDS | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.SSDS | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.SSDS | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.SSDS | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.SSDS | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.SSDS | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.SSDS | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.SSDS | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.SSDS | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.SSDS | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.SSDS | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.SSDS | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.SSDS | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.SSDS | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.SSDS | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.SSDS | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.SSDS | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.SSDS | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.SSDS | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.SSDS | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.SSDS | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.SSDS | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.SSDS | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.SSDS | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.SSDS | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.SSDS | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.SSDS | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.SSDS | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.SSDS | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.SSDS | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false

        // TIP: Technical Installation Plan
        DocumentType.TIP | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIP | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIP | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIP | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIP | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || true
        DocumentType.TIP | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIP | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIP | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIP | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIP | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIP | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIP | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIP | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIP | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIP | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIP | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIP | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIP | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIP | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIP | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIP | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIP | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIP | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIP | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false

        DocumentType.TIP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIP | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIP | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIP | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false

        // TIR: Technical Installation Report
        DocumentType.TIR | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIR | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIR | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIR | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIR | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIR | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIR | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIR | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIR | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIR | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIR | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIR | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIR | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIR | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIR | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIR | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIR | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIR | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIR | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIR | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIR | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIR | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIR | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIR | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false

        DocumentType.TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || true
        DocumentType.TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || true
        DocumentType.TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false

        // OVERALL_DTR: Overall Software Development Testing Report
        DocumentType.OVERALL_DTR | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_DTR | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_DTR | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_DTR | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_DTR | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_DTR | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_DTR | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_DTR | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || true
        DocumentType.OVERALL_DTR | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_DTR | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_DTR | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_DTR | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_DTR | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_DTR | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_DTR | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_DTR | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_DTR | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_DTR | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_DTR | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_DTR | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_DTR | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_DTR | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_DTR | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_DTR | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false

        DocumentType.OVERALL_DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_DTR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_DTR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_DTR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false

        // OVERALL_TIR: Overall Technical Installation Report
        DocumentType.OVERALL_TIR | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_TIR | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_TIR | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_TIR | null | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_TIR | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_TIR | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_TIR | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_TIR | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_TIR | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_TIR | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_TIR | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_TIR | null | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_TIR | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_TIR | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_TIR | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_TIR | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_TIR | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_TIR | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_TIR | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_TIR | null | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_TIR | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_TIR | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_TIR | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_TIR | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || true

        DocumentType.OVERALL_TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_TIR | REPO_ODS_CODE    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_TIR | REPO_ODS_SERVICE | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.INIT     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.DEPLOY   | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.RELEASE  | PipelinePhaseLifecycleStage.PRE_END           || false
        DocumentType.OVERALL_TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_START        || false
        DocumentType.OVERALL_TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO  || false
        DocumentType.OVERALL_TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.POST_EXECUTE_REPO || false
        DocumentType.OVERALL_TIR | REPO_ODS_TEST    | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END           || false
    }

    def "is document applicable with invalid GAMP category"() {
        given:
        def project = createProject()
        project.data.metadata.capabilities = [[LeVADocs: [GAMPCategory: "0" ]]]

        def steps = Spy(util.PipelineSteps)
        def util = Mock(MROPipelineUtil)
        def usecase = Mock(LeVADocumentUseCase)
        def logger = Mock(Logger)
        def bbt = Mock(BitbucketTraceabilityUseCase)

        def scheduler = Spy(new LeVADocumentScheduler(project, steps, util, usecase, logger))

        // Test Parameters
        def documentType = "myDocumentType"
        def phase = "myPhase"
        def stage = PipelinePhaseLifecycleStage.POST_START
        def repo = project.repositories.first()

        when:
        scheduler.isDocumentApplicable(documentType, phase, stage)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to assert applicability of document type '${documentType}' for project '${project.key}' in phase '${phase}'. The GAMP category '0' is not supported for non-SAAS systems."

        when:
        scheduler.isDocumentApplicable(documentType, phase, stage, repo)

        then:
        e = thrown(IllegalArgumentException)
        e.message == "Error: unable to assert applicability of document type '${documentType}' for project '${project.key}' and repo '${repo.id}' in phase '${phase}'. The GAMP category '0' is not supported."
    }

    def "is document applicable in the absence of Jira"() {
        given:
        def project = PROJECT_GAMP_5_WITHOUT_JIRA

        def steps = Spy(util.PipelineSteps)
        def util = Mock(MROPipelineUtil)
        def usecase = Mock(LeVADocumentUseCase)
        def logger = Mock(Logger)
        def bbt = Mock(BitbucketTraceabilityUseCase)

        def scheduler = Spy(new LeVADocumentScheduler(project, steps, util, usecase, logger))

        expect:
        scheduler.isDocumentApplicable(documentType as String, phase, stage, repo) == result

        where:
        documentType                         | repo | phase                               | stage                                               || result
        DocumentType.CSD | null | MROPipelineUtil.PipelinePhases.INIT | PipelinePhaseLifecycleStage.PRE_END || false
    }

    def "is document applicable in the absence of repositories"() {
        given:
        def project = PROJECT_GAMP_5_WITHOUT_REPOS

        def steps = Spy(util.PipelineSteps)
        def util = Mock(MROPipelineUtil)
        def usecase = Mock(LeVADocumentUseCase)
        def logger = Mock(Logger)
        def bbt = Mock(BitbucketTraceabilityUseCase)

        def scheduler = Spy(new LeVADocumentScheduler(project, steps, util, usecase, logger))

        expect:
        scheduler.isDocumentApplicable(documentType as String, phase, stage, repo) == result

        where:
        documentType                                 | repo | phase                                   | stage                                               || result
        DocumentType.CFTR        | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END || false
        DocumentType.IVR         | null | MROPipelineUtil.PipelinePhases.TEST     | PipelinePhaseLifecycleStage.PRE_END || false
        DocumentType.OVERALL_DTR | null | MROPipelineUtil.PipelinePhases.BUILD    | PipelinePhaseLifecycleStage.PRE_END || false
        DocumentType.OVERALL_TIR | null | MROPipelineUtil.PipelinePhases.FINALIZE | PipelinePhaseLifecycleStage.PRE_END || false
    }

    def "run for GAMP category 1 in DEV with Developer Preview Mode"() {
        given:
        def project = PROJECT_GAMP_1
        project.buildParams.targetEnvironment = "dev"
        project.buildParams.targetEnvironmentToken = project.buildParams.targetEnvironment[0].toUpperCase()
        project.buildParams.version = "WIP"

        def steps = Spy(util.PipelineSteps)
        def docGen = Mock(DocGenService)
        def jenkins = Mock(JenkinsService)
        def jiraUseCase = Mock(JiraUseCase)
        def junit = Mock(JUnitTestReportsUseCase)
        def levaFiles = Mock(LeVADocumentChaptersFileService)
        def nexus = Mock(NexusService)
        def os = Mock(OpenShiftService)
        def pdf = Mock(PDFUtil)
        def sq = Mock(SonarQubeUseCase)
        def git = Mock(GitService)
        def logger = Mock(Logger)
        def bbt = Mock(BitbucketTraceabilityUseCase)


        def utilObj = new MROPipelineUtil(project, steps, git, logger)
        def util = Mock(MROPipelineUtil) {
            executeBlockAndFailBuild(_) >> { block ->
                utilObj.executeBlockAndFailBuild(block)
            }
        }

        def usecaseObj = new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, junit, levaFiles, nexus, os, pdf, sq, bbt, logger)

        def usecase = Mock(LeVADocumentUseCase) {
            getMetaClass() >> {
                return usecaseObj.getMetaClass()
            }

            getSupportedDocuments() >> {
                return usecaseObj.getSupportedDocuments()
            }
        }

        def scheduler = Spy(new LeVADocumentScheduler(project, steps, util, usecase, logger))

        // Test Parameters
        def data = [ testReportFiles: null, testResults: null ]

        when:
        scheduler.run(MROPipelineUtil.PipelinePhases.INIT, PipelinePhaseLifecycleStage.PRE_END)

        then:
        1 * usecase.invokeMethod("createCSD", [null, null] as Object[])
        0 * usecase.invokeMethod(*_)

        when:
        scheduler.run(MROPipelineUtil.PipelinePhases.BUILD, PipelinePhaseLifecycleStage.POST_START)

        then:
        1 * usecase.invokeMethod("createTIP", [null, null] as Object[])
        1 * usecase.invokeMethod("createSSDS", [null, null] as Object[])

        when:
        scheduler.run(MROPipelineUtil.PipelinePhases.DEPLOY, PipelinePhaseLifecycleStage.POST_EXECUTE_REPO, REPO_ODS_CODE)

        then:
        1 * usecase.invokeMethod("createTIR", [REPO_ODS_CODE, null] as Object[])
        0 * usecase.invokeMethod(*_)

        when:
        scheduler.run(MROPipelineUtil.PipelinePhases.DEPLOY, PipelinePhaseLifecycleStage.POST_EXECUTE_REPO, REPO_ODS_SERVICE)

        then:
        1 * usecase.invokeMethod("createTIR", [REPO_ODS_SERVICE, null] as Object[])
        0 * usecase.invokeMethod(*_)

        when:
        scheduler.run(MROPipelineUtil.PipelinePhases.BUILD, PipelinePhaseLifecycleStage.POST_START)

        then:
        1 * usecase.invokeMethod("createIVP", [null, null] as Object[])
        1 * usecase.invokeMethod('createRA', [null, null] as Object[])

        when:
        scheduler.run(MROPipelineUtil.PipelinePhases.TEST, PipelinePhaseLifecycleStage.PRE_END, [:], data)

        then:
        1 * usecase.invokeMethod("createCFTR", [[:], data] as Object[])
        1 * usecase.invokeMethod("createIVR", [[:], data] as Object[])
        1 * usecase.invokeMethod("createTCR", [[:], data] as Object[])
        1 * usecase.invokeMethod("createDIL", [[:], data] as Object[])

        when:
        scheduler.run(MROPipelineUtil.PipelinePhases.FINALIZE, PipelinePhaseLifecycleStage.PRE_END)

        then:
        1 * usecase.invokeMethod("createOverallTIR", [null, null] as Object[])
        0 * usecase.invokeMethod(*_)
    }

    def "run for GAMP category 1 in DEV"() {
        given:
        def project = PROJECT_GAMP_1
        project.buildParams.targetEnvironment = "dev"
        project.buildParams.targetEnvironmentToken = project.buildParams.targetEnvironment[0].toUpperCase()
        project.buildParams.version = "v1.0"

        def steps = Spy(util.PipelineSteps)
        def docGen = Mock(DocGenService)
        def jenkins = Mock(JenkinsService)
        def jiraUseCase = Mock(JiraUseCase)
        def junit = Mock(JUnitTestReportsUseCase)
        def levaFiles = Mock(LeVADocumentChaptersFileService)
        def nexus = Mock(NexusService)
        def os = Mock(OpenShiftService)
        def pdf = Mock(PDFUtil)
        def sq = Mock(SonarQubeUseCase)
        def git = Mock(GitService)
        def logger = Mock(Logger)
        def bbt = Mock(BitbucketTraceabilityUseCase)


        def utilObj = new MROPipelineUtil(project, steps, git, logger)
        def util = Mock(MROPipelineUtil) {
            executeBlockAndFailBuild(_) >> { block ->
                utilObj.executeBlockAndFailBuild(block)
            }
        }

        def usecaseObj = new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, junit, levaFiles, nexus, os, pdf, sq, bbt, logger)

        def usecase = Mock(LeVADocumentUseCase) {
            getMetaClass() >> {
                return usecaseObj.getMetaClass()
            }

            getSupportedDocuments() >> {
                return usecaseObj.getSupportedDocuments()
            }
        }

        def scheduler = Spy(new LeVADocumentScheduler(project, steps, util, usecase, logger))

        // Test Parameters
        def data = [ testReportFiles: null, testResults: null ]

        when:
        scheduler.run(MROPipelineUtil.PipelinePhases.INIT, PipelinePhaseLifecycleStage.PRE_END)

        then:
        1 * usecase.invokeMethod("createCSD", [null, null] as Object[])
        0 * usecase.invokeMethod(*_)

        when:
        scheduler.run(MROPipelineUtil.PipelinePhases.DEPLOY, PipelinePhaseLifecycleStage.POST_EXECUTE_REPO, REPO_ODS_CODE)

        then:
        1 * usecase.invokeMethod("createTIR", [REPO_ODS_CODE, null] as Object[])
        0 * usecase.invokeMethod(*_)

        when:
        scheduler.run(MROPipelineUtil.PipelinePhases.DEPLOY, PipelinePhaseLifecycleStage.POST_EXECUTE_REPO, REPO_ODS_SERVICE)

        then:
        1 * usecase.invokeMethod("createTIR", [REPO_ODS_SERVICE, null] as Object[])
        0 * usecase.invokeMethod(*_)

        when:
        scheduler.run(MROPipelineUtil.PipelinePhases.TEST, PipelinePhaseLifecycleStage.PRE_END, [:], data)

        then:
        0 * usecase.invokeMethod(*_)

        when:
        scheduler.run(MROPipelineUtil.PipelinePhases.FINALIZE, PipelinePhaseLifecycleStage.PRE_END)

        then:
        1 * usecase.invokeMethod("createOverallTIR", [null, null] as Object[])
        0 * usecase.invokeMethod(*_)
    }

    def "run for GAMP category 3 in DEV with Developer Preview Mode"() {
        given:
        def project = PROJECT_GAMP_3
        project.buildParams.targetEnvironment = "dev"
        project.buildParams.targetEnvironmentToken = project.buildParams.targetEnvironment[0].toUpperCase()
        project.buildParams.version = "WIP"

        def steps = Spy(util.PipelineSteps)
        def docGen = Mock(DocGenService)
        def jenkins = Mock(JenkinsService)
        def jiraUseCase = Mock(JiraUseCase)
        def junit = Mock(JUnitTestReportsUseCase)
        def levaFiles = Mock(LeVADocumentChaptersFileService)
        def nexus = Mock(NexusService)
        def os = Mock(OpenShiftService)
        def pdf = Mock(PDFUtil)
        def sq = Mock(SonarQubeUseCase)
        def git = Mock(GitService)
        def logger = Mock(Logger)
        def bbt = Mock(BitbucketTraceabilityUseCase)


        def utilObj = new MROPipelineUtil(project, steps, git, logger)
        def util = Mock(MROPipelineUtil) {
            executeBlockAndFailBuild(_) >> { block ->
                utilObj.executeBlockAndFailBuild(block)
            }
        }

        def usecaseObj = new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, junit, levaFiles, nexus, os, pdf, sq, bbt, logger)

        def usecase = Mock(LeVADocumentUseCase) {
            getMetaClass() >> {
                return usecaseObj.getMetaClass()
            }

            getSupportedDocuments() >> {
                return usecaseObj.getSupportedDocuments()
            }
        }

        def scheduler = Spy(new LeVADocumentScheduler(project, steps, util, usecase, logger))

        // Test Parameters
        def data = [ testReportFiles: null, testResults: null ]

        when:
        scheduler.run(MROPipelineUtil.PipelinePhases.INIT, PipelinePhaseLifecycleStage.PRE_END)

        then:
        1 * usecase.invokeMethod("createCSD", [null, null] as Object[])
        0 * usecase.invokeMethod(*_)

        when:
        scheduler.run(MROPipelineUtil.PipelinePhases.TEST, PipelinePhaseLifecycleStage.PRE_END, [:], data)

        then:
        1 * usecase.invokeMethod("createIVR", [[:], data] as Object[])
        1 * usecase.invokeMethod("createTCR", [[:], data] as Object[])
        1 * usecase.invokeMethod("createDIL", [[:], data] as Object[])
        1 * usecase.invokeMethod("createCFTR", [[:], data] as Object[])
        0 * usecase.invokeMethod(*_)
    }

    def "run for GAMP category 3 in DEV"() {
        given:
        def project = PROJECT_GAMP_3
        project.buildParams.targetEnvironment = "dev"
        project.buildParams.targetEnvironmentToken = project.buildParams.targetEnvironment[0].toUpperCase()
        project.buildParams.version = "v1.0"

        def steps = Spy(util.PipelineSteps)
        def docGen = Mock(DocGenService)
        def jenkins = Mock(JenkinsService)
        def jiraUseCase = Mock(JiraUseCase)
        def junit = Mock(JUnitTestReportsUseCase)
        def levaFiles = Mock(LeVADocumentChaptersFileService)
        def nexus = Mock(NexusService)
        def os = Mock(OpenShiftService)
        def pdf = Mock(PDFUtil)
        def sq = Mock(SonarQubeUseCase)
        def git = Mock(GitService)
        def logger = Mock(Logger)
        def bbt = Mock(BitbucketTraceabilityUseCase)

        def utilObj = new MROPipelineUtil(project, steps, git, logger)
        def util = Mock(MROPipelineUtil) {
            executeBlockAndFailBuild(_) >> { block ->
                utilObj.executeBlockAndFailBuild(block)
            }
        }

        def usecaseObj = new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, junit, levaFiles, nexus, os, pdf, sq, bbt, logger)

        def usecase = Mock(LeVADocumentUseCase) {
            getMetaClass() >> {
                return usecaseObj.getMetaClass()
            }

            getSupportedDocuments() >> {
                return usecaseObj.getSupportedDocuments()
            }
        }

        def scheduler = Spy(new LeVADocumentScheduler(project, steps, util, usecase, logger))

        // Test Parameters
        def data = [ testReportFiles: null, testResults: null ]

        when:
        scheduler.run(MROPipelineUtil.PipelinePhases.INIT, PipelinePhaseLifecycleStage.PRE_END)

        then:
        1 * usecase.invokeMethod("createCSD", [null, null] as Object[])
        0 * usecase.invokeMethod(*_)

        when:
        scheduler.run(MROPipelineUtil.PipelinePhases.TEST, PipelinePhaseLifecycleStage.PRE_END, [:], data)

        then:
        0 * usecase.invokeMethod(*_)
    }

    def "run for GAMP category 4 in DEV"() {
        given:
        def project = PROJECT_GAMP_4
        project.buildParams.targetEnvironment = "dev"
        project.buildParams.targetEnvironmentToken = project.buildParams.targetEnvironment[0].toUpperCase()
        project.buildParams.version = "v1.0"

        def steps = Spy(util.PipelineSteps)
        def docGen = Mock(DocGenService)
        def jenkins = Mock(JenkinsService)
        def jiraUseCase = Mock(JiraUseCase)
        def junit = Mock(JUnitTestReportsUseCase)
        def levaFiles = Mock(LeVADocumentChaptersFileService)
        def nexus = Mock(NexusService)
        def os = Mock(OpenShiftService)

        def pdf = Mock(PDFUtil)
        def sq = Mock(SonarQubeUseCase)
        def git = Mock(GitService)
        def logger = Mock(Logger)
        def bbt = Mock(BitbucketTraceabilityUseCase)


        def utilObj = new MROPipelineUtil(project, steps, git, logger)
        def util = Mock(MROPipelineUtil) {
            executeBlockAndFailBuild(_) >> { block ->
                utilObj.executeBlockAndFailBuild(block)
            }
        }

        def usecaseObj = new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, junit, levaFiles, nexus, os, pdf, sq, bbt, logger)

        def usecase = Mock(LeVADocumentUseCase) {
            getMetaClass() >> {
                return usecaseObj.getMetaClass()
            }

            getSupportedDocuments() >> {
                return usecaseObj.getSupportedDocuments()
            }

            invokeMethod(_, _) >> { method, args ->
                if (method.startsWith("create")) {
                    return "http://nexus"
                }
            }
        }

        def scheduler = Spy(new LeVADocumentScheduler(project, steps, util, usecase, logger))

        // Test Parameters
        def data = [ testReportFiles: null, testResults: null ]

        when:
        scheduler.run(MROPipelineUtil.PipelinePhases.INIT, PipelinePhaseLifecycleStage.PRE_END)

        then:
        1 * usecase.invokeMethod("createCSD", [null, null] as Object[])
        0 * usecase.invokeMethod(*_)
    }

    def "run for GAMP category 4 in DEV with Developer Preview Mode"() {
        given:
        def project = PROJECT_GAMP_4
        project.buildParams.targetEnvironment = "dev"
        project.buildParams.targetEnvironmentToken = project.buildParams.targetEnvironment[0].toUpperCase()
        project.buildParams.version = "WIP"

        def steps = Spy(util.PipelineSteps)
        def docGen = Mock(DocGenService)
        def jenkins = Mock(JenkinsService)
        def jiraUseCase = Mock(JiraUseCase)
        def junit = Mock(JUnitTestReportsUseCase)
        def levaFiles = Mock(LeVADocumentChaptersFileService)
        def nexus = Mock(NexusService)
        def os = Mock(OpenShiftService)
        def pdf = Mock(PDFUtil)
        def sq = Mock(SonarQubeUseCase)
        def git = Mock(GitService)
        def logger = Mock(Logger)
        def bbt = Mock(BitbucketTraceabilityUseCase)


        def utilObj = new MROPipelineUtil(project, steps, git, logger)
        def util = Mock(MROPipelineUtil) {
            executeBlockAndFailBuild(_) >> { block ->
                utilObj.executeBlockAndFailBuild(block)
            }
        }

        def usecaseObj = new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, junit, levaFiles, nexus, os, pdf, sq, bbt, logger)

        def usecase = Mock(LeVADocumentUseCase) {
            getMetaClass() >> {
                return usecaseObj.getMetaClass()
            }

            getSupportedDocuments() >> {
                return usecaseObj.getSupportedDocuments()
            }

            invokeMethod(_, _) >> { method, args ->
                if (method.startsWith("create")) {
                    return "http://nexus"
                }
            }
        }

        def scheduler = Spy(new LeVADocumentScheduler(project, steps, util, usecase, logger))

        // Test Parameters
        def data = [ testReportFiles: null, testResults: null ]

        when:
        scheduler.run(MROPipelineUtil.PipelinePhases.INIT, PipelinePhaseLifecycleStage.PRE_END)

        then:
        1 * usecase.invokeMethod("createCSD", [null, null] as Object[])
        0 * usecase.invokeMethod(*_)

        when:
        scheduler.run(MROPipelineUtil.PipelinePhases.BUILD, PipelinePhaseLifecycleStage.POST_START)

        then:
        1 * usecase.invokeMethod("createCFTP", [null, null] as Object[])
        1 * usecase.invokeMethod("createIVP", [null, null] as Object[])
        1 * usecase.invokeMethod("createTCP", [null, null] as Object[])
        1 * usecase.invokeMethod("createRA", [null, null] as Object[])
        1 * usecase.invokeMethod('createTIP', [null, null] as Object[])
        1 * usecase.invokeMethod("createTRC", [null, null] as Object[])
        1 * usecase.invokeMethod("createSSDS", [null, null] as Object[])

        0 * usecase.invokeMethod(*_)

        when:
        scheduler.run(MROPipelineUtil.PipelinePhases.TEST, PipelinePhaseLifecycleStage.PRE_END, [:], data)

        then:
        1 * usecase.invokeMethod("createCFTR", [[:], data] as Object[])
        1 * usecase.invokeMethod("createIVR", [[:], data] as Object[])
        1 * usecase.invokeMethod("createDIL", [[:], data] as Object[])
        1 * usecase.invokeMethod("createTCR", [[:], data] as Object[])
        0 * usecase.invokeMethod(*_)
    }

    def "run for GAMP category 5 in DEV"() {
        given:
        def project = PROJECT_GAMP_5
        project.buildParams.targetEnvironment = "dev"
        project.buildParams.targetEnvironmentToken = project.buildParams.targetEnvironment[0].toUpperCase()
        project.buildParams.version = "v1.0"

        def steps = Spy(util.PipelineSteps)
        def docGen = Mock(DocGenService)
        def jenkins = Mock(JenkinsService)
        def jiraUseCase = Mock(JiraUseCase)
        def junit = Mock(JUnitTestReportsUseCase)
        def levaFiles = Mock(LeVADocumentChaptersFileService)
        def nexus = Mock(NexusService)
        def os = Mock(OpenShiftService)
        def pdf = Mock(PDFUtil)
        def sq = Mock(SonarQubeUseCase)
        def git = Mock(GitService)
        def logger = Mock(Logger)
        def bbt = Mock(BitbucketTraceabilityUseCase)


        def utilObj = new MROPipelineUtil(project, steps, git, logger)
        def util = Mock(MROPipelineUtil) {
            executeBlockAndFailBuild(_) >> { block ->
                utilObj.executeBlockAndFailBuild(block)
            }
        }

        def usecaseObj = new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, junit, levaFiles, nexus, os, pdf, sq, bbt, logger)

        def usecase = Mock(LeVADocumentUseCase) {
            getMetaClass() >> {
                return usecaseObj.getMetaClass()
            }

            getSupportedDocuments() >> {
                return usecaseObj.getSupportedDocuments()
            }
        }

        def scheduler = Spy(new LeVADocumentScheduler(project, steps, util, usecase, logger))

        // Test Parameters
        def data = [ testReportFiles: null, testResults: null ]

        when:
        scheduler.run(MROPipelineUtil.PipelinePhases.INIT, PipelinePhaseLifecycleStage.PRE_END)

        then:
        1 * usecase.invokeMethod("createCSD", [null, null] as Object[])
        0 * usecase.invokeMethod(*_)

        when:
        scheduler.run(MROPipelineUtil.PipelinePhases.BUILD, PipelinePhaseLifecycleStage.POST_START)

        then:
        1 * usecase.invokeMethod("createDTP", [null, null] as Object[])
        1 * usecase.invokeMethod('createRA', [null, null] as Object[])
        1 * usecase.invokeMethod('createCFTP', [null, null] as Object[])
        1 * usecase.invokeMethod("createIVP", [null, null] as Object[])
        1 * usecase.invokeMethod("createTCP", [null, null] as Object[])
        1 * usecase.invokeMethod('createTIP', [null, null] as Object[])
        1 * usecase.invokeMethod('createSSDS', [null, null] as Object[])
        1 * usecase.invokeMethod('createTRC', [null, null] as Object[])
        0 * usecase.invokeMethod(*_)

        when:
        scheduler.run(MROPipelineUtil.PipelinePhases.BUILD, PipelinePhaseLifecycleStage.PRE_END)

        then:
        1 * usecase.invokeMethod('createOverallDTR', [null, null])
        0 * usecase.invokeMethod(*_)

        when:
        scheduler.run(MROPipelineUtil.PipelinePhases.BUILD, PipelinePhaseLifecycleStage.POST_EXECUTE_REPO, REPO_ODS_CODE, data)

        then:
        1 * usecase.invokeMethod("createDTR", [REPO_ODS_CODE, data] as Object[])
        0 * usecase.invokeMethod(*_)

        when:
        scheduler.run(MROPipelineUtil.PipelinePhases.BUILD, PipelinePhaseLifecycleStage.PRE_END, REPO_ODS_CODE)

        then:
        0 * usecase.invokeMethod(*_)

        when:
        scheduler.run(MROPipelineUtil.PipelinePhases.BUILD, PipelinePhaseLifecycleStage.PRE_END, REPO_ODS_TEST)

        then:
        0 * usecase.invokeMethod(*_)

        when:
        scheduler.run(MROPipelineUtil.PipelinePhases.DEPLOY, PipelinePhaseLifecycleStage.POST_EXECUTE_REPO, REPO_ODS_CODE)

        then:
        1 * usecase.invokeMethod("createTIR", [REPO_ODS_CODE, null] as Object[])
        0 * usecase.invokeMethod(*_)

        when:
        scheduler.run(MROPipelineUtil.PipelinePhases.DEPLOY, PipelinePhaseLifecycleStage.POST_EXECUTE_REPO, REPO_ODS_SERVICE)

        then:
        1 * usecase.invokeMethod("createTIR", [REPO_ODS_SERVICE, null] as Object[])
        0 * usecase.invokeMethod(*_)

        when:
        scheduler.run(MROPipelineUtil.PipelinePhases.FINALIZE, PipelinePhaseLifecycleStage.PRE_END)

        then:
        1 * usecase.invokeMethod("createOverallTIR", [null, null] as Object[])
        0 * usecase.invokeMethod(*_)
    }

    def "run for GAMP category 5 in DEV with Developer Preview Mode"() {
        given:
        def project = PROJECT_GAMP_5
        project.buildParams.targetEnvironment = "dev"
        project.buildParams.targetEnvironmentToken = project.buildParams.targetEnvironment[0].toUpperCase()
        project.buildParams.version = "WIP"

        def steps = Spy(util.PipelineSteps)
        def docGen = Mock(DocGenService)
        def jenkins = Mock(JenkinsService)
        def jiraUseCase = Mock(JiraUseCase)
        def junit = Mock(JUnitTestReportsUseCase)
        def levaFiles = Mock(LeVADocumentChaptersFileService)
        def nexus = Mock(NexusService)
        def os = Mock(OpenShiftService)
        def pdf = Mock(PDFUtil)
        def sq = Mock(SonarQubeUseCase)
        def git = Mock(GitService)
        def logger = Mock(Logger)
        def bbt = Mock(BitbucketTraceabilityUseCase)


        def utilObj = new MROPipelineUtil(project, steps, git, logger)
        def util = Mock(MROPipelineUtil) {
            executeBlockAndFailBuild(_) >> { block ->
                utilObj.executeBlockAndFailBuild(block)
            }
        }

        def usecaseObj = new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, junit, levaFiles, nexus, os, pdf, sq, bbt, logger)

        def usecase = Mock(LeVADocumentUseCase) {
            getMetaClass() >> {
                return usecaseObj.getMetaClass()
            }

            getSupportedDocuments() >> {
                return usecaseObj.getSupportedDocuments()
            }
        }

        def scheduler = Spy(new LeVADocumentScheduler(project, steps, util, usecase, logger))

        // Test Parameters
        def data = [ testReportFiles: null, testResults: null ]

        when:
        scheduler.run(MROPipelineUtil.PipelinePhases.INIT, PipelinePhaseLifecycleStage.PRE_END)

        then:
        1 * usecase.invokeMethod("createCSD", [null, null] as Object[])
        0 * usecase.invokeMethod(*_)

        when:
        scheduler.run(MROPipelineUtil.PipelinePhases.BUILD, PipelinePhaseLifecycleStage.POST_START)

        then:
        1 * usecase.invokeMethod("createDTP", [null, null] as Object[])
        1 * usecase.invokeMethod('createRA', [null, null] as Object[])
        1 * usecase.invokeMethod("createTIP", [null, null] as Object[])
        1 * usecase.invokeMethod('createCFTP', [null, null] as Object[])
        1 * usecase.invokeMethod('createIVP', [null, null] as Object[])
        1 * usecase.invokeMethod('createTCP', [null, null] as Object[])
        1 * usecase.invokeMethod("createTRC", [null, null] as Object[])
        1 * usecase.invokeMethod("createSSDS", [null, null] as Object[])
        0 * usecase.invokeMethod(*_)

        when:
        scheduler.run(MROPipelineUtil.PipelinePhases.BUILD, PipelinePhaseLifecycleStage.PRE_END)

        then:
        1 * usecase.invokeMethod("createOverallDTR", [null, null] as Object[])
        0 * usecase.invokeMethod(*_)

        when:
        scheduler.run(MROPipelineUtil.PipelinePhases.BUILD, PipelinePhaseLifecycleStage.POST_EXECUTE_REPO, REPO_ODS_CODE, data)

        then:
        1 * usecase.invokeMethod("createDTR", [REPO_ODS_CODE, data] as Object[])
        0 * usecase.invokeMethod(*_)

        when:
        scheduler.run(MROPipelineUtil.PipelinePhases.BUILD, PipelinePhaseLifecycleStage.PRE_END, REPO_ODS_CODE)

        then:
        0 * usecase.invokeMethod(*_)

        when:
        scheduler.run(MROPipelineUtil.PipelinePhases.BUILD, PipelinePhaseLifecycleStage.PRE_END, REPO_ODS_TEST)

        then:
        0 * usecase.invokeMethod(*_)

        when:
        scheduler.run(MROPipelineUtil.PipelinePhases.DEPLOY, PipelinePhaseLifecycleStage.POST_START)

        then:

        0 * usecase.invokeMethod(*_)

        when:
        scheduler.run(MROPipelineUtil.PipelinePhases.DEPLOY, PipelinePhaseLifecycleStage.POST_EXECUTE_REPO, REPO_ODS_CODE)

        then:
        1 * usecase.invokeMethod("createTIR", [REPO_ODS_CODE, null] as Object[])
        0 * usecase.invokeMethod(*_)

        when:
        scheduler.run(MROPipelineUtil.PipelinePhases.DEPLOY, PipelinePhaseLifecycleStage.POST_EXECUTE_REPO, REPO_ODS_SERVICE)

        then:
        1 * usecase.invokeMethod("createTIR", [REPO_ODS_SERVICE, null] as Object[])
        0 * usecase.invokeMethod(*_)

        when:
        scheduler.run(MROPipelineUtil.PipelinePhases.TEST, PipelinePhaseLifecycleStage.PRE_END, [:], data)

        then:
        1 * usecase.invokeMethod("createDIL", [[:], data] as Object[])
        1 * usecase.invokeMethod("createCFTR", [[:], data] as Object[])
        1 * usecase.invokeMethod("createIVR", [[:], data] as Object[])
        1 * usecase.invokeMethod("createTCR", [[:], data] as Object[])
        0 * usecase.invokeMethod(*_)

        when:
        scheduler.run(MROPipelineUtil.PipelinePhases.FINALIZE, PipelinePhaseLifecycleStage.PRE_END)

        then:
        1 * usecase.invokeMethod("createOverallTIR", [null, null] as Object[])
        0 * usecase.invokeMethod(*_)
    }

    def "run for GAMP category 1 in QA"() {
        given:
        def project = PROJECT_GAMP_1
        project.buildParams.targetEnvironment = "qa"
        project.buildParams.targetEnvironmentToken = project.buildParams.targetEnvironment[0].toUpperCase()

        def steps = Spy(util.PipelineSteps)
        def docGen = Mock(DocGenService)
        def jenkins = Mock(JenkinsService)
        def jiraUseCase = Mock(JiraUseCase)
        def junit = Mock(JUnitTestReportsUseCase)
        def levaFiles = Mock(LeVADocumentChaptersFileService)
        def nexus = Mock(NexusService)
        def os = Mock(OpenShiftService)
        def pdf = Mock(PDFUtil)
        def sq = Mock(SonarQubeUseCase)
        def git = Mock(GitService)
        def logger = Mock(Logger)
        def bbt = Mock(BitbucketTraceabilityUseCase)


        def utilObj = new MROPipelineUtil(project, steps, git, logger)
        def util = Mock(MROPipelineUtil) {
            executeBlockAndFailBuild(_) >> { block ->
                utilObj.executeBlockAndFailBuild(block)
            }
        }

        def usecaseObj = new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, junit, levaFiles, nexus, os, pdf, sq, bbt, logger)

        def usecase = Mock(LeVADocumentUseCase) {
            getMetaClass() >> {
                return usecaseObj.getMetaClass()
            }

            getSupportedDocuments() >> {
                return usecaseObj.getSupportedDocuments()
            }
        }

        def scheduler = Spy(new LeVADocumentScheduler(project, steps, util, usecase, logger))

        // Test Parameters
        def data = [ testReportFiles: null, testResults: null ]

        when:
        scheduler.run(MROPipelineUtil.PipelinePhases.DEPLOY, PipelinePhaseLifecycleStage.POST_START)

        then:
        0 * usecase.invokeMethod(*_)

        when:
        scheduler.run(MROPipelineUtil.PipelinePhases.DEPLOY, PipelinePhaseLifecycleStage.POST_EXECUTE_REPO, REPO_ODS_CODE)

        then:
        1 * usecase.invokeMethod("createTIR", [REPO_ODS_CODE, null] as Object[])
        0 * usecase.invokeMethod(*_)

        when:
        scheduler.run(MROPipelineUtil.PipelinePhases.DEPLOY, PipelinePhaseLifecycleStage.POST_EXECUTE_REPO, REPO_ODS_SERVICE)

        then:
        1 * usecase.invokeMethod("createTIR", [REPO_ODS_SERVICE, null] as Object[])
        0 * usecase.invokeMethod(*_)

        when:
        scheduler.run(MROPipelineUtil.PipelinePhases.TEST, PipelinePhaseLifecycleStage.POST_START)

        then:
        0 * usecase.invokeMethod(*_)

        when:
        scheduler.run(MROPipelineUtil.PipelinePhases.TEST, PipelinePhaseLifecycleStage.PRE_END, [:], data)

        then:
        1 * usecase.invokeMethod("createIVR", [[:], data] as Object[])
        1 * usecase.invokeMethod("createCFTR", [[:], data] as Object[])
        1 * usecase.invokeMethod("createDIL", [[:], data] as Object[])
        1 * usecase.invokeMethod("createTCR", [[:], data] as Object[])
        0 * usecase.invokeMethod(*_)
    }

    def "run for GAMP category 3 in QA"() {
        given:
        def project = PROJECT_GAMP_3
        project.buildParams.targetEnvironment = "qa"
        project.buildParams.targetEnvironmentToken = project.buildParams.targetEnvironment[0].toUpperCase()

        def steps = Spy(util.PipelineSteps)
        def docGen = Mock(DocGenService)
        def jenkins = Mock(JenkinsService)
        def jiraUseCase = Mock(JiraUseCase)
        def junit = Mock(JUnitTestReportsUseCase)
        def levaFiles = Mock(LeVADocumentChaptersFileService)
        def nexus = Mock(NexusService)
        def os = Mock(OpenShiftService)
        def pdf = Mock(PDFUtil)
        def sq = Mock(SonarQubeUseCase)
        def git = Mock(GitService)
        def logger = Mock(Logger)
        def bbt = Mock(BitbucketTraceabilityUseCase)


        def utilObj = new MROPipelineUtil(project, steps, git, logger)
        def util = Mock(MROPipelineUtil) {
            executeBlockAndFailBuild(_) >> { block ->
                utilObj.executeBlockAndFailBuild(block)
            }
        }

        def usecaseObj = new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, junit, levaFiles, nexus, os, pdf, sq, bbt, logger)

        def usecase = Mock(LeVADocumentUseCase) {
            getMetaClass() >> {
                return usecaseObj.getMetaClass()
            }

            getSupportedDocuments() >> {
                return usecaseObj.getSupportedDocuments()
            }
        }

        def scheduler = Spy(new LeVADocumentScheduler(project, steps, util, usecase, logger))

        // Test Parameters
        def data = [ testReportFiles: null, testResults: null ]

        when:
        scheduler.run(MROPipelineUtil.PipelinePhases.TEST, PipelinePhaseLifecycleStage.POST_START)

        then:
        0 * usecase.invokeMethod(*_)

        when:
        scheduler.run(MROPipelineUtil.PipelinePhases.TEST, PipelinePhaseLifecycleStage.PRE_END, [:], data)

        then:
        1 * usecase.invokeMethod("createIVR", [[:], data] as Object[])
        1 * usecase.invokeMethod("createDIL", [[:], data] as Object[])
        1 * usecase.invokeMethod("createTCR", [[:], data] as Object[])
        1 * usecase.invokeMethod("createCFTR", [[:], data] as Object[])
        0 * usecase.invokeMethod(*_)
    }

    def "run for GAMP category 4 in QA"() {
        given:
        def project = PROJECT_GAMP_4
        project.buildParams.targetEnvironment = "qa"
        project.buildParams.targetEnvironmentToken = project.buildParams.targetEnvironment[0].toUpperCase()

        def steps = Spy(util.PipelineSteps)
        def docGen = Mock(DocGenService)
        def jenkins = Mock(JenkinsService)
        def jiraUseCase = Mock(JiraUseCase)
        def junit = Mock(JUnitTestReportsUseCase)
        def levaFiles = Mock(LeVADocumentChaptersFileService)
        def nexus = Mock(NexusService)
        def os = Mock(OpenShiftService)
        def pdf = Mock(PDFUtil)
        def sq = Mock(SonarQubeUseCase)
        def git = Mock(GitService)
        def logger = Mock(Logger)
        def bbt = Mock(BitbucketTraceabilityUseCase)


        def utilObj = new MROPipelineUtil(project, steps, git, logger)
        def util = Mock(MROPipelineUtil) {
            executeBlockAndFailBuild(_) >> { block ->
                utilObj.executeBlockAndFailBuild(block)
            }
        }

        def usecaseObj = new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, junit, levaFiles, nexus, os, pdf, sq, bbt, logger)

        def usecase = Mock(LeVADocumentUseCase) {
            getMetaClass() >> {
                return usecaseObj.getMetaClass()
            }

            getSupportedDocuments() >> {
                return usecaseObj.getSupportedDocuments()
            }
        }

        def scheduler = Spy(new LeVADocumentScheduler(project, steps, util, usecase, logger))

        // Test Parameters
        def data = [ testReportFiles: null, testResults: null ]

        when:
        scheduler.run(MROPipelineUtil.PipelinePhases.TEST, PipelinePhaseLifecycleStage.POST_START)

        then:
        0 * usecase.invokeMethod(*_)

        when:
        scheduler.run(MROPipelineUtil.PipelinePhases.TEST, PipelinePhaseLifecycleStage.PRE_END, [:], data)

        then:
        1 * usecase.invokeMethod("createIVR", [[:], data] as Object[])
        1 * usecase.invokeMethod("createDIL", [[:], data] as Object[])
        1 * usecase.invokeMethod("createCFTR", [[:], data] as Object[])
        1 * usecase.invokeMethod("createTCR", [[:], data] as Object[])
        0 * usecase.invokeMethod(*_)
    }

    def "run for GAMP category 5 in QA"() {
        given:
        def project = PROJECT_GAMP_5
        project.buildParams.targetEnvironment = "qa"
        project.buildParams.targetEnvironmentToken = project.buildParams.targetEnvironment[0].toUpperCase()

        def steps = Spy(util.PipelineSteps)
        def docGen = Mock(DocGenService)
        def jenkins = Mock(JenkinsService)
        def jiraUseCase = Mock(JiraUseCase)
        def junit = Mock(JUnitTestReportsUseCase)
        def levaFiles = Mock(LeVADocumentChaptersFileService)
        def nexus = Mock(NexusService)
        def os = Mock(OpenShiftService)
        def pdf = Mock(PDFUtil)
        def sq = Mock(SonarQubeUseCase)
        def git = Mock(GitService)
        def logger = Mock(Logger)
        def bbt = Mock(BitbucketTraceabilityUseCase)


        def utilObj = new MROPipelineUtil(project, steps, git, logger)
        def util = Mock(MROPipelineUtil) {
            executeBlockAndFailBuild(_) >> { block ->
                utilObj.executeBlockAndFailBuild(block)
            }
        }

        def usecaseObj = new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, junit, levaFiles, nexus, os, pdf, sq, bbt, logger)

        def usecase = Mock(LeVADocumentUseCase) {
            getMetaClass() >> {
                return usecaseObj.getMetaClass()
            }

            getSupportedDocuments() >> {
                return usecaseObj.getSupportedDocuments()
            }
        }

        def scheduler = Spy(new LeVADocumentScheduler(project, steps, util, usecase, logger))

        // Test Parameters
        def data = [ testReportFiles: null, testResults: null ]

        when:
        scheduler.run(MROPipelineUtil.PipelinePhases.DEPLOY, PipelinePhaseLifecycleStage.POST_START)

        then:
        0 * usecase.invokeMethod(*_)

        when:
        scheduler.run(MROPipelineUtil.PipelinePhases.DEPLOY, PipelinePhaseLifecycleStage.POST_EXECUTE_REPO, REPO_ODS_CODE)

        then:
        1 * usecase.invokeMethod("createTIR", [REPO_ODS_CODE, null] as Object[])
        0 * usecase.invokeMethod(*_)

        when:
        scheduler.run(MROPipelineUtil.PipelinePhases.DEPLOY, PipelinePhaseLifecycleStage.POST_EXECUTE_REPO, REPO_ODS_SERVICE)

        then:
        1 * usecase.invokeMethod("createTIR", [REPO_ODS_SERVICE, null] as Object[])
        0 * usecase.invokeMethod(*_)

        when:
        scheduler.run(MROPipelineUtil.PipelinePhases.TEST, PipelinePhaseLifecycleStage.POST_START)

        then:
        0 * usecase.invokeMethod(*_)

        when:
        scheduler.run(MROPipelineUtil.PipelinePhases.TEST, PipelinePhaseLifecycleStage.PRE_END, [:], data)

        then:
        1 * usecase.invokeMethod("createIVR", [[:], data] as Object[])
        1 * usecase.invokeMethod("createDIL", [[:], data] as Object[])
        1 * usecase.invokeMethod("createCFTR", [[:], data] as Object[])
        1 * usecase.invokeMethod("createTCR", [[:], data] as Object[])
        0 * usecase.invokeMethod(*_)
    }

    def "run for GAMP category 1 in PROD"() {
        given:
        def project = PROJECT_GAMP_1
        project.buildParams.targetEnvironment = "prod"
        project.buildParams.targetEnvironmentToken = project.buildParams.targetEnvironment[0].toUpperCase()

        def steps = Spy(util.PipelineSteps)
        def docGen = Mock(DocGenService)
        def jenkins = Mock(JenkinsService)
        def jiraUseCase = Mock(JiraUseCase)
        def junit = Mock(JUnitTestReportsUseCase)
        def levaFiles = Mock(LeVADocumentChaptersFileService)
        def nexus = Mock(NexusService)
        def os = Mock(OpenShiftService)
        def pdf = Mock(PDFUtil)
        def sq = Mock(SonarQubeUseCase)
        def git = Mock(GitService)
        def logger = Mock(Logger)
        def bbt = Mock(BitbucketTraceabilityUseCase)


        def utilObj = new MROPipelineUtil(project, steps, git, logger)
        def util = Mock(MROPipelineUtil) {
            executeBlockAndFailBuild(_) >> { block ->
                utilObj.executeBlockAndFailBuild(block)
            }
        }

        def usecaseObj = new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, junit, levaFiles, nexus, os, pdf, sq, bbt, logger)

        def usecase = Mock(LeVADocumentUseCase) {
            getMetaClass() >> {
                return usecaseObj.getMetaClass()
            }

            getSupportedDocuments() >> {
                return usecaseObj.getSupportedDocuments()
            }
        }

        def scheduler = Spy(new LeVADocumentScheduler(project, steps, util, usecase, logger))

        // Test Parameters
        def data = [ testReportFiles: null, testResults: null ]

        when:
        scheduler.run(MROPipelineUtil.PipelinePhases.DEPLOY, PipelinePhaseLifecycleStage.POST_EXECUTE_REPO, REPO_ODS_CODE)

        then:
        1 * usecase.invokeMethod("createTIR", [REPO_ODS_CODE, null] as Object[])
        0 * usecase.invokeMethod(*_)

        when:
        scheduler.run(MROPipelineUtil.PipelinePhases.DEPLOY, PipelinePhaseLifecycleStage.POST_EXECUTE_REPO, REPO_ODS_SERVICE)

        then:
        1 * usecase.invokeMethod("createTIR", [REPO_ODS_SERVICE, null] as Object[])
        0 * usecase.invokeMethod(*_)

        when:
        scheduler.run(MROPipelineUtil.PipelinePhases.TEST, PipelinePhaseLifecycleStage.PRE_END, [:], data)

        then:
        1 * usecase.invokeMethod("createIVR", [[:], data] as Object[])
        0 * usecase.invokeMethod(*_)
    }

    def "run for GAMP category 3 in PROD"() {
        given:
        def project = PROJECT_GAMP_3
        project.buildParams.targetEnvironment = "prod"
        project.buildParams.targetEnvironmentToken = project.buildParams.targetEnvironment[0].toUpperCase()

        def steps = Spy(util.PipelineSteps)
        def docGen = Mock(DocGenService)
        def jenkins = Mock(JenkinsService)
        def jiraUseCase = Mock(JiraUseCase)
        def junit = Mock(JUnitTestReportsUseCase)
        def levaFiles = Mock(LeVADocumentChaptersFileService)
        def nexus = Mock(NexusService)
        def os = Mock(OpenShiftService)
        def pdf = Mock(PDFUtil)
        def sq = Mock(SonarQubeUseCase)
        def git = Mock(GitService)
        def logger = Mock(Logger)
        def bbt = Mock(BitbucketTraceabilityUseCase)


        def utilObj = new MROPipelineUtil(project, steps, git, logger)
        def util = Mock(MROPipelineUtil) {
            executeBlockAndFailBuild(_) >> { block ->
                utilObj.executeBlockAndFailBuild(block)
            }
        }

        def usecaseObj = new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, junit, levaFiles, nexus, os, pdf, sq, bbt, logger)

        def usecase = Mock(LeVADocumentUseCase) {
            getMetaClass() >> {
                return usecaseObj.getMetaClass()
            }

            getSupportedDocuments() >> {
                return usecaseObj.getSupportedDocuments()
            }
        }

        def scheduler = Spy(new LeVADocumentScheduler(project, steps, util, usecase, logger))

        // Test Parameters
        def data = [ testReportFiles: null, testResults: null ]

        when:
        scheduler.run(MROPipelineUtil.PipelinePhases.TEST, PipelinePhaseLifecycleStage.PRE_END, [:], data)

        then:
        1 * usecase.invokeMethod("createIVR", [[:], data] as Object[])
    }

    def "run for GAMP category 4 in PROD"() {
        given:
        def project = PROJECT_GAMP_4
        project.buildParams.targetEnvironment = "prod"
        project.buildParams.targetEnvironmentToken = project.buildParams.targetEnvironment[0].toUpperCase()

        def steps = Spy(util.PipelineSteps)
        def docGen = Mock(DocGenService)
        def jenkins = Mock(JenkinsService)
        def jiraUseCase = Mock(JiraUseCase)
        def junit = Mock(JUnitTestReportsUseCase)
        def levaFiles = Mock(LeVADocumentChaptersFileService)
        def nexus = Mock(NexusService)
        def os = Mock(OpenShiftService)
        def pdf = Mock(PDFUtil)
        def sq = Mock(SonarQubeUseCase)
        def git = Mock(GitService)
        def logger = Mock(Logger)
        def bbt = Mock(BitbucketTraceabilityUseCase)


        def utilObj = new MROPipelineUtil(project, steps, git, logger)
        def util = Mock(MROPipelineUtil) {
            executeBlockAndFailBuild(_) >> { block ->
                utilObj.executeBlockAndFailBuild(block)
            }
        }

        def usecaseObj = new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, junit, levaFiles, nexus, os, pdf, sq, bbt, logger)

        def usecase = Mock(LeVADocumentUseCase) {
            getMetaClass() >> {
                return usecaseObj.getMetaClass()
            }

            getSupportedDocuments() >> {
                return usecaseObj.getSupportedDocuments()
            }
        }

        def scheduler = Spy(new LeVADocumentScheduler(project, steps, util, usecase, logger))

        // Test Parameters
        def data = [ testReportFiles: null, testResults: null ]

        when:
        scheduler.run(MROPipelineUtil.PipelinePhases.TEST, PipelinePhaseLifecycleStage.PRE_END, [:], data)

        then:
        1 * usecase.invokeMethod("createIVR", [[:], data] as Object[])
        0 * usecase.invokeMethod(*_)
    }

    def "run for GAMP category 5 in PROD"() {
        given:
        def project = PROJECT_GAMP_5
        project.buildParams.targetEnvironment = "prod"
        project.buildParams.targetEnvironmentToken = project.buildParams.targetEnvironment[0].toUpperCase()

        def steps = Spy(util.PipelineSteps)
        def docGen = Mock(DocGenService)
        def jenkins = Mock(JenkinsService)
        def jiraUseCase = Mock(JiraUseCase)
        def junit = Mock(JUnitTestReportsUseCase)
        def levaFiles = Mock(LeVADocumentChaptersFileService)
        def nexus = Mock(NexusService)
        def os = Mock(OpenShiftService)
        def pdf = Mock(PDFUtil)
        def sq = Mock(SonarQubeUseCase)
        def git = Mock(GitService)
        def logger = Mock(Logger)
        def bbt = Mock(BitbucketTraceabilityUseCase)


        def utilObj = new MROPipelineUtil(project, steps, git, logger)
        def util = Mock(MROPipelineUtil) {
            executeBlockAndFailBuild(_) >> { block ->
                utilObj.executeBlockAndFailBuild(block)
            }
        }

        def usecaseObj = new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, junit, levaFiles, nexus, os, pdf, sq, bbt, logger)

        def usecase = Mock(LeVADocumentUseCase) {
            getMetaClass() >> {
                return usecaseObj.getMetaClass()
            }

            getSupportedDocuments() >> {
                return usecaseObj.getSupportedDocuments()
            }
        }

        def scheduler = Spy(new LeVADocumentScheduler(project, steps, util, usecase, logger))

        // Test Parameters
        def data = [ testReportFiles: null, testResults: null ]

        when:
        scheduler.run(MROPipelineUtil.PipelinePhases.DEPLOY, PipelinePhaseLifecycleStage.POST_EXECUTE_REPO, REPO_ODS_CODE)

        then:
        1 * usecase.invokeMethod("createTIR", [REPO_ODS_CODE, null] as Object[])
        0 * usecase.invokeMethod(*_)

        when:
        scheduler.run(MROPipelineUtil.PipelinePhases.DEPLOY, PipelinePhaseLifecycleStage.POST_EXECUTE_REPO, REPO_ODS_SERVICE)

        then:
        1 * usecase.invokeMethod("createTIR", [REPO_ODS_SERVICE, null] as Object[])
        0 * usecase.invokeMethod(*_)

        when:
        scheduler.run(MROPipelineUtil.PipelinePhases.TEST, PipelinePhaseLifecycleStage.PRE_END, [:], data)

        then:
        1 * usecase.invokeMethod("createIVR", [[:], data] as Object[])
        0 * usecase.invokeMethod(*_)
    }

    def "in Developer Preview Mode all documents types are applicable"() {
        given:
        def project = createProject()
        project.buildParams.version = "WIP"

        def steps = Spy(util.PipelineSteps)
        def util = Mock(MROPipelineUtil)
        def docGen = Mock(DocGenService)
        def jenkins = Mock(JenkinsService)
        def jiraUseCase = Mock(JiraUseCase)
        def junit = Mock(JUnitTestReportsUseCase)
        def levaFiles = Mock(LeVADocumentChaptersFileService)
        def nexus = Mock(NexusService)
        def os = Mock(OpenShiftService)
        def pdf = Mock(PDFUtil)
        def sq = Mock(SonarQubeUseCase)
        def logger = Mock(Logger)
        def bbt = Mock(BitbucketTraceabilityUseCase)


        def usecaseObj = new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, junit, levaFiles, nexus, os, pdf, sq, bbt, logger)

        def usecase = Mock(LeVADocumentUseCase) {
            getMetaClass() >> {
                return usecaseObj.getMetaClass()
            }

            getSupportedDocuments() >> {
                return usecaseObj.getSupportedDocuments()
            }
        }

        def scheduler = Spy(new LeVADocumentScheduler(project, steps, util, usecase, logger))

        def result = []
        def expected = []

        when:
        for (DocumentType documentType : DocumentType.values()) {
            result.add(scheduler.isDocumentApplicableForEnvironment(documentType.name(), "D"))
            expected.add(true)
        }

        then:
        _ * scheduler.isDocumentApplicableForEnvironment(_ as String, "D")
        expected == result
    }

    def "in QA environment only specific types are applicable"() {
        given:
        def project = createProject()

        def steps = Spy(util.PipelineSteps)
        def util = Mock(MROPipelineUtil)
        def docGen = Mock(DocGenService)
        def jenkins = Mock(JenkinsService)
        def jiraUseCase = Mock(JiraUseCase)
        def junit = Mock(JUnitTestReportsUseCase)
        def levaFiles = Mock(LeVADocumentChaptersFileService)
        def nexus = Mock(NexusService)
        def os = Mock(OpenShiftService)
        def pdf = Mock(PDFUtil)
        def sq = Mock(SonarQubeUseCase)
        def logger = Mock(Logger)
        def bbt = Mock(BitbucketTraceabilityUseCase)


        def usecaseObj = new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, junit, levaFiles, nexus, os, pdf, sq, bbt, logger)

        def usecase = Mock(LeVADocumentUseCase) {
            getMetaClass() >> {
                return usecaseObj.getMetaClass()
            }

            getSupportedDocuments() >> {
                return usecaseObj.getSupportedDocuments()
            }
        }

        def scheduler = Spy(new LeVADocumentScheduler(project, steps, util, usecase, logger))

        // Test Parameters
        def qTypes = [
            DocumentType.TIR as String,
            DocumentType.IVR as String,
            DocumentType.CFTR as String,
            DocumentType.TCR as String,
            DocumentType.DIL as String,
        ]

        def result = []
        def expected = []

        when:
        for (DocumentType documentType : qTypes) {
            result.add(scheduler.isDocumentApplicableForEnvironment(documentType.name(), "Q"))
            expected.add(true)
        }

        then:
        _ * scheduler.isDocumentApplicableForEnvironment(_ as String, "Q")
        expected == result
    }

    def "in PROD environment only specific types are applicable"() {
        given:
        def project = createProject()

        def steps = Spy(util.PipelineSteps)
        def util = Mock(MROPipelineUtil)
        def docGen = Mock(DocGenService)
        def jenkins = Mock(JenkinsService)
        def jiraUseCase = Mock(JiraUseCase)
        def junit = Mock(JUnitTestReportsUseCase)
        def levaFiles = Mock(LeVADocumentChaptersFileService)
        def nexus = Mock(NexusService)
        def os = Mock(OpenShiftService)
        def pdf = Mock(PDFUtil)
        def sq = Mock(SonarQubeUseCase)
        def logger = Mock(Logger)
        def bbt = Mock(BitbucketTraceabilityUseCase)


        def usecaseObj = new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, junit, levaFiles, nexus, os, pdf, sq, bbt, logger)

        def usecase = Mock(LeVADocumentUseCase) {
            getMetaClass() >> {
                return usecaseObj.getMetaClass()
            }

            getSupportedDocuments() >> {
                return usecaseObj.getSupportedDocuments()
            }
        }

        def scheduler = Spy(new LeVADocumentScheduler(project, steps, util, usecase, logger))

        // Test Parameters
        def pTypes = [
            DocumentType.IVR as String,
            DocumentType.TIR as String
        ]

        def result = []
        def expected = []

        when:
        for (DocumentType documentType : pTypes) {
            result.add(scheduler.isDocumentApplicableForEnvironment(documentType.name(), "P"))
            expected.add(true)
        }

        then:
        _ * scheduler.isDocumentApplicableForEnvironment(_ as String, "P")
        expected == result
    }

    def "run with a failure stops the pipeline"() {
        given:
        def project = PROJECT_GAMP_1
        project.buildParams.targetEnvironment = "dev"
        project.buildParams.targetEnvironmentToken = project.buildParams.targetEnvironment[0].toUpperCase()

        def steps = Spy(util.PipelineSteps)
        def docGen = Mock(DocGenService)
        def jenkins = Mock(JenkinsService)
        def jiraUseCase = Mock(JiraUseCase)
        def junit = Mock(JUnitTestReportsUseCase)
        def levaFiles = Mock(LeVADocumentChaptersFileService)
        def nexus = Mock(NexusService)
        def os = Mock(OpenShiftService)
        def pdf = Mock(PDFUtil)
        def sq = Mock(SonarQubeUseCase)
        def git = Mock(GitService)
        def logger = Mock(Logger)
        def bbt = Mock(BitbucketTraceabilityUseCase)


        def utilObj = new MROPipelineUtil(project, steps, git, logger)
        def util = Mock(MROPipelineUtil) {
            executeBlockAndFailBuild(_) >> { block ->
                utilObj.executeBlockAndFailBuild(block)
            }
        }

        def usecaseObj = new LeVADocumentUseCase(project, steps, util, docGen, jenkins, jiraUseCase, junit, levaFiles, nexus, os, pdf, sq, bbt, logger)

        def usecase = Mock(LeVADocumentUseCase) {
            getMetaClass() >> {
                return usecaseObj.getMetaClass()
            }

            getSupportedDocuments() >> {
                return usecaseObj.getSupportedDocuments()
            }

            invokeMethod(_, _) >> { method, args ->
                if (method.startsWith("create")) {
                    throw new IllegalStateException("some error")
                }
            }
        }

        def scheduler = Spy(new LeVADocumentScheduler(project, steps, util, usecase, logger))

        // Test Parameters
        def data = [ testReportFiles: null, testResults: null ]

        when:
        scheduler.run(MROPipelineUtil.PipelinePhases.INIT, PipelinePhaseLifecycleStage.PRE_END)

        then:
        def e = thrown(IllegalStateException)
        e.message == "Error: Creating document of type 'CSD' for project '${project.key}' in phase '${MROPipelineUtil.PipelinePhases.INIT}' and stage '${PipelinePhaseLifecycleStage.PRE_END}' has failed: some error."
    }

    def "not run for not included repo"() {
        given:
            //def steps = Stub(util.PipelineSteps)
            def steps = Stub(IPipelineSteps)
            def util = Stub(MROPipelineUtil)
            def usecase = Stub(LeVADocumentUseCase)
            def logger = Stub(Logger)
            def bbt = Stub(BitbucketTraceabilityUseCase)
            PROJECT_GAMP_5.repositories[1].include = false
            def scheduler = new LeVADocumentScheduler(PROJECT_GAMP_5, steps, util, usecase, logger)

        when:
            def result = scheduler.isDocumentApplicable(DocumentType.TIR as String,
                MROPipelineUtil.PipelinePhases.DEPLOY,
                PipelinePhaseLifecycleStage.POST_EXECUTE_REPO,
                PROJECT_GAMP_5.repositories[1])
        then:
            !result
    }

    def "run for included repo"() {
        given:
        //def steps = Stub(util.PipelineSteps)
        def steps = Stub(IPipelineSteps)
        def util = Stub(MROPipelineUtil)
        def usecase = Stub(LeVADocumentUseCase)
        def logger = Stub(Logger)
        def bbt = Stub(BitbucketTraceabilityUseCase)

        def scheduler = new LeVADocumentScheduler(PROJECT_GAMP_5, steps, util, usecase, logger)

        when:
            def result = scheduler.isDocumentApplicable(DocumentType.TIR as String,
                MROPipelineUtil.PipelinePhases.DEPLOY,
                PipelinePhaseLifecycleStage.POST_EXECUTE_REPO,
                PROJECT_GAMP_5.repositories[1])
        then:
            result
    }
}
