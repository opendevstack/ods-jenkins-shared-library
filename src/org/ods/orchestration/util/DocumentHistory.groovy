package org.ods.orchestration.util

import com.cloudbees.groovy.cps.NonCPS
import org.ods.orchestration.service.leva.ProjectDataBitbucketRepository
import org.ods.orchestration.util.Project.JiraDataItem
import org.ods.util.ILogger
import org.ods.util.IPipelineSteps

/**
 * This class contains the logic for keeping a consistent document version.
 */
class DocumentHistory {

    static final String ADD = 'add'
    static final String ADDED = ADD + 'ed'
    static final String DELETE = 'discontinue'
    static final String DELETED = DELETE + 'd'
    static final String CHANGE = 'change'
    static final String CHANGED = CHANGE + 'd'

    protected IPipelineSteps steps
    protected ILogger logger
    protected List<DocumentHistoryEntry> data = []
    protected String targetEnvironment
    /**
     * Autoincremental number containing the internal version Id of the document
     */
    protected Long latestVersionId
    protected final String documentType
    protected Boolean allIssuesAreValid = true

    DocumentHistory(IPipelineSteps steps, ILogger logger, String targetEnvironment, String documentType) {
        this.steps = steps
        this.logger = logger
        this.documentType = documentType
        if (!targetEnvironment) {
            throw new RuntimeException('Variable \'targetEnvironment\' cannot be empty for computing Document History')
        }
        this.latestVersionId = 1L
        this.targetEnvironment = targetEnvironment
    }

    DocumentHistory load(Map jiraData, Long savedVersionId = null, List<String> filterKeys) {
        this.latestVersionId = (savedVersionId ?: 0L) + 1L
        if (savedVersionId && savedVersionId != 0L) {
            this.data = sortDocHistoriesReversed(this.loadSavedDocHistoryData(savedVersionId))
        }
        def newDocDocumentHistoryEntry = parseJiraDataToDocumentHistoryEntry(jiraData, filterKeys)
        if (this.allIssuesAreValid) {
            newDocDocumentHistoryEntry.rational = createRational(newDocDocumentHistoryEntry)
            this.data.add(0, newDocDocumentHistoryEntry)
            this.data = sortDocHistoriesReversed(this.data)
        }
        this
    }

    Long getVersion() {
        this.latestVersionId
    }

    List<DocumentHistoryEntry> loadSavedDocHistoryData(Long versionIdToRetrieve,
                                                       ProjectDataBitbucketRepository repo = null) {
        def fileName = this.getSavedDocumentName()
        this.logger.debug("Retrieving saved document history with name '${fileName}' " +
            "with version '${versionIdToRetrieve}' in workspace '${this.steps.env.WORKSPACE}'.")
        if (!repo) {
            repo = new ProjectDataBitbucketRepository(steps)
        }
        def content = repo.loadFile(fileName)
        try {
            content.collect { Map entry ->
                if (! entry.entryId) {
                    throw new IllegalArgumentException('EntryId cannot be empty')
                }
                if (! entry.projectVersion) {
                    throw new IllegalArgumentException('projectVersion cannot be empty')
                }
                new DocumentHistoryEntry(
                    entry,
                    entry.entryId,
                    entry.projectVersion,
                    entry.previousProjectVersion,
                    entry.rational
                )
            }
        } catch (Exception e) {
            throw new IllegalArgumentException('Unable to load saved document history for file ' +
                "'${fileName}': ${e.message}", e)
        }
    }

    String saveDocHistoryData(ProjectDataBitbucketRepository repository) {
        repository.save(this.data, this.getSavedDocumentName())
    }

    List<DocumentHistoryEntry> getDocHistoryEntries() {
        this.data
    }

    List<Map> getDocGenFormat() {
        def issueTypes = JiraDataItem.TYPES - JiraDataItem.TYPE_DOCS
        def transformEntry =  { DocumentHistoryEntry e ->
            if (e.getEntryId() == 1L) {
                return [
                    entryId: e.getEntryId(),
                    rational: e.getRational(),
                    ]
            }
            def formatedIssues = issueTypes.collect { type ->
                def issues = e.getOrDefault(type, [])
                if (issues.isEmpty()) {
                    return null
                }
                def changed = issues.findAll { it.action == CHANGE }.clone()
                    .collect { [key: it.key, predecessors: it.predecessors.join(", ")] }

                [ type: type,
                  (ADDED): SortUtil.sortIssuesByKey(issues.findAll { it.action == ADD }),
                  (CHANGED): SortUtil.sortIssuesByKey(changed),
                  (DELETED): SortUtil.sortIssuesByKey(issues.findAll { it.action == DELETE }),
                ]
            }.findAll { it }

            return [entryId: e.getEntryId(),
                    rational: e.getRational(),
                    issueType: formatedIssues + computeDocChaptersOfDocument(e)
            ]
        }
        sortDocHistories(this.data).collect { transformEntry(it) }
    }

