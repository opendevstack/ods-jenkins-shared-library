package org.ods.orchestration.usecase

import org.ods.util.ILogger

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

    private final ILogger logger

    LeVADocumentUseCase(ILogger logger) {
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
