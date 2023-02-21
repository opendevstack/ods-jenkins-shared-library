package org.ods.orchestration.scheduler

import com.cloudbees.groovy.cps.NonCPS
import org.ods.orchestration.usecase.DocumentType
import org.ods.orchestration.usecase.LeVADocumentUseCase
import org.ods.orchestration.util.Environment
import org.ods.orchestration.util.MROPipelineUtil
import org.ods.orchestration.util.PipelinePhaseLifecycleStage
import org.ods.orchestration.util.Project
import org.ods.util.ILogger
import org.ods.util.IPipelineSteps

@SuppressWarnings(['LineLength', 'SpaceAroundMapEntryColon'])
class LeVADocumentScheduler extends DocGenScheduler {

    // Document types per GAMP category
    private final static Map GAMP_CATEGORIES = [
        "1": [
            DocumentType.CSD as String,
            DocumentType.RA as String,
            DocumentType.SSDS as String,
            DocumentType.TIP as String,
            DocumentType.TIR as String,
            DocumentType.OVERALL_TIR as String,
            DocumentType.IVP as String,
            DocumentType.IVR as String,
            DocumentType.CFTP as String,
            DocumentType.CFTR as String,
            DocumentType.TCP as String,
            DocumentType.TCR as String,
            DocumentType.DIL as String,
        ],
        "3": [
            DocumentType.CSD as String,
            DocumentType.RA as String,
            DocumentType.SSDS as String,
            DocumentType.TIP as String,
            DocumentType.TIR as String,
            DocumentType.OVERALL_TIR as String,
            DocumentType.IVP as String,
            DocumentType.IVR as String,
            DocumentType.CFTP as String,
            DocumentType.CFTR as String,
            DocumentType.TCP as String,
            DocumentType.TCR as String,
            DocumentType.DIL as String,
            DocumentType.TRC as String,
        ],
        "4": [
            DocumentType.CSD as String,
            DocumentType.RA as String,
            DocumentType.SSDS as String,
            DocumentType.TIP as String,
            DocumentType.TIR as String,
            DocumentType.OVERALL_TIR as String,
            DocumentType.IVP as String,
            DocumentType.IVR as String,
            DocumentType.TCP as String,
            DocumentType.TCR as String,
            DocumentType.CFTP as String,
            DocumentType.CFTR as String,
            DocumentType.DIL as String,
            DocumentType.TRC as String,
        ],
        "5": [
            DocumentType.CSD as String,
            DocumentType.RA as String,
            DocumentType.SSDS as String,
            DocumentType.DTP as String,
            DocumentType.DTR as String,
            DocumentType.OVERALL_DTR as String,
            DocumentType.TIP as String,
            DocumentType.TIR as String,
            DocumentType.OVERALL_TIR as String,
            DocumentType.IVP as String,
            DocumentType.IVR as String,
            DocumentType.CFTP as String,
            DocumentType.CFTR as String,
            DocumentType.TCP as String,
            DocumentType.TCR as String,
            DocumentType.DIL as String,
            DocumentType.TRC as String,
        ]
    ]

    // Document types per GAMP category - for a saas only project
    private static Map GAMP_CATEGORIES_SAAS_ONLY = [
        "3": [
            DocumentType.CSD as String,
            DocumentType.RA as String,
            DocumentType.SSDS as String,
            DocumentType.CFTP as String,
            DocumentType.CFTR as String,
            DocumentType.TCP as String,
            DocumentType.TCR as String,
            DocumentType.DIL as String,
            DocumentType.TRC as String,
        ],
        "4": [
            DocumentType.CSD as String,
            DocumentType.RA as String,
            DocumentType.SSDS as String,
            DocumentType.TCP as String,
            DocumentType.TCR as String,
            DocumentType.CFTP as String,
            DocumentType.CFTR as String,
            DocumentType.DIL as String,
            DocumentType.TRC as String,
        ]
    ]

