package org.ods.scheduler

import org.ods.usecase.LeVADocumentUseCase
import org.ods.util.IPipelineSteps
import org.ods.util.MROPipelineUtil
import org.ods.util.Project

class LeVADocumentScheduler extends DocGenScheduler {

    // Document types per GAMP category
    private static Map GAMP_CATEGORIES = [
        "1": [
            LeVADocumentUseCase.DocumentType.DSD as String,
            LeVADocumentUseCase.DocumentType.FS as String,
            LeVADocumentUseCase.DocumentType.FTP as String,
            LeVADocumentUseCase.DocumentType.FTR as String,
            LeVADocumentUseCase.DocumentType.IVP as String,
            LeVADocumentUseCase.DocumentType.IVR as String,
            LeVADocumentUseCase.DocumentType.TIP as String,
            LeVADocumentUseCase.DocumentType.TIR as String,
            LeVADocumentUseCase.DocumentType.OVERALL_TIR as String
        ],
        "3": [
            LeVADocumentUseCase.DocumentType.DSD as String,
            LeVADocumentUseCase.DocumentType.IVP as String,
            LeVADocumentUseCase.DocumentType.IVR as String,
            LeVADocumentUseCase.DocumentType.URS as String,
            LeVADocumentUseCase.DocumentType.TIP as String,
            LeVADocumentUseCase.DocumentType.TIR as String,
            LeVADocumentUseCase.DocumentType.OVERALL_TIR as String
        ],
        "4": [
            LeVADocumentUseCase.DocumentType.CS as String,
            LeVADocumentUseCase.DocumentType.DSD as String,
            LeVADocumentUseCase.DocumentType.FTP as String,
            LeVADocumentUseCase.DocumentType.FTR as String,
            LeVADocumentUseCase.DocumentType.IVP as String,
            LeVADocumentUseCase.DocumentType.IVR as String,
            LeVADocumentUseCase.DocumentType.URS as String,
            LeVADocumentUseCase.DocumentType.TIP as String,
            LeVADocumentUseCase.DocumentType.TIR as String,
            LeVADocumentUseCase.DocumentType.OVERALL_TIR as String
        ],
        "5": [
            LeVADocumentUseCase.DocumentType.CS as String,
            LeVADocumentUseCase.DocumentType.DSD as String,
            LeVADocumentUseCase.DocumentType.DTP as String,
            LeVADocumentUseCase.DocumentType.DTR as String,
            LeVADocumentUseCase.DocumentType.OVERALL_DTR as String,
            LeVADocumentUseCase.DocumentType.FS as String,
            LeVADocumentUseCase.DocumentType.FTP as String,
            LeVADocumentUseCase.DocumentType.FTR as String,
            LeVADocumentUseCase.DocumentType.IVP as String,
            LeVADocumentUseCase.DocumentType.IVR as String,
            LeVADocumentUseCase.DocumentType.SCP as String,
            LeVADocumentUseCase.DocumentType.SCR as String,
            LeVADocumentUseCase.DocumentType.OVERALL_SCR as String,
            LeVADocumentUseCase.DocumentType.SDS as String,
            LeVADocumentUseCase.DocumentType.OVERALL_SDS as String,
            LeVADocumentUseCase.DocumentType.URS as String,
            LeVADocumentUseCase.DocumentType.TIP as String,
            LeVADocumentUseCase.DocumentType.TIR as String,
            LeVADocumentUseCase.DocumentType.OVERALL_TIR as String
        ]
    ]

