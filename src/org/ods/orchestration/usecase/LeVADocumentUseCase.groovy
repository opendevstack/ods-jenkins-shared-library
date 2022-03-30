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
import org.ods.util.IPipelineSteps

import java.nio.file.Path
import java.nio.file.Paths

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


        boolean isTIR() {
            this == DocumentType.TIR
        }

        boolean isDTR() {
            this == DocumentType.DTR
        }

        boolean isOverallTIR() {
            this == DocumentType.OVERALL_TIR
        }
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
    private final IPipelineSteps steps

    LeVADocumentUseCase(Project project,
                        DocGenService docGen,
                        JenkinsService jenkins,
                        NexusService nexus,
                        LeVADocumentParamsMapper leVADocumentParamsMapper,
                        IPipelineSteps steps,
                        ILogger logger) {
        this.project = project
        this.docGen = docGen
        this.jenkins = jenkins
        this.nexus = nexus
        this.leVADocumentParamsMapper = leVADocumentParamsMapper
        this.steps = steps
        this.logger = logger
    }

    @NonCPS
    List<String> getSupportedDocuments() {
        return DocumentType.values().collect { it as String }
    }

    void createDocument(String docType, Map repo = null, Map data = null) {
        logger.info("create document ${docType} start ")

        DocumentType documentType = getDocumentType(docType)

        if (documentType.isOverallTIR() && StringUtils.isEmpty(project.data.jenkinLog)) {
            project.data.jenkinLog = uploadJenkinsJobLogToNexus()
        }

        Map<String, Map<String, ?>> params = getParams(documentType, repo, data)

        // WARNING: env -> getEnv -> CPS method
        String buildNumber = project.steps.env.BUILD_NUMBER as String

        logger.info("repo:${prettyPrint(toJson(repo))}), data:${prettyPrint(toJson(data))}")
        createDoc(documentType, buildNumber, params)
        logger.info("create document ${docType} end")
    }

    @NonCPS
    private void createDoc(DocumentType documentType, String buildNumber, Map params) {
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

    private Map<String, Map<String, ?>> getParams(DocumentType documentType, Map repo, Map data) {
        if (documentType.isDTR() || documentType.isTIR()) {
            return leVADocumentParamsMapper.build([tests: data, repo: repo])
        }

        return leVADocumentParamsMapper.build()
    }

    private DocumentType getDocumentType(String docType) {
        DocumentType documentType = DocumentType.valueOf(docType)
        if (documentType == null) {
            throw new RuntimeException("Received a docType value not recognized: ${docType}")
        }
        return documentType
    }

    private String uploadJenkinsJobLogToNexus() {
        MROPipelineUtil util = project.jiraUseCase?.util

        if (util == null) {
            String warnMsg = "JiraUseCase does not have util (MROPipelineUtil) "
            logger.warn(warnMsg)
            throw new RuntimeException(warnMsg)
        }

        Path jenkinsLogFilePath = Paths.get("${steps.env.WORKSPACE}", BUILD_FOLDER, JENKINS_LOG_TXT_FILE_NAME)
        jenkins.storeCurrentBuildLogInFile(jenkinsLogFilePath)

        logger.info("Stored jenkins log file in file ${jenkinsLogFilePath.toString()}")
        Path jenkinsLogZipped = util.createZipArtifact(JENKINS_LOG_ZIP_FILE_NAME, [ jenkinsLogFilePath ] as Path[])
        logger.info("Stored zipped jenkins log file in file ${jenkinsLogZipped.toString()}")

        // project.steps.archiveArtifacts(workspacePath)
        return nexus.uploadJenkinsJobLog(project.getJiraProjectKey(), project.steps.env.BUILD_NUMBER, jenkinsLogZipped)
    }
}