    // Document types per pipeline phase with an optional lifecycle constraint
    private final static Map PIPELINE_PHASES = [
        (MROPipelineUtil.PipelinePhases.INIT): [
            (DocumentType.CSD as String): PipelinePhaseLifecycleStage.PRE_END
        ],
        (MROPipelineUtil.PipelinePhases.BUILD): [
            (DocumentType.DTP as String): PipelinePhaseLifecycleStage.POST_START,
            (DocumentType.TIP as String): PipelinePhaseLifecycleStage.POST_START,
            (DocumentType.RA as String): PipelinePhaseLifecycleStage.POST_START,
            (DocumentType.IVP as String): PipelinePhaseLifecycleStage.POST_START,
            (DocumentType.CFTP as String): PipelinePhaseLifecycleStage.POST_START,
            (DocumentType.TCP as String): PipelinePhaseLifecycleStage.POST_START,
            (DocumentType.DTR as String): PipelinePhaseLifecycleStage.POST_EXECUTE_REPO,
            (DocumentType.TRC as String): PipelinePhaseLifecycleStage.POST_START,
            (DocumentType.SSDS as String): PipelinePhaseLifecycleStage.POST_START,
            (DocumentType.OVERALL_DTR as String): PipelinePhaseLifecycleStage.PRE_END
        ],
        (MROPipelineUtil.PipelinePhases.DEPLOY): [
            (DocumentType.TIR as String): PipelinePhaseLifecycleStage.POST_EXECUTE_REPO
        ],
        (MROPipelineUtil.PipelinePhases.TEST): [
            (DocumentType.IVR as String): PipelinePhaseLifecycleStage.PRE_END,
            (DocumentType.CFTR as String): PipelinePhaseLifecycleStage.PRE_END,
            (DocumentType.DIL as String): PipelinePhaseLifecycleStage.PRE_END,
            (DocumentType.TCR as String): PipelinePhaseLifecycleStage.PRE_END
        ],
        (MROPipelineUtil.PipelinePhases.RELEASE): [
        ],
        (MROPipelineUtil.PipelinePhases.FINALIZE): [
            (DocumentType.OVERALL_TIR as String): PipelinePhaseLifecycleStage.PRE_END
        ],
    ]

    // Document types per repository type with an optional phase constraint
    private static Map REPSITORY_TYPES = [
        (MROPipelineUtil.PipelineConfig.REPO_TYPE_ODS_CODE): [
            (DocumentType.DTR as String): null,
            (DocumentType.TIR as String): null
        ],
        (MROPipelineUtil.PipelineConfig.REPO_TYPE_ODS_INFRA): [
            (DocumentType.TIR as String): null
        ],
        (MROPipelineUtil.PipelineConfig.REPO_TYPE_ODS_SAAS_SERVICE): [:],
        (MROPipelineUtil.PipelineConfig.REPO_TYPE_ODS_SERVICE): [
            (DocumentType.TIR as String): null
        ],
        (MROPipelineUtil.PipelineConfig.REPO_TYPE_ODS_TEST): [:],
        (MROPipelineUtil.PipelineConfig.REPO_TYPE_ODS_LIB): [:],
    ]

    // Document types at the project level which require repositories
    private static List REQUIRING_REPOSITORIES = [
        DocumentType.OVERALL_DTR as String,
        DocumentType.OVERALL_TIR as String,
        DocumentType.CFTR as String,
        DocumentType.IVR as String,
        DocumentType.TCP as String,
        DocumentType.TCR as String
    ]

