package org.ods.orchestration.scheduler

import groovy.util.logging.Slf4j
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

@Slf4j
class DocGenSchedulerSpec extends SpecHelper {

    class DocGenUseCaseImpl extends LeVADocumentUseCase {

        DocGenUseCaseImpl(Project project) {
            super(project, null, null, null, null, log as ILogger)
        }

        List<String> getSupportedDocuments() {
            return ["A", "B", "C"]
        }

        void createDocument(String docType, Map repo = null, Map data = null) {
            if ((docType != "A") &&
                (docType != "B") &&
                (docType != "C")) {
                throw new RuntimeException("Unsupported document: ${docType}")
            }
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

    def "run with repo and data null"() {
        given:
        MROPipelineUtil util = Mock(MROPipelineUtil)
        JiraUseCase jiraUseCase = Mock(JiraUseCase)
        // Spy(new JiraUseCase(null, null, util, null, log))
        def project = createProject(jiraUseCase)

        def steps = Spy(util.PipelineSteps)
        def usecase = Spy(new DocGenUseCaseImpl(project))
        def scheduler = Spy(new DocGenSchedulerImpl(project, steps, util, usecase))

        // Test Parameters
        def phase = "myPhase"

        when:
        scheduler.run(phase, MROPipelineUtil.PipelinePhaseLifecycleStage.PRE_END)

        then:
        1 * usecase.getSupportedDocuments()

        then:
        1 * scheduler.isDocumentApplicable("A", phase, MROPipelineUtil.PipelinePhaseLifecycleStage.PRE_END, null)
        1 * usecase.createDocument("A", null, null)

        then:
        1 * scheduler.isDocumentApplicable("B", phase, MROPipelineUtil.PipelinePhaseLifecycleStage.PRE_END, null)
        1 * usecase.createDocument("B", null, null)

        then:
        1 * scheduler.isDocumentApplicable("C", phase, MROPipelineUtil.PipelinePhaseLifecycleStage.PRE_END, null)
        1 * usecase.createDocument("C", null, null)
    }

    def "run with repo, not data"() {
        given:
        MROPipelineUtil util = Mock(MROPipelineUtil)
        JiraUseCase jiraUseCase = Mock(JiraUseCase)
        // Spy(new JiraUseCase(null, null, util, null, log))
        def project = createProject(jiraUseCase)

        def steps = Spy(util.PipelineSteps)
        def usecase = Spy(new DocGenUseCaseImpl(project))
        def scheduler = Spy(new DocGenSchedulerImpl(project, steps, util, usecase))

        // Test Parameters
        def phase = "myPhase"
        def repo = project.repositories.first()

        when:
        scheduler.run(phase, MROPipelineUtil.PipelinePhaseLifecycleStage.PRE_END, repo)

        then:
        1 * usecase.getSupportedDocuments()

        then:
        0 * scheduler.isDocumentApplicable('A', phase, MROPipelineUtil.PipelinePhaseLifecycleStage.PRE_END, MROPipelineUtil.PipelinePhaseLifecycleStage.PRE_END, repo)
        0 * usecase.createDocument('A', repo, _)

        then:
        1 * scheduler.isDocumentApplicable('B', phase, MROPipelineUtil.PipelinePhaseLifecycleStage.PRE_END, repo)
        1 * usecase.createDocument('B', repo, _)

        then:
        1 * scheduler.isDocumentApplicable("C", phase, MROPipelineUtil.PipelinePhaseLifecycleStage.PRE_END, repo)
        1 * usecase.createDocument('C', repo, _)
    }

    def "run with repo and data"() {
        given:
        MROPipelineUtil util = Mock(MROPipelineUtil)
        JiraUseCase jiraUseCase = Mock(JiraUseCase)
        // Spy(new JiraUseCase(null, null, util, null, log))
        def project = createProject(jiraUseCase)

        def steps = Spy(util.PipelineSteps)
        def usecase = Spy(new DocGenUseCaseImpl(project))
        def scheduler = Spy(new DocGenSchedulerImpl(project, steps, util, usecase))

        // Test Parameters
        def phase = "myPhase"
        def repo = project.repositories.first()
        def data = [a: 1, b: 2, c: 3]

        when:
        scheduler.run(phase, MROPipelineUtil.PipelinePhaseLifecycleStage.PRE_END, repo, data)

        then:
        1 * usecase.getSupportedDocuments()

        then:
        0 * scheduler.isDocumentApplicable("A", phase, MROPipelineUtil.PipelinePhaseLifecycleStage.PRE_END, repo)
        0 * usecase.createDocument("A", repo, data)

        then:
        0 * scheduler.isDocumentApplicable("B", phase, MROPipelineUtil.PipelinePhaseLifecycleStage.PRE_END, repo)
        0 * usecase.createDocument("B", repo, data)

        then:
        1 * scheduler.isDocumentApplicable("C", phase, MROPipelineUtil.PipelinePhaseLifecycleStage.PRE_END, repo)
        1 * usecase.createDocument("C", repo, data)
    }
}
