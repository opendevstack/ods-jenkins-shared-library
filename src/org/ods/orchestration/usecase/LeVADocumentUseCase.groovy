package org.ods.orchestration.usecase

import com.cloudbees.groovy.cps.NonCPS
import org.ods.orchestration.mapper.DefaultLeVADocumentParamsMapper
import org.ods.orchestration.service.DocGenService
import org.ods.orchestration.service.LeVADocumentChaptersFileService
import org.ods.orchestration.util.MROPipelineUtil
import org.ods.orchestration.util.PDFUtil
import org.ods.orchestration.util.Project
import org.ods.services.JenkinsService
import org.ods.services.NexusService
import org.ods.services.OpenShiftService
import org.ods.util.ILogger
import org.ods.util.IPipelineSteps

import static groovy.json.JsonOutput.prettyPrint
import static groovy.json.JsonOutput.toJson

class LeVADocumentUseCase {

    private final Project project
    private final IPipelineSteps steps
    private final MROPipelineUtil util
    private final DocGenService docGen
    private final JenkinsService jenkins
    private final NexusService nexus
    private final PDFUtil pdf
    private final String projectId
    private final String buildNumber

    enum DocumentType {

        CSD,
        DIL,
        DTP,
        DTR,
        RA,
        CFTP,
        CFTR,
        IVP,
        IVR,
        SSDS,
        TCP,
        TCR,
        TIP,
        TIR,
        TRC,
        OVERALL_DTR,
        OVERALL_IVR,
        OVERALL_TIR

    }

    protected static Map DOCUMENT_TYPE_NAMES = [
        (DocumentType.CSD as String)        : 'Combined Specification Document',
        (DocumentType.DIL as String)        : 'Discrepancy Log',
        (DocumentType.DTP as String)        : 'Software Development Testing Plan',
        (DocumentType.DTR as String)        : 'Software Development Testing Report',
        (DocumentType.CFTP as String)       : 'Combined Functional and Requirements Testing Plan',
        (DocumentType.CFTR as String)       : 'Combined Functional and Requirements Testing Report',
        (DocumentType.IVP as String)        : 'Configuration and Installation Testing Plan',
        (DocumentType.IVR as String)        : 'Configuration and Installation Testing Report',
        (DocumentType.RA as String)         : 'Risk Assessment',
        (DocumentType.TRC as String)        : 'Traceability Matrix',
        (DocumentType.SSDS as String)       : 'System and Software Design Specification',
        (DocumentType.TCP as String)        : 'Test Case Plan',
        (DocumentType.TCR as String)        : 'Test Case Report',
        (DocumentType.TIP as String)        : 'Technical Installation Plan',
        (DocumentType.TIR as String)        : 'Technical Installation Report',
        (DocumentType.OVERALL_DTR as String): 'Overall Software Development Testing Report',
        (DocumentType.OVERALL_IVR as String): 'Overall Configuration and Installation Testing Report',
        (DocumentType.OVERALL_TIR as String): 'Overall Technical Installation Report',
    ]

    static GAMP_CATEGORY_SENSITIVE_DOCS = [
        DocumentType.SSDS as String,
        DocumentType.CSD as String,
        DocumentType.CFTP as String,
        DocumentType.CFTR as String
    ]

    static Map<String, Map> DOCUMENT_TYPE_FILESTORAGE_EXCEPTIONS = [
        'SCRR-MD': [storage: 'pdf', content: 'pdf']
    ]

    static List<String> COMPONENT_TYPE_IS_NOT_INSTALLED = [
        MROPipelineUtil.PipelineConfig.REPO_TYPE_ODS_SAAS_SERVICE as String,
        MROPipelineUtil.PipelineConfig.REPO_TYPE_ODS_TEST as String,
        MROPipelineUtil.PipelineConfig.REPO_TYPE_ODS_LIB as String
    ]

    static Map<String, String> INTERNAL_TO_EXT_COMPONENT_TYPES = [
        (MROPipelineUtil.PipelineConfig.REPO_TYPE_ODS_SAAS_SERVICE as String): 'SAAS Component',
        (MROPipelineUtil.PipelineConfig.REPO_TYPE_ODS_TEST as String)        : 'Automated tests',
        (MROPipelineUtil.PipelineConfig.REPO_TYPE_ODS_SERVICE as String)     : '3rd Party Service Component',
        (MROPipelineUtil.PipelineConfig.REPO_TYPE_ODS_CODE as String)        : 'ODS Software Component',
        (MROPipelineUtil.PipelineConfig.REPO_TYPE_ODS_INFRA as String)       : 'Infrastructure as Code Component',
        (MROPipelineUtil.PipelineConfig.REPO_TYPE_ODS_LIB as String)         : 'ODS library component'
    ]