    // Document types per environment token and label to track with Jira
    @SuppressWarnings('NonFinalPublicField')
    public static Map ENVIRONMENT_TYPE = [
        "D": [
            (DocumentType.CSD as String)    : ["${DocumentType.CSD}"],
            (DocumentType.SSDS as String)   : ["${DocumentType.SSDS}"],
            (DocumentType.RA as String)     : ["${DocumentType.RA}"],
            (DocumentType.TIP as String)    : ["${DocumentType.TIP}_Q",
                                                                    "${DocumentType.TIP}_P"],
            (DocumentType.TIR as String)    : ["${DocumentType.TIR}"],
            (DocumentType.OVERALL_TIR as String)    : ["${DocumentType.TIR}"],
            (DocumentType.IVP as String)    : ["${DocumentType.IVP}_Q",
                                                                    "${DocumentType.IVP}_P"],
            (DocumentType.CFTP as String)   : ["${DocumentType.CFTP}"],
            (DocumentType.TCP as String)    : ["${DocumentType.TCP}"],
            (DocumentType.DTP as String)    : ["${DocumentType.DTP}"],
            (DocumentType.DTR as String)    : ["${DocumentType.DTR}"],
            (DocumentType.OVERALL_DTR as String)    : ["${DocumentType.DTR}"],
        ],
        "Q": [
            (DocumentType.TIR as String)    : ["${DocumentType.TIR}_Q"],
            (DocumentType.OVERALL_TIR as String)    : ["${DocumentType.TIR}_Q"],
            (DocumentType.IVR as String)    : ["${DocumentType.IVR}_Q"],
            (DocumentType.OVERALL_IVR as String)    : ["${DocumentType.IVR}_Q"],
            (DocumentType.CFTR as String)   : ["${DocumentType.CFTR}"],
            (DocumentType.TCR as String)    : ["${DocumentType.TCR}"],
            (DocumentType.TRC as String)    : ["${DocumentType.TRC}"],
            (DocumentType.DIL as String)    : ["${DocumentType.DIL}_Q"]
        ],
        "P": [
            (DocumentType.TIR as String)    : ["${DocumentType.TIR}_P"],
            (DocumentType.OVERALL_TIR as String)    : ["${DocumentType.TIR}_P"],
            (DocumentType.IVR as String)    : ["${DocumentType.IVR}_P"],
            (DocumentType.OVERALL_IVR as String)    : ["${DocumentType.IVR}_P"]
        ]
    ]

    private final ILogger logger

    LeVADocumentScheduler(Project project, IPipelineSteps steps, MROPipelineUtil util, LeVADocumentUseCase usecase,
        ILogger logger) {
        super(project, steps, util, usecase)
        this.logger = logger
    }

    /**
     * Returns the first environment where a document is generated.
     * This will also be the only one for which the document history is created or updated.
     * Subsequent environments will get copies of the document history for the previous one.
     *
     * @param documentType a document type.
     * @return the first environment for which the given document type is generated.
     */
    @NonCPS
    static String getFirstCreationEnvironment(String documentType) {
        def environment = Environment.values()*.toString().find { env ->
            ENVIRONMENT_TYPE[env].containsKey(documentType)
        }
        return environment
    }

    /**
     * Returns the last environment where a document is generated before the given one.
     * If the document is not generated in any previous environment, the given environment is returned.
     *
     * @param documentType a document type.
     * @param environment the environment for which to find the previous creation environment.
     * @return the last environment where a document is generated before the given one.
     */
    @NonCPS
    static String getPreviousCreationEnvironment(String documentType, String environment) {
        def previousEnvironment = null
        Environment.values()*.toString()
            .takeWhile { it != environment }
            .each { env ->
                if (ENVIRONMENT_TYPE[env].containsKey(documentType)) {
                    previousEnvironment = env
                }
            }
        return previousEnvironment ?: environment
    }

    @NonCPS
    private boolean isProjectOneSAASRepoOnly () {
        if (!(this.project.repositories.findAll{ repo ->
            repo.type == MROPipelineUtil.PipelineConfig.REPO_TYPE_ODS_SAAS_SERVICE}).isEmpty()) {
            return (this.project.repositories.findAll{ repo ->
                (repo.type != MROPipelineUtil.PipelineConfig.REPO_TYPE_ODS_SAAS_SERVICE &&
                repo.type != MROPipelineUtil.PipelineConfig.REPO_TYPE_ODS_TEST)
            }).isEmpty()
        } else {
            return false
        }
    }

    private boolean isDocumentApplicableForGampCategory(String documentType, String gampCategory) {
        return this.GAMP_CATEGORIES[gampCategory].contains(documentType)
    }

    private boolean isDocumentApplicableForSAASOnlyGampCategory(String documentType, String gampCategory) {
        return this.GAMP_CATEGORIES_SAAS_ONLY[gampCategory].contains(documentType)
    }