    @SuppressWarnings(['UseCollectMany'])
    protected List<String> getDocumentKeys() {
        def result = this.data
            .collect { e ->
                e.findAll { JiraDataItem.TYPES.contains(it.key) }
                    .collect { type, actions -> actions.collect { it.key } }
            }.flatten()
        if (result) {
            return result
        }
        return []
    }

    protected Map computeDocChaptersOfDocument(DocumentHistoryEntry entry) {
        def docIssues = SortUtil.sortHeadingNumbers(entry.getOrDefault(JiraDataItem.TYPE_DOCS, []), 'number')
            .collect { [action: it.action, key: "${it.number} ${it.name}"] }
        return [ type: 'document sections',
                 (ADDED): docIssues.findAll { it.action == ADD },
                 (CHANGED): docIssues.findAll { it.action == CHANGE },
                 (DELETED): docIssues.findAll { it.action == DELETE },
        ]

    }

    protected String getSavedDocumentName() {
        def suffix = (documentType) ? '-' + documentType : ''
        return "documentHistory-${this.targetEnvironment}${suffix}"
    }

    // Do not remove me. Sort is not supported by the Jenkins runtime
    @NonCPS
    protected static List<DocumentHistoryEntry> sortDocHistories(List<DocumentHistoryEntry> dhs) {
        dhs.sort { it.getEntryId() }
    }

    @NonCPS
    protected static List<DocumentHistoryEntry> sortDocHistoriesReversed(List<DocumentHistoryEntry> dhs) {
        sortDocHistories(dhs).reverse()
    }

    private static Map computeDiscontinuations(Map jiraData, List<String> previousDocumentIssues) {
        jiraData.getOrDefault("discontinuationsPerType", [:])
            .collectEntries { String issueType, List<Map> issues ->
                def discont = discontinuedIssuesThatWereInDocument(issueType, previousDocumentIssues, issues)
                [(issueType): discont.collect { computeIssueContent(issueType, DELETE, it) } ]
            }
    }

    private static List<Map> discontinuedIssuesThatWereInDocument(String issuesType, List<String> previousDocIssues,
                                                                  List<Map> discontinued) {
        if (issuesType.equalsIgnoreCase(JiraDataItem.TYPE_DOCS)) {
            discontinued
        } else {
            discontinued.findAll { previousDocIssues.contains(it.key) }
        }
    }

    private static Map computeIssueContent(String issueType, String action, Map issue) {
        def result = [key: issue.key, action: action]
        if (JiraDataItem.TYPE_DOCS.equalsIgnoreCase(issueType)) {
            result << issue.subMap(['documents', 'number', 'name'])
        }
        if (action.equalsIgnoreCase(CHANGE)) {
            result << [predecessors: issue.predecessors]
        }
        return result
    }

