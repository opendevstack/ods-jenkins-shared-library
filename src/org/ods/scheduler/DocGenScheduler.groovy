package org.ods.scheduler

import com.cloudbees.groovy.cps.NonCPS

import org.ods.usecase.DocGenUseCase
import org.ods.util.IPipelineSteps
import org.ods.util.MROPipelineUtil

abstract class DocGenScheduler {

    protected IPipelineSteps steps
    protected MROPipelineUtil util
    protected DocGenUseCase usecase

    DocGenScheduler(IPipelineSteps steps, MROPipelineUtil util, DocGenUseCase usecase) {
        this.steps = steps
        this.util = util
        this.usecase = usecase
    }

    @NonCPS
    protected String getMethodNameForDocumentType(String documentType) {
        def name = documentType.replaceAll("OVERALL_", "Overall")
        return "create${name}"
    }

    @NonCPS
    protected int getMethodParamsSizeForDocumentType(String documentType) {
        def name = this.getMethodNameForDocumentType(documentType)
        def method = this.usecase.getMetaClass().getMethods().find { method ->
            return method.isPublic() && method.getName() == name
        }

        return method ? method.getParameterTypes().size() : 0
    }

    protected abstract boolean isDocumentApplicable(String documentType, String phase, MROPipelineUtil.PipelinePhaseLifecycleStage stage, Map project, Map repo = null)

    void run(String phase, MROPipelineUtil.PipelinePhaseLifecycleStage stage, Map project, Map repo = null, Map data = null) {
        def documents = this.usecase.getSupportedDocuments()
        documents.each { documentType ->
            def args = [project, repo, data]
            def argsDefined = args.findAll()

            def paramsSize = this.getMethodParamsSizeForDocumentType(documentType)
            if (paramsSize == 0 || argsDefined.size() > paramsSize) {
                return
            }

            if (this.isDocumentApplicable(documentType, phase, stage, project, repo)) {
                def message = "Creating document of type '${documentType}' for project '${project.id}'"
                if (repo) message += " and repo '${repo.id}'"
                this.steps.echo(message)

                // Apply args according to the method's parameters length
                this.usecase.invokeMethod(this.getMethodNameForDocumentType(documentType), args[0..(Math.min(args.size(), paramsSize) - 1)] as Object[])
            }
        }
    }
}
