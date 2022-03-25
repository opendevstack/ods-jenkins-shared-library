package org.ods.orchestration.usecase

import com.cloudbees.groovy.cps.NonCPS
import org.apache.commons.lang3.StringUtils
import org.ods.orchestration.mapper.LeVADocumentParamsMapper
import org.ods.orchestration.service.DocGenService
import org.ods.orchestration.util.DocumentHistoryEntry
import org.ods.orchestration.util.MROPipelineUtil
import org.ods.orchestration.util.Project
import org.ods.services.JenkinsService
import org.ods.services.NexusService
import org.ods.util.ILogger

import java.nio.file.Path

import static groovy.json.JsonOutput.prettyPrint
import static groovy.json.JsonOutput.toJson

@SuppressWarnings(['SpaceAroundMapEntryColon', 'PropertyName', 'ParameterCount', 'UnusedMethodParameter'])
class LeVADocumentUseCase {

    public static final String OVERALL = "OVERALL"

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

    private static final String BUILD_FOLDER = 'build'
    private static final String JENKINS_LOG_TXT_FILE_NAME = 'jenkins-job-log.txt'
    private static final String JENKINS_LOG_ZIP_FILE_NAME = 'jenkins-job-log.zip'

    public static final String WORK_IN_PROGRESS_WATERMARK = 'Work in Progress'

    private final Project project
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
        MROPipelineUtil util = project.jiraUseCase?.util

        if (util == null) {
            String warnMsg = "JiraUseCase does not have util (MROPipelineUtil) "
            logger.warn(warnMsg)
            throw new RuntimeException(warnMsg)
        }

        if (StringUtils.isEmpty(project.data.jenkinLog)) {
            Path jenkinsLogFilePath = jenkins.storeCurrentBuildLogInFile(BUILD_FOLDER, JENKINS_LOG_TXT_FILE_NAME)
            logger.info("Stored jenkins log file in file ${jenkinsLogFilePath.toString()}")
            Path jenkinsLogZipped = util.createZipArtifact(JENKINS_LOG_ZIP_FILE_NAME, [ jenkinsLogFilePath ] as Path[])
            logger.info("Stored zipped jenkins log file in file ${jenkinsLogZipped.toString()}")

            // project.steps.archiveArtifacts(workspacePath)
            project.data.jenkinLog = nexus.uploadJenkinsJobLog(
                project.getJiraProjectKey(),
                project.steps.env.BUILD_NUMBER,
                jenkinsLogZipped)
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

    private void createDoc(DocumentType documentType, Map params) {
        // WARNING: env -> getEnv -> CPS method
        String buildNumber = project.steps.env.BUILD_NUMBER as String
        createDocForBuild(documentType, buildNumber, params)
    }

    @NonCPS
    private void createDocForBuild(DocumentType documentType, String buildNumber, Map params) {
        if (documentType.name().startsWith(OVERALL)){
            docGen.createDocumentOverall(
                project.getJiraProjectKey(),
                buildNumber,
                documentType.name(),
                params)
        } else {
            List<DocumentHistoryEntry> docHistoryList =  docGen.createDocument(
                project.getJiraProjectKey(),
                buildNumber,
                documentType.name(),
                params)
            if (docHistoryList.size()>1){
                project.setHistoryForDocument(docHistoryList, documentType.name())
            }
        }

    }

}
