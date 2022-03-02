package org.ods.orchestration.usecase

import com.cloudbees.groovy.cps.NonCPS
import org.ods.orchestration.mapper.ComponentDataLeVADocumentParamsMapper
import org.ods.orchestration.mapper.DefaultLeVADocumentParamsMapper
import org.ods.orchestration.mapper.TestDataLeVADocumentParamsMapper
import org.ods.orchestration.service.DocGenService
import org.ods.orchestration.service.LeVADocumentChaptersFileService
import org.ods.orchestration.util.DocumentHistoryEntry
import org.ods.orchestration.util.MROPipelineUtil
import org.ods.orchestration.util.PDFUtil
import org.ods.orchestration.util.Project
import org.ods.orchestration.util.WeakPair
import org.ods.services.JenkinsService
import org.ods.services.NexusService
import org.ods.services.OpenShiftService
import org.ods.util.ILogger
import org.ods.util.IPipelineSteps

import static groovy.json.JsonOutput.prettyPrint
import static groovy.json.JsonOutput.toJson

@SuppressWarnings(['SpaceAroundMapEntryColon', 'PropertyName', 'ParameterCount'])
class LeVADocumentUseCase {

    private final Project project
    private final IPipelineSteps steps
    private final MROPipelineUtil util
    private final DocGenService docGen
    private final JenkinsService jenkins
    private final NexusService nexus
    private final PDFUtil pdf

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

    public static final String DEVELOPER_PREVIEW_WATERMARK = 'Developer Preview'
    public static final String WORK_IN_PROGRESS_WATERMARK = 'Work in Progress'
    public static final String WORK_IN_PROGRESS_DOCUMENT_MESSAGE = 'Attention: this document is work in progress!'

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
    }

    String createCSD(Map repo = null, Map data = null) {
        DocumentType documentType = DocumentType.CSD
        List<DocumentHistoryEntry> docHistoryList = createDocWithDefaultParams(documentType)
        this.project.setHistoryForDocument(docHistoryList, documentType.name())
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
        return createDocWithTestDataParams(DocumentType.TCR, data)
    }

    String createDTR(Map repo, Map data) {
        return createDocWithComponentDataParams(DocumentType.DTR, repo, data)
    }

    String createOverallDTR(Map repo = null, Map data = null) {
        createDocWithDefaultParams(DocumentType.OVERALL_DTR)
    }

    String createCFTR(Map repo = null, Map data) {
        return createDocWithTestDataParams(DocumentType.CFTR, data)
    }

    String createIVR(Map repo = null, Map data) {
        return createDocWithTestDataParams(DocumentType.IVR, data)
    }

    String createTIR(Map repo, Map data) {
        return createDocWithComponentDataParams(DocumentType.TIR, repo, data)
    }

    String createOverallTIR(Map repo = null, Map data = null) {
        uploadJenkinsJobLog()
        return createDocWithDefaultParams(DocumentType.OVERALL_TIR)
    }

    private uploadJenkinsJobLog() {
        String fileName = "jenkins-job-log"
        String projectId = project.getJiraProjectKey().toLowerCase()
        String buildNumber = project.steps.env.BUILD_NUMBER

        InputStream logInputStream = this.jenkins.getCurrentBuildLogInputStream()
        WeakPair<String, InputStream> file = new WeakPair<String, InputStream>(fileName + ".txt", logInputStream)
        WeakPair<String, InputStream> [] files = [ file ]
        byte[] zipArtifact = util.createZipArtifact(fileName + ".zip", files, true)

        String nexusRepository = NexusService.DEFAULT_NEXUS_REPOSITORY
        URI report = this.nexus.storeArtifact(
            "${nexusRepository}",
            "${projectId}/${buildNumber}",
            "${fileName}.zip",
            zipArtifact, "application/octet-binary")
        // "text/html"

        logger.info "Report stored in: ${report}"

        return report
    }

    @NonCPS
    List<String> getSupportedDocuments() {
        return DocumentType.values().collect { it as String }
    }

    private List<DocumentHistoryEntry> createDoc(DocumentType documentType, Map params) {
        String projectId = project.getJiraProjectKey()
        String buildNumber = project.steps.env.BUILD_NUMBER
        Map document = docGen.createDocument(projectId, buildNumber, documentType.toString(), params)
        logger.info("create document ${documentType} return:${document.nexusURL}")
        return document.nexusURL
    }

    private List<DocumentHistoryEntry> createDocWithDefaultParams(DocumentType documentType) {
        logger.info("create document ${documentType} start")
        return createDoc(documentType, getDefaultParams())
    }

    private String createDocWithTestDataParams(DocumentType documentType, Map testData) {
        logger.info("create document ${documentType} start, data:${prettyPrint(toJson(testData))}")
        return createDoc(documentType, getTestDataParams(testData))
    }

    private String createDocWithComponentDataParams(DocumentType documentType, Map repo, Map testData) {
        logger.info("create document ${documentType} start")
        logger.info("repo:${prettyPrint(toJson(repo))}), data:${prettyPrint(toJson(testData))}")
        return createDoc(documentType, getComponentDataParams(testData, repo))
    }

    Map getDefaultParams() {
        DefaultLeVADocumentParamsMapper mapper = new DefaultLeVADocumentParamsMapper(this.project, this.steps)
        Map<String, LinkedHashMap<String, Object>> build = mapper.build()
        return build
    }

    Map getTestDataParams(Map testData) {
        return new TestDataLeVADocumentParamsMapper(this.project, this.steps, testData).build()
    }

    Map getComponentDataParams(Map testData, Map repo) {
        return new ComponentDataLeVADocumentParamsMapper(this.project, this.steps, testData, repo).build()
    }
}
