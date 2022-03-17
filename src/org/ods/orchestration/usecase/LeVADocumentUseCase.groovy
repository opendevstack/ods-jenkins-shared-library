package org.ods.orchestration.usecase

import com.cloudbees.groovy.cps.NonCPS
import jdk.nashorn.internal.ir.annotations.Ignore
import org.apache.commons.lang3.StringUtils
import org.ods.orchestration.mapper.LeVADocumentParamsMapper
import org.ods.orchestration.service.DocGenService
import org.ods.orchestration.util.DocumentHistoryEntry
import org.ods.orchestration.util.MROPipelineUtil
import org.ods.orchestration.util.Project
import org.ods.orchestration.util.WeakPair
import org.ods.services.JenkinsService
import org.ods.services.NexusService
import org.ods.util.ILogger

import static groovy.json.JsonOutput.prettyPrint
import static groovy.json.JsonOutput.toJson

@SuppressWarnings(['SpaceAroundMapEntryColon', 'PropertyName', 'ParameterCount', 'UnusedMethodParameter'])
class LeVADocumentUseCase {

    public static final String CONTENT_TYPE = "application/octet-binary"
    public static final String OVERALL = "OVERALL"
    public static final String JENKINS_LOG = "jenkins-job-log"

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

    public static final String WORK_IN_PROGRESS_WATERMARK = 'Work in Progress'

    private final Project project
    private final MROPipelineUtil util
    private final DocGenService docGen
    private final JenkinsService jenkins
    private final NexusService nexus
    private final LeVADocumentParamsMapper leVADocumentParamsMapper
    private final ILogger logger

    LeVADocumentUseCase(Project project,
                        DocGenService docGen,
                        JenkinsService jenkins,
                        NexusService nexus,
                        LeVADocumentParamsMapper leVADocumentParamsMapper,
                        ILogger logger) {
        this.project = project
        this.util = project.jiraUseCase.util
        this.docGen = docGen
        this.jenkins = jenkins
        this.nexus = nexus
        this.leVADocumentParamsMapper = leVADocumentParamsMapper
        this.logger = logger
    }

    @NonCPS
    List<String> getSupportedDocuments() {
        return DocumentType.values().collect { it as String }
    }

    void createCSD(Map repo = null, Map data = null) {
        createDocWithDefaultParams(DocumentType.CSD)
    }

    void createDIL(Map repo = null, Map data = null) {
        createDocWithDefaultParams(DocumentType.DIL)
    }

    void createDTP(Map repo = null, Map data = null) {
        createDocWithDefaultParams(DocumentType.DTP)
    }

    void createRA(Map repo = null, Map data = null) {
        createDocWithDefaultParams(DocumentType.RA)
    }

    void createCFTP(Map repo = null, Map data = null) {
        createDocWithDefaultParams(DocumentType.CFTP)
    }

    void createIVP(Map repo = null, Map data = null) {
        createDocWithDefaultParams(DocumentType.IVP)
    }

    void createSSDS(Map repo = null, Map data = null) {
        createDocWithDefaultParams(DocumentType.SSDS)
    }

    void createTCP(Map repo = null, Map data = null) {
        createDocWithDefaultParams(DocumentType.TCP)
    }

    void createTIP(Map repo = null, Map data = null) {
        createDocWithDefaultParams(DocumentType.TIP)
    }

    void createTRC(Map repo = null, Map data = null) {
        createDocWithDefaultParams(DocumentType.TRC)
    }

    void createTCR(Map repo = null, Map data = null) {
        createDocWithDefaultParams(DocumentType.TCR)
    }

    void createDTR(Map repo, Map data) {
        createDocWithComponentDataParams(DocumentType.DTR, repo, data)
    }

    void createOverallDTR(Map repo = null, Map data = null) {
        createDocWithDefaultParams(DocumentType.OVERALL_DTR)
    }

    void createCFTR(Map repo = null, Map data = null) {
        createDocWithDefaultParams(DocumentType.CFTR)
    }

    void createIVR(Map repo = null, Map data = null) {
        createDocWithDefaultParams(DocumentType.IVR)
    }

    void createTIR(Map repo, Map data) {
        createDocWithComponentDataParams(DocumentType.TIR, repo, data)
    }

    void createOverallTIR(Map repo = null, Map data = null) {
        if (StringUtils.isEmpty(project.data.jenkinLog)) {
            project.data.jenkinLog = uploadJenkinsJobLog(
                project.getJiraProjectKey(),
                project.steps.env.BUILD_NUMBER,
                jenkins.getCurrentBuildLogInputStream())
        }
        createDocWithDefaultParams(DocumentType.OVERALL_TIR)
    }

    private void createDocWithDefaultParams(DocumentType documentType) {
        logger.info("create document ${documentType} start ")
        createDoc(documentType, leVADocumentParamsMapper.build())
        logger.info("create document ${documentType} end")
    }

    private void createDocWithComponentDataParams(DocumentType documentType, Map repo, Map testData) {
        logger.info("repo:${prettyPrint(toJson(repo))}), data:${prettyPrint(toJson(testData))}")
        createDoc(documentType, leVADocumentParamsMapper.build([tests: testData, repo: repo]))
        logger.info("create document ${documentType} end")
    }

    @NonCPS
    private void createDoc(DocumentType documentType, Map params) {
        if (documentType.name().startsWith(OVERALL)){
            docGen.createDocumentOverall(
                project.getJiraProjectKey(),
                project.steps.env.BUILD_NUMBER as String,
                documentType.name(),
                params)
        } else {
            List<DocumentHistoryEntry> docHistoryList =  docGen.createDocument(
                project.getJiraProjectKey(),
                project.steps.env.BUILD_NUMBER as String,
                documentType.name(),
                params)
            if (docHistoryList.size()>1){
                this.project.setHistoryForDocument(docHistoryList, documentType.name())
            }
        }

    }

    // This test is only used to upload the parts we need to run docGen tests.
    @Ignore
    private String uploadJenkinsJobLog(String projectKey, String buildNumber, InputStream jenkinsJobLog) {
        String fileName = JENKINS_LOG
        String nexusPath = "${projectKey.toLowerCase()}/${buildNumber}"

        WeakPair<String, InputStream> file = new WeakPair<String, InputStream>(fileName + ".txt", jenkinsJobLog)
        WeakPair<String, InputStream> [] files = [ file ]

        String logFileZipped = "${fileName}.zip"
        byte[] zipArtifact = util.createZipArtifact(logFileZipped, files, true)

        String nexusRepository = NexusService.DEFAULT_NEXUS_REPOSITORY
        URI report = this.nexus.storeArtifact(
            nexusRepository,
            nexusPath,
            logFileZipped,
            zipArtifact,
            CONTENT_TYPE
        )

        logger.info "Report stored in: ${report.toString()}"
        return report.toString()
    }

}
