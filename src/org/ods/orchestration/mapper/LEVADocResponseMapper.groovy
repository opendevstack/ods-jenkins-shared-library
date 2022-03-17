package org.ods.orchestration.mapper

import com.cloudbees.groovy.cps.NonCPS
import org.ods.orchestration.util.DocumentHistoryEntry

class LEVADocResponseMapper {

    @NonCPS
    static List<DocumentHistoryEntry> parse(historyListParsed) {
        List<DocumentHistoryEntry> historyList = []
        historyListParsed.each { entry ->
            DocumentHistoryEntry historyEntry = new DocumentHistoryEntry(
                entry as Map,
                entry.entryId as Long,
                entry.projectVersion as String,
                entry.previousProjectVersion as String,
                entry.rational as String)
            historyList.add(historyEntry)
        }
        return historyList
    }

}