    // Document types per pipeline phase with an optional lifecycle constraint
    private static Map PIPELINE_PHASES = [
        (MROPipelineUtil.PipelinePhases.INIT): [
            (LeVADocumentUseCase.DocumentType.URS as String): MROPipelineUtil.PipelinePhaseLifecycleStage.PRE_END,
            (LeVADocumentUseCase.DocumentType.FS as String): MROPipelineUtil.PipelinePhaseLifecycleStage.PRE_END,
            (LeVADocumentUseCase.DocumentType.CS as String): MROPipelineUtil.PipelinePhaseLifecycleStage.PRE_END,
            (LeVADocumentUseCase.DocumentType.DSD as String): MROPipelineUtil.PipelinePhaseLifecycleStage.PRE_END
        ],
        (MROPipelineUtil.PipelinePhases.BUILD): [
            (LeVADocumentUseCase.DocumentType.SDS as String): MROPipelineUtil.PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO,
            (LeVADocumentUseCase.DocumentType.OVERALL_SDS as String): MROPipelineUtil.PipelinePhaseLifecycleStage.PRE_END,
            (LeVADocumentUseCase.DocumentType.SCP as String): MROPipelineUtil.PipelinePhaseLifecycleStage.POST_START,
            (LeVADocumentUseCase.DocumentType.DTP as String): MROPipelineUtil.PipelinePhaseLifecycleStage.POST_START,
            (LeVADocumentUseCase.DocumentType.SCR as String): MROPipelineUtil.PipelinePhaseLifecycleStage.POST_EXECUTE_REPO,
            (LeVADocumentUseCase.DocumentType.DTR as String): MROPipelineUtil.PipelinePhaseLifecycleStage.POST_EXECUTE_REPO,
            (LeVADocumentUseCase.DocumentType.OVERALL_DTR as String): MROPipelineUtil.PipelinePhaseLifecycleStage.PRE_END
        ],
        (MROPipelineUtil.PipelinePhases.DEPLOY): [
            (LeVADocumentUseCase.DocumentType.TIP as String): MROPipelineUtil.PipelinePhaseLifecycleStage.POST_START,
            (LeVADocumentUseCase.DocumentType.TIR as String): MROPipelineUtil.PipelinePhaseLifecycleStage.POST_EXECUTE_REPO
        ],
        (MROPipelineUtil.PipelinePhases.TEST): [
            (LeVADocumentUseCase.DocumentType.IVP as String): MROPipelineUtil.PipelinePhaseLifecycleStage.POST_START,
            (LeVADocumentUseCase.DocumentType.IVR as String): MROPipelineUtil.PipelinePhaseLifecycleStage.PRE_END,
            (LeVADocumentUseCase.DocumentType.FTP as String): MROPipelineUtil.PipelinePhaseLifecycleStage.POST_START,
            (LeVADocumentUseCase.DocumentType.FTR as String): MROPipelineUtil.PipelinePhaseLifecycleStage.PRE_END,
            (LeVADocumentUseCase.DocumentType.SCR as String): MROPipelineUtil.PipelinePhaseLifecycleStage.POST_EXECUTE_REPO,
            (LeVADocumentUseCase.DocumentType.OVERALL_SCR as String): MROPipelineUtil.PipelinePhaseLifecycleStage.PRE_END
        ],
        (MROPipelineUtil.PipelinePhases.RELEASE): [
        ],
        (MROPipelineUtil.PipelinePhases.FINALIZE): [
            (LeVADocumentUseCase.DocumentType.OVERALL_TIR as String): MROPipelineUtil.PipelinePhaseLifecycleStage.PRE_END
        ]
    ]

    // Document types per repository type with an optional phase constraint
    private static Map REPSITORY_TYPES = [
        (MROPipelineUtil.PipelineConfig.REPO_TYPE_ODS_CODE): [
            (LeVADocumentUseCase.DocumentType.DTR as String): null,
            (LeVADocumentUseCase.DocumentType.SCR as String): MROPipelineUtil.PipelinePhases.BUILD,
            (LeVADocumentUseCase.DocumentType.SDS as String): null,
            (LeVADocumentUseCase.DocumentType.TIR as String): null
        ],
        (MROPipelineUtil.PipelineConfig.REPO_TYPE_ODS_SERVICE): [
            (LeVADocumentUseCase.DocumentType.TIR as String): null
        ],
        (MROPipelineUtil.PipelineConfig.REPO_TYPE_ODS_TEST): [
            (LeVADocumentUseCase.DocumentType.SCR as String): MROPipelineUtil.PipelinePhases.TEST,
            (LeVADocumentUseCase.DocumentType.SDS as String): null
        ]
    ]

