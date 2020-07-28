package org.ods.orchestration.util

import org.ods.orchestration.service.leva.ProjectDataBitbucketRepository
import org.ods.util.ILogger
import org.ods.util.IPipelineSteps

/**
 * This class contains the logic for keeping a consistent document version.
 */
class DocumentHistory {

    protected IPipelineSteps steps
    protected ILogger logger
    protected List<DocumentHistoryEntry> data = []
    protected String targetEnvironment
    /**
     * Autoincremental number containing the internal version Id of the document
     */
    protected Long latestVersionId
    protected String documentSuffix = null

    DocumentHistory(IPipelineSteps steps, ILogger logger, String targetEnvironment) {
        this.steps = steps
        this.logger = logger

        if (!targetEnvironment) {
            throw new RuntimeException('Variable \'targetEnvironment\' cannot be empty for computing Document History')
        }
        this.latestVersionId = 1L
        this.targetEnvironment = targetEnvironment
    }

    DocumentHistory load(Map jiraData, Long savedVersionId = null, String suffix = null) {
        this.documentSuffix = suffix
        if (savedVersionId) {
            this.latestVersionId = savedVersionId + 1L
            this.data = this.loadSavedDocHistoryData(savedVersionId)
        }
        def newDocDocumentHistoryEntry = parseJiraDataToDocumentHistoryEntry(jiraData)
        newDocDocumentHistoryEntry.rational = rationaleIfConcurrentVersionsAreFound(newDocDocumentHistoryEntry)
        this.data.add(newDocDocumentHistoryEntry)
        this.data.sort { a, b -> b.getEntryId() <=> a.getEntryId() }
        this
    }

    Long getVersion() {
        this.latestVersionId
    }

    List<DocumentHistoryEntry> loadSavedDocHistoryData(Long versionIdToRetrieve) {
        this.logger.debug('Retrieving saved document history with name'
            + this.getSavedDocumentName(versionIdToRetrieve) )
        //new ProjectDataBitbucketRepository(steps)
        //    .loadFile(this.getSavedDocumentName(versionIdToRetrieve)) as List<DocumentHistoryEntry>
        []
    }

    void saveDocHistoryData() {
        new ProjectDataBitbucketRepository(steps)
            .save(this.data, this.getSavedDocumentName(this.latestVersionId))
    }

    List<DocumentHistoryEntry> getDocHistoryEntries() {
        this.data
    }

    protected String getSavedDocumentName(Long versionId) {
        def suffix = (documentSuffix) ? "-" + documentSuffix : ""
        return "documentHistory-${this.targetEnvironment}-${versionId}${suffix}"
    }

    private static void failIfThereAreIssuesWithoutVersions(Collection<Map> jiraIssues) {
        if (jiraIssues) {
            def issuesWithNoVersion = jiraIssues.findAll { Map i ->
                (i.version) ? false : true
            }
            if (!issuesWithNoVersion.isEmpty()) {
                throw new RuntimeException('In order to build a coherent document history we need to have a' +
                    ' version for all the elements. In this case, the following items have this state: ' +
                    "'${issuesWithNoVersion.collect { it.key }.join(', ')}'")
            }
        }
    }

    private String rationaleIfConcurrentVersionsAreFound(DocumentHistoryEntry currentEntry) {
        def oldVersionsSimplified = (this.data.clone() as List<DocumentHistoryEntry>).collect {
            [id: it.id, previousProjectVersion: it.previousProjectVersion]
        }.findAll { it.id != currentEntry.id }
        def concurrentVersions = oldVersionsSimplified.reverse()
            .takeWhile { it.projectVersion != currentEntry.previousProjectVersion }*.id

        if (currentEntry.previousProjectVersion && oldVersionsSimplified.size() == concurrentVersions.size()) {
            throw new RuntimeException('Inconsistent state found when building DocumentHistory. ' +
                "Project has as previous project version ${currentEntry.previousProjectVersion} " +
                'but no document history containing that ' +
                'version can be found. Please check the file named ' +
                "'${this.getSavedDocumentName(currentEntry.id - 1L)}.json'" +
                ' in your release manager repository')
        }

        if (concurrentVersions.isEmpty()) {
            return null
        } else {
            def pluralS = (concurrentVersions.size() == 1) ? '' : 's'
            return "This document version invalidates the changes done in version${pluralS} " +
                "'${concurrentVersions.join(', ')}'."
        }
    }

    private DocumentHistoryEntry parseJiraDataToDocumentHistoryEntry(Map jiraData) {
        def projectVersion = jiraData.version
        def previousProjectVersion = jiraData.previousVersion ?: ''
        def additionsAndUpdates = jiraData.findAll { Project.JiraDataItem.TYPES.contains(it.key) }
            .collectEntries { issueType, Map<String, Map> issues ->
                failIfThereAreIssuesWithoutVersions(issues.values())
                def issuesOfThisVersion = issues.findAll { it.value.version == projectVersion }.collect {
                    issueKey, issue ->
                    def isAnUpdate = !issue.getOrDefault('predecessors', []).isEmpty()
                    if (isAnUpdate) {
                        [key: issueKey, action: 'change', predecessors: issue.predecessors]
                    } else {
                        [key: issueKey, action: 'add']
                    }
                }
                [(issueType): issuesOfThisVersion]
            }
        def discontinuations = jiraData.getOrDefault("discontinuationsPerType", [])
            .collectEntries { issueType, List<String> issueKeys ->
                [(issueType): issueKeys.collect { [key: it, action: 'discontinue'] }]
            }
        def versionMap = Project.JiraDataItem.TYPES.collectEntries { String issueType ->
            [(issueType): additionsAndUpdates.getOrDefault(issueType, [])
                + discontinuations.getOrDefault(issueType, [])]
        } as Map
        return new DocumentHistoryEntry(versionMap, this.latestVersionId, projectVersion, previousProjectVersion, '')
    }

}
