package org.ods.orchestration.scheduler

import org.ods.services.JenkinsService
import org.ods.services.NexusService
import org.ods.orchestration.service.*
import org.ods.orchestration.usecase.*
import org.ods.orchestration.util.*
import org.ods.services.OpenShiftService
import org.ods.util.ILogger
import org.ods.util.IPipelineSteps

import spock.lang.*

import static util.FixtureHelper.*

import util.*

class DocGenSchedulerSpec extends SpecHelper {

    class DocGenUseCaseImpl extends LeVADocumentUseCase {
        DocGenUseCaseImpl() {
            super(null, null, null, null,
                null, null, null,
                null, null, null,
                null, null, null, null)
        }

        void createA() {}
        void createB(Map repo) {}
        void createC(Map repo, Map data) {}

        List<String> getSupportedDocuments() {
            return ["A", "B", "C"]
        }

        String getDocumentTemplatesVersion() {
            return "0.1"
        }

        Map getFiletypeForDocumentType (String documentType) {
            return [storage: 'zip', content: 'pdf']
        }

        boolean shouldCreateArtifact (String documentType, Map repo) {
            return true
        }
    }

    class DocGenSchedulerImpl extends DocGenScheduler {
        DocGenSchedulerImpl(Project project, IPipelineSteps steps, MROPipelineUtil util, LeVADocumentUseCase docGen) {
            super(project, steps, util, docGen)
        }

        protected boolean isDocumentApplicable(String documentType, String phase, MROPipelineUtil.PipelinePhaseLifecycleStage stage, Map repo = null) {
            return true
        }
    }

    def "run"() {
        given:
        def project = createProject()

        def steps = Spy(util.PipelineSteps)
        def util = Mock(MROPipelineUtil)
        def usecase = Spy(new DocGenUseCaseImpl())
        def scheduler = Spy(new DocGenSchedulerImpl(project, steps, util, usecase))

        // Test Parameters
        def phase = "myPhase"
        def repo = project.repositories.first()
        def data = [ a: 1, b: 2, c: 3 ]

        when:
        scheduler.run(phase, MROPipelineUtil.PipelinePhaseLifecycleStage.PRE_END)

        then:
        1 * usecase.getSupportedDocuments()

        then:
        1 * scheduler.isDocumentApplicable("A", phase, MROPipelineUtil.PipelinePhaseLifecycleStage.PRE_END, null)
        1 * usecase.createA()

        then:
        1 * scheduler.isDocumentApplicable("B", phase, MROPipelineUtil.PipelinePhaseLifecycleStage.PRE_END, null)
        1 * usecase.createB(null)

        then:
        1 * scheduler.isDocumentApplicable("C", phase, MROPipelineUtil.PipelinePhaseLifecycleStage.PRE_END, null)
        1 * usecase.createC(null,  null)

        when:
        scheduler.run(phase, MROPipelineUtil.PipelinePhaseLifecycleStage.PRE_END, repo)

        then:
        1 * usecase.getSupportedDocuments()

        then:
        0 * scheduler.isDocumentApplicable("A", phase, MROPipelineUtil.PipelinePhaseLifecycleStage.PRE_END, MROPipelineUtil.PipelinePhaseLifecycleStage.PRE_END, repo)
        0 * usecase.createA()

        then:
        1 * scheduler.isDocumentApplicable("B", phase, MROPipelineUtil.PipelinePhaseLifecycleStage.PRE_END, repo)
        1 * usecase.createB(repo)

        then:
        1 * scheduler.isDocumentApplicable("C", phase, MROPipelineUtil.PipelinePhaseLifecycleStage.PRE_END, repo)
        1 * usecase.createC(repo,  null)

        when:
        scheduler.run(phase, MROPipelineUtil.PipelinePhaseLifecycleStage.PRE_END, repo, data)

        then:
        1 * usecase.getSupportedDocuments()

        then:
        0 * scheduler.isDocumentApplicable("A", phase, MROPipelineUtil.PipelinePhaseLifecycleStage.PRE_END, repo)
        0 * usecase.createA()

        then:
        0 * scheduler.isDocumentApplicable("B", phase, MROPipelineUtil.PipelinePhaseLifecycleStage.PRE_END, repo)
        0 * usecase.createB(repo)

        then:
        1 * scheduler.isDocumentApplicable("C", phase, MROPipelineUtil.PipelinePhaseLifecycleStage.PRE_END, repo)
        1 * usecase.createC(repo,  data)
    }
}
