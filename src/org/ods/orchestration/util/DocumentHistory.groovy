package org.ods.orchestration.util

import com.cloudbees.groovy.cps.NonCPS
import org.ods.orchestration.scheduler.LeVADocumentScheduler
import org.ods.orchestration.service.leva.ProjectDataBitbucketRepository
import org.ods.orchestration.util.Project.JiraDataItem
import org.ods.util.ILogger
import org.ods.util.IPipelineSteps

import java.nio.file.NoSuchFileException

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
    private String sourceEnvironment
    protected String targetEnvironment
    /**
     * Autoincremental number containing the internal version Id of the document
     */
    protected Long latestVersionId
    protected final String documentName
    protected Boolean allIssuesAreValid = true
    protected String allIssuesAreNotValidMessage = ''

    DocumentHistory(IPipelineSteps steps, ILogger logger, String targetEnvironment, String documentName) {
        this.steps = steps
        this.logger = logger
        this.documentName = documentName
        if (!targetEnvironment) {
            throw new RuntimeException('Variable \'targetEnvironment\' cannot be empty for computing Document History')
        }
        this.latestVersionId = 0L
        this.targetEnvironment = targetEnvironment
        // Retrieve the history from the previous environment,
        // unless the target environment is the first one where the document is generated.
        sourceEnvironment = getSourceEnvironment(documentName, targetEnvironment)
    }

    DocumentHistory load(Map jiraData, List<String> filterKeys) {
        this.latestVersionId = 0L
        try {
            def docHistories = sortDocHistoriesReversed(this.loadSavedDocHistoryData())
            if (docHistories) {
                this.latestVersionId = docHistories.first().getEntryId()
                if (logger.getDebugMode()) {
                    logger.debug("Retrieved latest ${documentName} version ${latestVersionId} from saved history")
                }
                this.data = docHistories
            }
        } catch (NoSuchFileException e) {
            if (sourceEnvironment != targetEnvironment) {
                this.logger.warn("No saved history found. Exception message: ${e.message}")
            } else if (logger.getDebugMode()) {
                this.logger.debug("No saved history found. Exception message: ${e.message}")
            }
        }
        // Only update the history if the target environment is the first one where the document is created,
        // or if no saved history was found
        if (sourceEnvironment == targetEnvironment || !latestVersionId) {
            this.latestVersionId += 1L
            def newDocDocumentHistoryEntry = parseJiraDataToDocumentHistoryEntry(jiraData, filterKeys)
            if (this.allIssuesAreValid) {
                if (logger.getDebugMode()) {
                    logger.debug("Creating a new history entry with version ${latestVersionId} for ${documentName}")
                }
                newDocDocumentHistoryEntry.rational = createRational(newDocDocumentHistoryEntry)
                this.data.add(0, newDocDocumentHistoryEntry)
                this.data = sortDocHistoriesReversed(this.data)
            } else {
                // We only want to update the document version when we are actually adding a new entry.
                this.latestVersionId -= 1L
                logger.warn("Not all issues for ${documentName} had version. Not creating a new history entry.")
            }
        }
        return this
    }

    @NonCPS
    Long getVersion() {
        this.latestVersionId
    }

    List<DocumentHistoryEntry> loadSavedDocHistoryData(ProjectDataBitbucketRepository repo = null) {
        def fileName = this.getSavedDocumentName(sourceEnvironment)
        this.logger.debug("Retrieving saved document history with name '${fileName}' " +
            "in workspace '${this.steps.env.WORKSPACE}'.")
        if (!repo) {
            repo = new ProjectDataBitbucketRepository(steps)
        }
        def content = repo.loadFile(fileName)
        try {
            return content.collect { Map entry ->
                if (! entry.entryId) {
                    throw new IllegalArgumentException('EntryId cannot be empty')
                }
                if (! entry.projectVersion) {
                    throw new IllegalArgumentException('projectVersion cannot be empty')
                }
                return new DocumentHistoryEntry(
                    entry,
                    entry.entryId,
                    entry.projectVersion,
                    entry.previousProjectVersion,
                    entry.docVersion,
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

    @NonCPS
    List<DocumentHistoryEntry> getDocHistoryEntries() {
        this.data.clone()
    }

    @NonCPS
    List<Map> getDocGenFormat() {
        def issueTypes = JiraDataItem.TYPES - JiraDataItem.TYPE_DOCS
        def transformEntry =  { DocumentHistoryEntry e ->
            if (e.getEntryId() == 1L) {
                return [
                    entryId: e.getEntryId(),
                    docVersion: e.getDocVersion() ?: e.getEntryId(),
                    rational: e.getRational(),
                    ]
            }
            def formatedIssues = issueTypes.collect { type ->
                def issues = e[type] ?: []
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
                    docVersion: e.getDocVersion() ?: e.getEntryId(),
                    rational: e.getRational(),
                    issueType: formatedIssues + computeDocChaptersOfDocument(e),
            ]
        }
        sortDocHistories(this.data).collect { transformEntry(it) }
    }

    @SuppressWarnings(['UseCollectMany'])
    @NonCPS
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

    @NonCPS
    protected Map computeDocChaptersOfDocument(DocumentHistoryEntry entry) {
        def docIssues = SortUtil.sortHeadingNumbers(entry[JiraDataItem.TYPE_DOCS] ?: [], 'number')
            .collect {
                def issue = [action: it.action, key: "${it.key}", details: "${it.number} ${it.heading}"]
                if (it.action == CHANGE) {
                    issue.predecessors = it.predecessors.join(", ")
                }
                return issue
            }
        return [ type: 'documentation chapters',
                 (ADDED): docIssues.findAll { it.action == ADD },
                 (CHANGED): docIssues.findAll { it.action == CHANGE },
                 (DELETED): docIssues.findAll { it.action == DELETE },
        ]
    }

    @NonCPS
    protected String getSavedDocumentName(String environment = targetEnvironment) {
        def suffix = (documentName) ? '-' + documentName : ''
        return "documentHistory-${environment}${suffix}"
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

    // Find the previous environment where the document was generated, if it exists.
    // Otherwise, use the target environment.
    @NonCPS
    private getSourceEnvironment(String documentName, String targetEnvironment) {
        def documentType = LeVADocumentUtil.getTypeFromName(documentName)
        return LeVADocumentScheduler.getPreviousCreationEnvironment(documentType, targetEnvironment)
    }

    @NonCPS
    private Map computeDiscontinuations(Map jiraData, List<String> previousDocumentIssues) {
        (jiraData.discontinuationsPerType ?: [:])
            .collectEntries { String issueType, List<Map> issues ->
                def discont = discontinuedIssuesThatWereInDocument(issueType, previousDocumentIssues, issues)
                [(issueType): discont.collect { computeIssueContent(issueType, DELETE, it) } ]
            }
    }

    @NonCPS
    private static List<Map> discontinuedIssuesThatWereInDocument(String issuesType, List<String> previousDocIssues,
                                                                  List<Map> discontinued) {
        if (issuesType.equalsIgnoreCase(JiraDataItem.TYPE_DOCS)) {
            discontinued
        } else {
            discontinued.findAll { previousDocIssues.contains(it.key) }
        }
    }

    @NonCPS
    private static Map computeIssueContent(String issueType, String action, Map issue) {
        def result = [key: issue.key, action: action]
        if (JiraDataItem.TYPE_DOCS.equalsIgnoreCase(issueType)) {
            result << issue.subMap(['documents', 'number', 'heading'])
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

    @NonCPS
    private void checkIfAllIssuesHaveVersions(Collection<Map> jiraIssues) {
        if (jiraIssues) {
            def issuesWithNoVersion = jiraIssues.findAll { Map i ->
                (i.versions) ? false : true
            }

            if (!issuesWithNoVersion.isEmpty()) {
                //throw new RuntimeException('In order to build a coherent document history we need to have a' +
                //    ' version for all the elements. In this case, the following items have this state: ' +
                //    "'${issuesWithNoVersion*.key.join(', ')}'")
                this.allIssuesAreNotValidMessage = 'Document history not valid. We don\'t have a version for ' +
                    "the following elements'${issuesWithNoVersion.collect { it.key }.join(', ')}'. " +
                    'If you are not using versioning ' +
                    'and its automated document history you can ignore this warning. Otherwise, make sure ' +
                    'all the issues have a version attached to it.'
                this.allIssuesAreValid = false
            }
        }
    }

    @NonCPS
    private String createRational(DocumentHistoryEntry currentEntry) {
        if (currentEntry.getEntryId() == 1L) {
            return "Initial document version."
        }
        def versionText
        if (containsChanges(currentEntry)) {
            versionText = "Modifications for project version '${currentEntry.getProjectVersion()}'."
        } else {
            versionText = "No changes were made to this " +
                "document for project version '${currentEntry.getProjectVersion()}'."
        }
        return versionText + rationaleIfConcurrentVersionsAreFound(currentEntry)
    }

    @NonCPS
    private boolean containsChanges(DocumentHistoryEntry documentHistoryEntry) {
        for (String delegateType : JiraDataItem.TYPES) {
            String[] values = documentHistoryEntry.get(delegateType)
            if (values.length > 0) {
                return true
            }
        }
        return false
    }

    /**
     * Adds a rational in case concurrent versions are found. This can only be achieved
     * @param currentEntry current document history entry
     * @return rational message
     */
    @NonCPS
    private String rationaleIfConcurrentVersionsAreFound(DocumentHistoryEntry currentEntry) {
        def oldVersionsSimplified = this.data.collect {
            [id: it.getEntryId(), version: it.getProjectVersion(), previousVersion: it.getPreviousProjectVersion()]
        }.findAll { it.id != currentEntry.getEntryId() }
        def concurrentVersions = getConcurrentVersions(oldVersionsSimplified, currentEntry.getPreviousProjectVersion())

        if (currentEntry.getPreviousProjectVersion() && oldVersionsSimplified.size() == concurrentVersions.size() &&
            LeVADocumentUtil.isFullDocument(documentName)) {
            throw new RuntimeException('Inconsistent state found when building DocumentHistory. ' +
                "Project has as previous project version '${currentEntry.getPreviousProjectVersion()}' " +
                'but no document history containing that ' +
                'version can be found. Please check the file named ' +
                "'${this.getSavedDocumentName(sourceEnvironment)}.json'" +
                ' in your release manager repository')
        }

        if (concurrentVersions.isEmpty()) {
            return ''
        } else {
            def pluralS = (concurrentVersions.size() == 1) ? '' : 's'
            return " This document version invalidates the changes done in document version${pluralS} " +
                "'${currentEntry.getProjectVersion()}/" +
                "${concurrentVersions.join("', '${currentEntry.getProjectVersion()}/")}'."
        }
    }

    private DocumentHistoryEntry parseJiraDataToDocumentHistoryEntry(Map jiraData, List<String> keysInDocument) {
        logger.debug("Parsing jira data to document history")
        def projectVersion = jiraData.version
        def previousProjectVersion = jiraData.previousVersion ?: ''
        this.allIssuesAreValid = true

        def versionMap = this.computeEntryData(jiraData, projectVersion, keysInDocument)
        if (!this.allIssuesAreValid) {
            logger.warn(this.allIssuesAreNotValidMessage)
        }
        logger.debug("parseJiraDataToDocumentHistoryEntry: versionMap = ${versionMap.toString()}")
        return new DocumentHistoryEntry(versionMap, this.latestVersionId, projectVersion, previousProjectVersion,
            "${projectVersion}/${this.latestVersionId}", '')
    }

    @NonCPS
    private Map computeEntryData(Map jiraData, String projectVersion, List<String> keysInDocument) {
        def previousDocumentIssues = this.getDocumentKeys()
        def additionsAndUpdates = this.computeAdditionsAndUpdates(jiraData, projectVersion)
        def discontinuations = computeDiscontinuations(jiraData, previousDocumentIssues)

        def addUpdDisc = JiraDataItem.TYPES.collectEntries { String issueType ->
            [(issueType): (additionsAndUpdates[issueType] ?: [])
                + (discontinuations[issueType] ?: [])
            ]
        } as Map

        return this.computeActionsThatBelongToTheCurrentHistoryData(previousDocumentIssues, addUpdDisc, keysInDocument)
    }

    /**
     * From the set of all issues that belong to the current project version, <code>versionActions</code>,
     * determine the actual set of issues and actions to be included in the current document history being generated.
     * This is done based on the issues in the previous version of the same history, <code>previousDocIssues</code>,
     * and the list of issues that should go into the current version, <code>issuesInDoc</code>.
     * These are generated in function of the document type and sometimes also in the concrete component.
     *
     * The resulting set of issues is not in general a subset of <code>versionActions</code>,
     * because some discontinuations must be added.
     * These are discontinuations to issues that are not listed in <code>issuesInDoc</code>.
     *
     * @param previousDocIssues list of issue keys that were in the previous document version.
     * @param versionActions map with all the issues that belong to the current project version by issue type.
     * @param issuesInDoc list of the keys of the issues that should be included in the current document version.
     * @return the map with all the issues and actions to be included in the current document history entry, by type.
     */
    @NonCPS
    private Map computeActionsThatBelongToTheCurrentHistoryData (
        List<String> previousDocIssues, Map versionActions, List<String> issuesInDoc) {
        // Guard against the possibility that a null issuesInDoc is provided
        if (issuesInDoc == null) {
            issuesInDoc = []
        }

        // Traverse the collection of all the issues that belong to the current project version,
        // in order to determine which of them are to be included in the current history data.
        def issues = versionActions.collectEntries { issueType, actions ->
            def typeResult = actions.collect { Map action ->
                // If the key belongs to the current history data, include it regardless of the action
                if (issuesInDoc.contains(action.key)) {
                    return action
                }
                // If we are here, the current issue doesn't belong to the current document history entry,
                // but we still need to add discontinuations for some of these issues.
                def ret = []
                // If it is a discontinuation, add it unconditionally.
                if (DELETE.equalsIgnoreCase(action.action)) {
                    ret << action
                }
                // If this is not the first version, let's treat the predecessors of the current issue.
                // As the current issue won't be in the document anymore, the predecessors may need to disappear, too.
                if (this.latestVersionId > 1L) {
                    // Any predecessors that were in the previous document history are discontinued.
                    // Note that there will never be more than one predecessor, but it's still a list.
                    ret += (action.predecessors ?: []).findAll { previousDocIssues.contains(it) }
                        .collect { computeIssueContent(issueType, DELETE, [key: it]) }
                }
                return ret
            }
            [(issueType): typeResult.flatten()]
        }
        return issues
    }

    @NonCPS
    private Map computeAdditionsAndUpdates(Map jiraData, String projectVersion) {
        jiraData.findAll { JiraDataItem.TYPES.contains(it.key) }
            .collectEntries { String issueType, Map<String, Map> issues ->
                checkIfAllIssuesHaveVersions(issues.values())
                def issuesOfThisVersion = this.getIssueChangesForVersion(projectVersion, issueType, issues)
                [(issueType): issuesOfThisVersion]
            }
    }

    @NonCPS
    private List<Map> getIssueChangesForVersion(String version, String issueType, Map issues) {
        // Filter chapter issues for this document only
        if (issueType == JiraDataItem.TYPE_DOCS) {
            def docType = LeVADocumentUtil.getTypeFromName(this.documentName)
            issues = issues.findAll { it.value.documents.contains(docType) }
        }

        issues.findAll { it.value.versions?.contains(version) }
            .collect { issueKey, issue ->
                def isAnUpdate = issue.predecessors
                if (isAnUpdate) {
                    computeIssueContent(issueType, CHANGE, issue)
                } else {
                    computeIssueContent(issueType, ADD, issue)
                }
            }
    }

}