    private boolean isDocumentApplicableForPipelinePhaseAndLifecycleStage(String documentType, String phase, PipelinePhaseLifecycleStage stage) {
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

    private boolean isDocumentApplicableForProject(String documentType, String gampCategory, String phase, PipelinePhaseLifecycleStage stage) {
        def result
        if (isProjectOneSAASRepoOnly()) {
            if (!this.GAMP_CATEGORIES_SAAS_ONLY.keySet().contains(gampCategory)) {
                throw new IllegalArgumentException("Error: unable to assert applicability of document type '${documentType}' for project '${this.project.key}' in phase '${phase}'. The GAMP category '${gampCategory}' is not supported for SAAS systems.")
            }
            result = isDocumentApplicableForSAASOnlyGampCategory(documentType, gampCategory) && isDocumentApplicableForPipelinePhaseAndLifecycleStage(documentType, phase, stage) && isProjectLevelDocument(documentType)
        } else {
            if (!this.GAMP_CATEGORIES.keySet().contains(gampCategory)) {
                throw new IllegalArgumentException("Error: unable to assert applicability of document type '${documentType}' for project '${this.project.key}' in phase '${phase}'. The GAMP category '${gampCategory}' is not supported for non-SAAS systems.")
            }
            result = isDocumentApplicableForGampCategory(documentType, gampCategory) && isDocumentApplicableForPipelinePhaseAndLifecycleStage(documentType, phase, stage) && isProjectLevelDocument(documentType)
        }
        if (isDocumentRequiringRepositories(documentType)) {
            result = result && !this.project.repositories.isEmpty()
        }

        // Applicable for certain document types only if the Jira service is configured in the release manager configuration
        if ([DocumentType.CSD, DocumentType.SSDS, DocumentType.CFTP, DocumentType.CFTR, DocumentType.IVP, DocumentType.IVR, DocumentType.DIL, DocumentType.TCP, DocumentType.TCR, DocumentType.RA, DocumentType.TRC].contains(documentType as DocumentType)) {
            result = result && this.project.services?.jira != null
        }

        return result
    }

    private boolean isDocumentApplicableForRepo(String documentType, String gampCategory, String phase, PipelinePhaseLifecycleStage stage, Map repo) {
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

    @SuppressWarnings('UseCollectMany')
    private boolean isRepositoryLevelDocument(String documentType) {
        return this.REPSITORY_TYPES.values().collect { it.keySet() }.flatten().contains(documentType)
    }

    protected boolean isDocumentApplicable(String documentType, String phase, PipelinePhaseLifecycleStage stage, Map repo = null) {
        def capability = this.project.getCapability('LeVADocs')
        if (!capability) {
            return false
        }

        def gampCategory = capability.GAMPCategory as String
        return !repo
          ? isDocumentApplicableForProject(documentType, gampCategory, phase, stage)
          : isDocumentApplicableForRepo(documentType, gampCategory, phase, stage, repo)
    }

    protected boolean isDocumentApplicableForEnvironment(String documentType, String environment) {
        // in developer preview mode always create
        if (project.isDeveloperPreviewMode()) {
            return true
        }

        return this.ENVIRONMENT_TYPE[environment].containsKey(documentType)
    }

    void run(String phase, PipelinePhaseLifecycleStage stage, Map repo = null, Map data = null) {
        def documents = this.usecase.getSupportedDocuments()
        def environment = this.project.buildParams.targetEnvironmentToken

        documents.each { documentType ->
            if (this.isDocumentApplicableForEnvironment(documentType, environment)) {
                def args = [repo, data]
                if (this.isDocumentApplicable(documentType, phase, stage, repo)) {
                    def message = "Creating document of type '${documentType}' for project '${this.project.key}'"
                    def debugKey = "docgen-${this.project.key}-${documentType}"
                    if (repo) {
                        message += " and repo '${repo.id}'"
                        debugKey += "-${repo.id}"
                    }
                    message += " in phase '${phase}' and stage '${stage}'"
                    logger.infoClocked("${debugKey}", message)
                    this.util.executeBlockAndFailBuild {
                        try {
                            // Apply args according to the method's parameters length
                            logger.debug("calling this.getMethodNamesForDocumentType(${documentType})")
                            def method = this.getMethodNameForDocumentType(documentType)
                            logger.debug("method: ${method}")
                            this.usecase.invokeMethod(method, args as Object[])
                            logger.debug("this.useCase.invokeMethod done")
                        } catch (e) {
                            logger.debug("Exception occured: ${e.class.name}, ${e.message}")
                            throw new IllegalStateException("Error: ${message} has failed: ${e.message}.", e)
                        }
                    }
                    logger.debugClocked("${debugKey}")
                }
            }
        }
    }
}