    public static String DEVELOPER_PREVIEW_WATERMARK = 'Developer Preview'
    public static String WORK_IN_PROGRESS_WATERMARK = 'Work in Progress'
    public static String WORK_IN_PROGRESS_DOCUMENT_MESSAGE = 'Attention: this document is work in progress!'

    private final JiraUseCase jiraUseCase
    private final JUnitTestReportsUseCase junit
    private final LeVADocumentChaptersFileService levaFiles
    private final OpenShiftService os
    private final SonarQubeUseCase sq
    private final BitbucketTraceabilityUseCase bbt
    private final ILogger logger

    LeVADocumentUseCase(Project project, IPipelineSteps steps, MROPipelineUtil util, DocGenService docGen,
                        JenkinsService jenkins, JiraUseCase jiraUseCase, JUnitTestReportsUseCase junit,
                        LeVADocumentChaptersFileService levaFiles, NexusService nexus, OpenShiftService os,
                        PDFUtil pdf, SonarQubeUseCase sq, BitbucketTraceabilityUseCase bbt, ILogger logger) {
        this.pdf = pdf
        this.nexus = nexus
        this.jenkins = jenkins
        this.docGen = docGen
        this.util = util
        this.steps = steps
        this.project = project
        this.jiraUseCase = jiraUseCase
        this.junit = junit
        this.levaFiles = levaFiles
        this.os = os
        this.sq = sq
        this.bbt = bbt
        this.logger = logger
        this.projectId = project.getJiraProjectKey()
        this.buildNumber = project.steps.env.BUILD_NUMBER
    }

    String createCSD(Map repo = null, Map data = null) {
        return createDocWithDefaultParams(DocumentType.CSD)
    }

    String createDIL(Map repo = null, Map data = null) {
        return createDocWithDefaultParams(DocumentType.DIL)
    }

    String createDTP(Map repo = null, Map data = null) {
        return createDocWithDefaultParams(DocumentType.DTP)
    }

    String createRA(Map repo = null, Map data = null) {
        return createDocWithDefaultParams(DocumentType.RA)
    }

    String createCFTP(Map repo = null, Map data = null) {
        return createDocWithDefaultParams(DocumentType.CFTP)
    }

    String createIVP(Map repo = null, Map data = null) {
        return createDocWithDefaultParams(DocumentType.IVP)
    }

    String createSSDS(Map repo = null, Map data = null) {
        return createDocWithDefaultParams(DocumentType.SSDS)
    }

    String createTCP(Map repo = null, Map data = null) {
        return createDocWithDefaultParams(DocumentType.TCP)
    }

    String createTIP(Map repo = null, Map data = null) {
        return createDocWithDefaultParams(DocumentType.TIP)
    }

    String createTRC(Map repo = null, Map data = null) {
        return createDocWithDefaultParams(DocumentType.TRC)
    }

    String createTCR(Map repo = null, Map data) {
        return createDocWithDefaultParams(DocumentType.TRC)
    }

    String createDTR(Map repo, Map data) {
        logger.debug("createDTR - repo:${repo}, data:${data}")
        def uri = ""
        return uri
    }

    String createOverallDTR(Map repo = null, Map data = null) {
        def documentTypeName = DOCUMENT_TYPE_NAMES[DocumentType.OVERALL_DTR as String]
        def uri = ""
        return uri
    }

    String createCFTR(Map repo = null, Map data) {
        logger.debug("createCFTR - data:${data}")

        def uri = ""
        return uri
    }

    String createIVR(Map repo = null, Map data) {
        logger.debug("createIVR - data:${data}")
        def uri = ""
        return uri
    }

    String createTIR(Map repo, Map data) {
        logger.debug("createTIR - repo:${prettyPrint(toJson(repo))}, data:${prettyPrint(toJson(data))}")
        def documentType = DocumentType.TIR as String
        def uri = ""
        return uri
    }

    String createOverallTIR(Map repo = null, Map data = null) {
        def documentTypeName = DOCUMENT_TYPE_NAMES[DocumentType.OVERALL_TIR as String]
        def uri = ""
        return uri
    }

    @NonCPS
    List<String> getSupportedDocuments() {
        return DocumentType.values().collect { it as String }
    }

    private String createDocWithDefaultParams(DocumentType documentType) {
        logger.info("create document ${documentType} start")
        Map data = getDefaultParams()
        Map document = docGen.createDocument(projectId, buildNumber, documentType.toString(), data)
        logger.info("create document ${documentType} return:${document.nexusURL}")
        return document.nexusURL
    }

    Map getDefaultParams() {
        return new DefaultLeVADocumentParamsMapper(this.project, this.steps).build()
    }

}