    @NonCPS
    private static List<String> getConcurrentVersions(List<Map> versions, String previousProjVersion) {
        // DO NOT remove this method. Takewhile is not supported by Jenkins and must be used in a Non-CPS method
        versions.takeWhile { it.version != previousProjVersion }.collect { it.id }
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
                    " elements'${issuesWithNoVersion.collect { it.key }.join(', ')}'. " +
                    'If you are not using versioning ' +
                    'and its automated document history you can ignore this warning. Otherwise, make sure ' +
                    'all the issues have a version attached to it.')
                this.allIssuesAreValid = false
            }
        }
    }

    private String createRational(DocumentHistoryEntry currentEntry) {
        if (currentEntry.getEntryId() == 1L) {
            return "Initial document version."
        }
        def versionText = "Modifications for project version '${currentEntry.getProjectVersion()}'."
        return versionText + rationaleIfConcurrentVersionsAreFound(currentEntry)
    }

    /**
     * Adds a rational in case concurrent versions are found. This can only be achieved
     * @param currentEntry current document history entry
     * @return rational message
     */
    private String rationaleIfConcurrentVersionsAreFound(DocumentHistoryEntry currentEntry) {
        def oldVersionsSimplified = this.data.collect {
            [id: it.getEntryId(), version: it.getProjectVersion(), previousVersion: it.getPreviousProjectVersion()]
        }.findAll { it.id != currentEntry.getEntryId() }
        def concurrentVersions = getConcurrentVersions(oldVersionsSimplified, currentEntry.getPreviousProjectVersion())

        if (currentEntry.getPreviousProjectVersion() && oldVersionsSimplified.size() == concurrentVersions.size()) {
            throw new RuntimeException('Inconsistent state found when building DocumentHistory. ' +
                "Project has as previous project version '${currentEntry.getPreviousProjectVersion()}' " +
                'but no document history containing that ' +
                'version can be found. Please check the file named ' +
                "'${this.getSavedDocumentName()}.json'" +
                ' in your release manager repository')
        }

        if (concurrentVersions.isEmpty()) {
            return ''
        } else {
            def pluralS = (concurrentVersions.size() == 1) ? '' : 's'
            return " This document version invalidates the changes done in document version${pluralS} " +
                "'${concurrentVersions.join(', ')}'."
        }
    }

    private DocumentHistoryEntry parseJiraDataToDocumentHistoryEntry(Map jiraData, List<String> keysInDocument) {
        logger.debug("Parsing jira data to document history")
        def projectVersion = jiraData.version
        def previousProjectVersion = jiraData.previousVersion ?: ''
        this.allIssuesAreValid = true

        def versionMap = this.computeEntryData(jiraData, projectVersion, keysInDocument)
        return new DocumentHistoryEntry(versionMap, this.latestVersionId, projectVersion, previousProjectVersion, '')
    }

    private Map computeEntryData(Map jiraData, String projectVersion, List<String> keysInDocument) {
        def previousDocumentIssues = this.getDocumentKeys()
        def additionsAndUpdates = this.computeAdditionsAndUpdates(jiraData, projectVersion)
        def discontinuations = computeDiscontinuations(jiraData, previousDocumentIssues)

        def addUpdDisc = JiraDataItem.TYPES.collectEntries { String issueType ->
            [(issueType): additionsAndUpdates.getOrDefault(issueType, [])
                + discontinuations.getOrDefault(issueType, [])]
        } as Map

        return this.computeIssuesThatAreNotInDocumentAnymore(previousDocumentIssues, addUpdDisc, keysInDocument)
    }

    private Map computeIssuesThatAreNotInDocumentAnymore(
        List<String> previousDocIssues, Map versionActions, List<String> issuesInDoc) {
        if (!issuesInDoc) { return versionActions }

        def issuesNotInDocAnymore = previousDocIssues - issuesInDoc
        versionActions.collectEntries { issueType, actions ->
            def typeResult = actions.collect { Map a ->
                if (issuesInDoc.contains(a.key)) {
                    a
                } else if (this.latestVersionId != 1L && issuesNotInDocAnymore?.containsAll(a.predecessors ?: [])) {
                    a.predecessors.collect { computeIssueContent(issueType, DELETE, [key: it]) }
                } else if (a.type == DELETE) {
                    a
                } else {
                    null
                }
            }
            [(issueType): typeResult.flatten() - [null]]
        }
    }

    private Map computeAdditionsAndUpdates(Map jiraData, String projectVersion) {
        jiraData.findAll { JiraDataItem.TYPES.contains(it.key) }
            .collectEntries { String issueType, Map<String, Map> issues ->
                checkIfAllIssuesHaveVersions(issues.values())
                def issuesOfThisVersion = this.getIssueChangesForVersion(projectVersion, issueType, issues)
                [(issueType): issuesOfThisVersion]
            }
    }

    private List<Map> getIssueChangesForVersion(String version, String issueType, Map issues) {
        // Filter chapter issues for this document only
        if (issueType == JiraDataItem.TYPE_DOCS) {
            issues = issues.findAll { it.value.documents.contains(this.documentType) }
        }

        issues.findAll { it.value.versions?.contains(version) }
            .collect { issueKey, issue ->
                def isAnUpdate = !issue.getOrDefault('predecessors', []).isEmpty()
                if (isAnUpdate) {
                    computeIssueContent(issueType, CHANGE, issue)
                } else {
                    computeIssueContent(issueType, ADD, issue)
                }
            }
    }

}
