package org.ods.orchestration.usecase

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
        this.jiraUseCase = jiraUseCase
        this.junit = junit
        this.levaFiles = levaFiles
        this.os = os
        this.sq = sq
        this.bbt = bbt
        this.logger = logger
    }

    String createCSD(Map data) {

        return uri
    }

    String createDTP(Map data) {

        return uri
    }

    String createDTR(Map data) {
        logger.debug("createDTR - data:${data}")


    }

    String createOverallDTR(Map data) {

        return uri
    }

    String createDIL(Map data) {

        return uri
    }

    String createCFTP(Map data) {

        return uri
    }

    String createCFTR(Map data) {
        logger.debug("createCFTR - data:${data}")

        return uri
    }

    String createRA(Map data) {

        return uri
    }

    String createIVP(Map data) {
        return uri
    }

    String createIVR(Map data) {
        logger.debug("createIVR - data:${data}")


        return uri
    }

    String createTCR(Map data) {
        logger.debug("createTCR - data:${data}")

        return uri
    }

    String createTCP(Map data) {
        String documentType = DocumentType.TCP as String

        return uri
    }

    String createSSDS(Map data) {
        def documentType = DocumentType.SSDS as String

        return uri
    }

    String createTIP(Map data) {
        def documentType = DocumentType.TIP as String


        return uri
    }

    String createTIR(Map data) {
        logger.debug("createTIR - data:${prettyPrint(toJson(data))}")

        def documentType = DocumentType.TIR as String


    }

    String createOverallTIR(Map data) {
        def documentTypeName = DOCUMENT_TYPE_NAMES[DocumentType.OVERALL_TIR as String]

        return uri
    }

    String createTRC(Map data) {
        logger.debug("createTRC - data:${data}")

        return uri
    }
}