    // Document types at the project level which require repositories
    private static List REQUIRING_REPOSITORIES = [
        LeVADocumentUseCase.DocumentType.OVERALL_DTR as String,
        LeVADocumentUseCase.DocumentType.OVERALL_TIR as String,
        LeVADocumentUseCase.DocumentType.FTR as String,
        LeVADocumentUseCase.DocumentType.IVR as String
    ]

    // Document types per environment token and label to track with Jira
    public static Map ENVIRONMENT_TYPE = [
        "D": [
            (LeVADocumentUseCase.DocumentType.DTP as String): "${LeVADocumentUseCase.DocumentType.DTP}",
            (LeVADocumentUseCase.DocumentType.FTP as String): "${LeVADocumentUseCase.DocumentType.FTP}",
            (LeVADocumentUseCase.DocumentType.IVP as String): "${LeVADocumentUseCase.DocumentType.IVP}_Q",
            (LeVADocumentUseCase.DocumentType.TIP as String): "${LeVADocumentUseCase.DocumentType.TIP}_Q"
        ],
        "Q": [
            (LeVADocumentUseCase.DocumentType.DTR as String): "${LeVADocumentUseCase.DocumentType.DTR}",
            (LeVADocumentUseCase.DocumentType.FTR as String): "${LeVADocumentUseCase.DocumentType.FTR}",
            (LeVADocumentUseCase.DocumentType.SCR as String): "${LeVADocumentUseCase.DocumentType.SCR}",
            (LeVADocumentUseCase.DocumentType.IVR as String): "${LeVADocumentUseCase.DocumentType.IVR}_Q",
            (LeVADocumentUseCase.DocumentType.TIR as String): "${LeVADocumentUseCase.DocumentType.TIR}_Q",
            (LeVADocumentUseCase.DocumentType.IVP as String): "${LeVADocumentUseCase.DocumentType.IVP}_P",
            (LeVADocumentUseCase.DocumentType.TIP as String): "${LeVADocumentUseCase.DocumentType.IVR}_P"
        ],
        "P": [
            (LeVADocumentUseCase.DocumentType.IVR as String): "${LeVADocumentUseCase.DocumentType.IVR}_P",
            (LeVADocumentUseCase.DocumentType.TIR as String): "${LeVADocumentUseCase.DocumentType.TIR}_P"
        ]
    ]

    LeVADocumentScheduler(Project project, IPipelineSteps steps, MROPipelineUtil util, LeVADocumentUseCase usecase) {
        super(project, steps, util, usecase)
    }

    private boolean isDocumentApplicableForGampCategory(String documentType, String gampCategory) {
        return this.GAMP_CATEGORIES[gampCategory].contains(documentType)
    }

    private boolean isDocumentApplicableForPipelinePhaseAndLifecycleStage(String documentType, String phase, MROPipelineUtil.PipelinePhaseLifecycleStage stage) {
        def documentTypesForPipelinePhase = this.PIPELINE_PHASES[phase]
        if (!documentTypesForPipelinePhase) {
            return false
        }

        def result = documentTypesForPipelinePhase.containsKey(documentType)

        // Check if the document type defines a lifecycle stage constraint
        def lifecycleStageConstraintForDocumentType = documentTypesForPipelinePhase[documentType]
        if (lifecycleStageConstraintForDocumentType != null) {
            result = result && lifecycleStageConstraintForDocumentType == stage
        }

        return result
    }

    private boolean isDocumentApplicableForProject(String documentType, String gampCategory, String phase, MROPipelineUtil.PipelinePhaseLifecycleStage stage) {
        if (!this.GAMP_CATEGORIES.keySet().contains(gampCategory)) {
            throw new IllegalArgumentException("Error: unable to assert applicability of document type '${documentType}' for project '${this.project.key}' in phase '${phase}'. The GAMP category '${gampCategory}' is not supported.")
        }

        def result = isDocumentApplicableForGampCategory(documentType, gampCategory) && isDocumentApplicableForPipelinePhaseAndLifecycleStage(documentType, phase, stage) && isProjectLevelDocument(documentType)
        if (isDocumentRequiringRepositories(documentType)) {
            result = result && !this.project.repositories.isEmpty()
        }

        // Applicable for certain document types only if the Jira service is configured in the release manager configuration
        if ([LeVADocumentUseCase.DocumentType.CS, LeVADocumentUseCase.DocumentType.DSD, LeVADocumentUseCase.DocumentType.FS, LeVADocumentUseCase.DocumentType.URS].contains(documentType as LeVADocumentUseCase.DocumentType)) {
            result = result && this.project.services?.jira != null
        }

        return result
    }

