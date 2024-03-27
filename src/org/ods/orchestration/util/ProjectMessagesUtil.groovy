package org.ods.orchestration.util

import org.ods.orchestration.usecase.LeVADocumentUseCase

class ProjectMessagesUtil {

    @SuppressWarnings('Instanceof')
    static String generateWIPIssuesMessage(Project project) {
        def message = project.isWorkInProgress ? 'Pipeline-generated documents are watermarked ' +
            "'${LeVADocumentUseCase.WORK_IN_PROGRESS_WATERMARK}' " +
            'since the following issues are work in progress: ' :
            "The pipeline failed since the following issues are work in progress (no documents were generated): "

        project.getWipJiraIssues().each { type, keys ->
            def values = keys instanceof Map ? keys.values().flatten() : keys
            if (!values.isEmpty()) {
                message += '\n\n' + type.capitalize() + ': ' + values.join(', ')
            }
        }
        message += "\n\nPlease note that for a successful Deploy to D, the above-mentioned issues need to be " +
            "in status Done."
        return message
    }

}
