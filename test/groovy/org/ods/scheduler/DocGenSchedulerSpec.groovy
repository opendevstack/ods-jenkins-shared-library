package org.ods.scheduler

import org.ods.service.DocGenService
import org.ods.service.NexusService
import org.ods.usecase.DocGenUseCase
import org.ods.util.MROPipelineUtil
import org.ods.util.PDFUtil

import spock.lang.*

import static util.FixtureHelper.*

import util.*

class DocGenSchedulerSpec extends SpecHelper {

    class DocGenUseCaseImpl extends DocGenUseCase {
        DocGenUseCaseImpl(PipelineSteps steps, MROPipelineUtil util, DocGenService docGen, NexusService nexus, PDFUtil pdf) {
            super(steps, util, docGen, nexus, pdf)
        }

        void createA(Map project) {}
        void createB(Map project, Map repo) {}
        void createC(Map project, Map repo, Map data) {}

        List<String> getSupportedDocuments() {
            return ["A", "B", "C"]
        }
    }

    class DocGenSchedulerImpl extends DocGenScheduler {
        DocGenSchedulerImpl(PipelineSteps steps, MROPipelineUtil util, DocGenUseCase docGen) {
            super(steps, util, docGen)
        }

        protected boolean isDocumentApplicable(String documentType, String phase, MROPipelineUtil.PipelinePhaseLifecycleStage stage, Map project, Map repo = null) {
            return true
        }
    }

    def "run"() {
        given:
        def steps = Spy(PipelineSteps)
        def util = Mock(MROPipelineUtil)
        def usecase = Spy(new DocGenUseCaseImpl(steps, Mock(MROPipelineUtil), Mock(DocGenService), Mock(NexusService), Mock(PDFUtil)))
        def scheduler = Spy(new DocGenSchedulerImpl(steps, util, usecase))

        // Test Parameters
        def phase = "myPhase"
        def project = createProject()
        def repo = project.repositories.first()
        def data = [ a: 1, b: 2, c: 3 ]

        when:
        scheduler.run(phase, MROPipelineUtil.PipelinePhaseLifecycleStage.PRE_END, project)

        then:
        1 * usecase.getSupportedDocuments()

        then:
        1 * scheduler.isDocumentApplicable("A", phase, MROPipelineUtil.PipelinePhaseLifecycleStage.PRE_END, project, null)
        1 * usecase.createA(project)

        then:
        1 * scheduler.isDocumentApplicable("B", phase, MROPipelineUtil.PipelinePhaseLifecycleStage.PRE_END, project, null)
        1 * usecase.createB(project, null)

        then:
        1 * scheduler.isDocumentApplicable("C", phase, MROPipelineUtil.PipelinePhaseLifecycleStage.PRE_END, project, null)
        1 * usecase.createC(project, null,  null)

        when:
        scheduler.run(phase, MROPipelineUtil.PipelinePhaseLifecycleStage.PRE_END, project, repo)

        then:
        1 * usecase.getSupportedDocuments()

        then:
        0 * scheduler.isDocumentApplicable("A", phase, MROPipelineUtil.PipelinePhaseLifecycleStage.PRE_END, MROPipelineUtil.PipelinePhaseLifecycleStage.PRE_END, project, repo)
        0 * usecase.createA(project)

        then:
        1 * scheduler.isDocumentApplicable("B", phase, MROPipelineUtil.PipelinePhaseLifecycleStage.PRE_END, project, repo)
        1 * usecase.createB(project, repo)

        then:
        1 * scheduler.isDocumentApplicable("C", phase, MROPipelineUtil.PipelinePhaseLifecycleStage.PRE_END, project, repo)
        1 * usecase.createC(project, repo,  null)

        when:
        scheduler.run(phase, MROPipelineUtil.PipelinePhaseLifecycleStage.PRE_END, project, repo, data)

        then:
        1 * usecase.getSupportedDocuments()

        then:
        0 * scheduler.isDocumentApplicable("A", phase, MROPipelineUtil.PipelinePhaseLifecycleStage.PRE_END, project, repo)
        0 * usecase.createA(project)

        then:
        0 * scheduler.isDocumentApplicable("B", phase, MROPipelineUtil.PipelinePhaseLifecycleStage.PRE_END, project, repo)
        0 * usecase.createB(project, repo)

        then:
        1 * scheduler.isDocumentApplicable("C", phase, MROPipelineUtil.PipelinePhaseLifecycleStage.PRE_END, project, repo)
        1 * usecase.createC(project, repo,  data)
    }
}