    private boolean isDocumentApplicableForRepo(String documentType, String gampCategory, String phase, MROPipelineUtil.PipelinePhaseLifecycleStage stage, Map repo) {
        if (!this.GAMP_CATEGORIES.keySet().contains(gampCategory)) {
            throw new IllegalArgumentException("Error: unable to assert applicability of document type '${documentType}' for project '${this.project.key}' and repo '${repo.id}' in phase '${phase}'. The GAMP category '${gampCategory}' is not supported.")
        }

        return isDocumentApplicableForGampCategory(documentType, gampCategory) && isDocumentApplicableForPipelinePhaseAndLifecycleStage(documentType, phase, stage) && isDocumentApplicableForRepoTypeAndPhase(documentType, phase, repo)
    }

    private boolean isDocumentApplicableForRepoTypeAndPhase(String documentType, String phase, Map repo) {
        def documentTypesForRepoType = this.REPSITORY_TYPES[(repo.type.toLowerCase())]
        if (!documentTypesForRepoType) {
            return false
        }

        def result = documentTypesForRepoType.containsKey(documentType)

        // Check if the document type defines a phase constraint
        def phaseConstraintForDocumentType = documentTypesForRepoType[documentType]
        if (phaseConstraintForDocumentType != null) {
            result = result && phaseConstraintForDocumentType == phase
        }

        return result
    }

    private boolean isDocumentRequiringRepositories(String documentType) {
        return this.REQUIRING_REPOSITORIES.contains(documentType)
    }

    private boolean isProjectLevelDocument(String documentType) {
        return !this.isRepositoryLevelDocument(documentType)
    }

    private boolean isRepositoryLevelDocument(String documentType) {
        return this.REPSITORY_TYPES.values().collect { it.keySet() }.flatten().contains(documentType)
    }

    protected boolean isDocumentApplicable(String documentType, String phase, MROPipelineUtil.PipelinePhaseLifecycleStage stage, Map repo = null) {
        def levaDocsCapability = this.project.capabilities.find { it instanceof Map && it.containsKey("LeVADocs") }?.LeVADocs
        if (!levaDocsCapability) {
            return false
        }

        def gampCategory = levaDocsCapability.GAMPCategory.toString()
        if (!gampCategory) {
            return false
        }

        return !repo
          ? isDocumentApplicableForProject(documentType, gampCategory, phase, stage)
          : isDocumentApplicableForRepo(documentType, gampCategory, phase, stage, repo)
    }

    protected boolean isDocumentApplicableForEnvironment(String documentType, String environment) {
        // In D always created
        if ("D".equalsIgnoreCase(environment)) {
            return true
        }
        
        return this.ENVIRONMENT_TYPE[environment].containsKey(documentType)
    }

    void run(String phase, MROPipelineUtil.PipelinePhaseLifecycleStage stage,Map repo = null, Map data = null) {
        def documents = this.usecase.getSupportedDocuments()
        def environment = this.project.buildParams.targetEnvironmentToken

        documents.each { documentType ->
            if (this.isDocumentApplicableForEnvironment(documentType, environment)) {
                def args = [repo, data]

                if (this.isDocumentApplicable(documentType, phase, stage, repo)) {
                    def message = "Creating document of type '${documentType}' for project '${this.project.key}'"
                    if (repo) message += " and repo '${repo.id}'"
                    message += " in phase '${phase}' and stage '${stage}'"
                    this.steps.echo(message)

                    this.util.executeBlockAndFailBuild {
                        try {
                            // Apply args according to the method's parameters length
                            def method = this.getMethodNameForDocumentType(documentType)
                            this.usecase.invokeMethod(method, args as Object[])
                        } catch (e) {
                            throw new IllegalStateException("Error: ${message} has failed: ${e.message}.")
                        }
                    }
                }
            }
        }
    }
}
