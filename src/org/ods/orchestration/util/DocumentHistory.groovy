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
    protected Boolean allIssuesAreValid = true

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

        this.latestVersionId = (savedVersionId ?:0L) + 1L
        def newDocDocumentHistoryEntry = parseJiraDataToDocumentHistoryEntry(jiraData)
        if (this.allIssuesAreValid) {
            if (savedVersionId) {
                this.data = this.loadSavedDocHistoryData(savedVersionId)
            }
            newDocDocumentHistoryEntry.rational = createRational(newDocDocumentHistoryEntry)
            this.data.add(newDocDocumentHistoryEntry)
            this.data.sort { a, b -> b.getEntryId() <=> a.getEntryId() }
        }
        this
    }

    Long getVersion() {
        this.latestVersionId
    }

    String setDocumentSuffix(String suffix) {
        this.documentSuffix = suffix
    }

    List<DocumentHistoryEntry> loadSavedDocHistoryData(Long versionIdToRetrieve) {
        this.logger.debug('Retrieving saved document history with name'
            + this.getSavedDocumentName(versionIdToRetrieve) )
        //new ProjectDataBitbucketRepository(steps)
        //    .loadFile(this.getSavedDocumentName(versionIdToRetrieve)) as List<DocumentHistoryEntry>
        []
    }

    String saveDocHistoryData(ProjectDataBitbucketRepository repository) {
        repository.save(this.data, this.getSavedDocumentName(this.latestVersionId))
    }

    List<DocumentHistoryEntry> getDocHistoryEntries() {
        this.data
    }

    List<Map> getHistoryForIssueTypes(List<String> issueTypes) {
        def transformEntry =  { DocumentHistoryEntry e ->
            def formatedIssues = issueTypes.collect { type ->
                def issues = e.getOrDefault(type, [])
                def changed = issues.findAll { it.action == "change" }.clone()
                    .collect { [key: it.key, predecessors: it.predecessors.join(", ")] }

                [ type: type,
                  added: issues.findAll { it.action == "add" },
                  changed: changed,
                  deleted: issues.findAll { it.action == "delete" },
                ]
            }

            return [entryId: e.getEntryId(),
                    rational: e.getRational(),
                    issueType: formatedIssues
            ]
        }
        this.data.collect { transformEntry(it) }
    }

    protected String getSavedDocumentName(Long versionId) {
        def suffix = (documentSuffix) ? "-" + documentSuffix : ""
        return "documentHistory-${this.targetEnvironment}-${versionId}${suffix}"
    }

    private void checkIfAllIssuesHaveVersions(Collection<Map> jiraIssues) {
        if (jiraIssues) {
            def issuesWithNoVersion = jiraIssues.findAll { Map i ->
                (i.versions) ? false : true
            }
            if (!issuesWithNoVersion.isEmpty()) {
                //throw new RuntimeException('In order to build a coherent document history we need to have a' +
                //    ' version for all the elements. In this case, the following items have this state: ' +
                //    "'${issuesWithNoVersion*.key.join(', ')}'")
                this.logger.warn('Document history not valid. We don\'t have a version for  the following' +
                    " elements'${issuesWithNoVersion*.key.join(', ')}'. If you are not using versioning " +
                    'and its automated document history you can ignore this warning. Otherwise, make sure ' +
                    'all the issues have a version attached to it.')
                this.allIssuesAreValid = false
            }
        }
    }

    private String createRational(DocumentHistoryEntry currentEntry) {
        def versionText = "Modifications for project version ${currentEntry.getProjectVersion()}."

        return versionText + rationaleIfConcurrentVersionsAreFound(currentEntry)
    }

    /**
     * Adds a rational in case concurrent versions are found. This can only be achieved
     * @param currentEntry current document history entry
     * @return rational message
     */
    private String rationaleIfConcurrentVersionsAreFound(DocumentHistoryEntry currentEntry) {
        def oldVersionsSimplified = (this.data.clone() as List<DocumentHistoryEntry>).collect {
            [id: it.getEntryId(), version: it.getProjectVersion(), previousVersion: it.getPreviousProjectVersion()]
        }.findAll { it.id != currentEntry.getEntryId() }
        def concurrentVersions = oldVersionsSimplified
            .takeWhile { it.version != currentEntry.getPreviousProjectVersion() }*.id

        if (currentEntry.getPreviousProjectVersion() && oldVersionsSimplified.size() == concurrentVersions.size()) {
            throw new RuntimeException('Inconsistent state found when building DocumentHistory. ' +
                "Project has as previous project version ${currentEntry.getPreviousProjectVersion()} " +
                'but no document history containing that ' +
                'version can be found. Please check the file named ' +
                "'${this.getSavedDocumentName(currentEntry.getEntryId() - 1L)}.json'" +
                ' in your release manager repository')
        }

        if (concurrentVersions.isEmpty()) {
            ''
        } else {
            def pluralS = (concurrentVersions.size() == 1) ? '' : 's'
            " This document version invalidates the changes done in document version${pluralS} " +
                "'${concurrentVersions.join(', ')}'."
        }
    }

    private DocumentHistoryEntry parseJiraDataToDocumentHistoryEntry(Map jiraData) {
        def projectVersion = jiraData.version
        def previousProjectVersion = jiraData.previousVersion ?: ''
        this.allIssuesAreValid = true
        def additionsAndUpdates = jiraData.findAll { Project.JiraDataItem.TYPES.contains(it.key) }
            .collectEntries { issueType, Map<String, Map> issues ->
                checkIfAllIssuesHaveVersions(issues.values())
                def issuesOfThisVersion = issues.findAll { it.value.versions?.contains(projectVersion) }
                    .collect { issueKey, issue ->
                        def isAnUpdate = !issue.getOrDefault('predecessors', []).isEmpty()
                        if (isAnUpdate) {
                            [key: issueKey, action: 'change', predecessors: issue.predecessors]
                        } else {
                            [key: issueKey, action: 'add']
                        }
                }
                [(issueType): issuesOfThisVersion]
            }
        def discontinuations = jiraData.getOrDefault("discontinuationsPerType", [:])
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
