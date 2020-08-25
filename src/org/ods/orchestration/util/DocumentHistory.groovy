package org.ods.orchestration.util

import com.cloudbees.groovy.cps.NonCPS
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

    DocumentHistory load(Map jiraData, Long savedVersionId = null) {
        this.latestVersionId = (savedVersionId ?:0L) + 1L
        def newDocDocumentHistoryEntry = parseJiraDataToDocumentHistoryEntry(jiraData)
        if (this.allIssuesAreValid) {
            if (savedVersionId && savedVersionId != 0L) {
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

    List<DocumentHistoryEntry> loadSavedDocHistoryData(Long versionIdToRetrieve,
                                                       ProjectDataBitbucketRepository repo = null) {
        def fileName = this.getSavedDocumentName(versionIdToRetrieve)
        this.logger.debug("Retrieving saved document history with name '${fileName}'.")
        if (!repo) {
            repo = new ProjectDataBitbucketRepository(steps)
        }
        def content = repo.loadFile(fileName)
        try {
            content.collect{ Map entry ->
                if (! entry.entryId) {
                    throw new RuntimeException('EntryId cannot be empty')
                }
                if (! entry.projectVersion) {
                    throw new RuntimeException('projectVersion cannot be empty')
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
            throw new RuntimeException("Unable to load saved document history for file '${fileName}': ${e.message}", e)
        }
    }

    String saveDocHistoryData(ProjectDataBitbucketRepository repository) {
        repository.save(this.data, this.getSavedDocumentName(this.latestVersionId))
    }

    List<DocumentHistoryEntry> getDocHistoryEntries() {
        this.data
    }

    List<Map> getDocGenFormat(List<String> issueTypes) {
        def transformEntry =  { DocumentHistoryEntry e ->
            def formatedIssues = issueTypes.collect { type ->
                def issues = e.getOrDefault(type, [])
                def changed = issues.findAll { it.action == 'change' }.clone()
                    .collect { [key: it.key, predecessors: it.predecessors.join(", ")] }

                [ type: type,
                  added: SortUtil.sortIssuesByProperties(issues.findAll { it.action == 'add' }, ['key']),
                  changed: SortUtil.sortIssuesByProperties(changed, ['key']),
                  deleted: SortUtil.sortIssuesByProperties(issues.findAll { it.action == 'delete' }, ['key']),
                ]
            }

            return [entryId: e.getEntryId(),
                    rational: e.getRational(),
                    issueType: formatedIssues + computeDocChaptersOfDocument(e)
            ]
        }
        this.data.collect { transformEntry(it) }
    }

    protected static Map computeDocChaptersOfDocument(DocumentHistoryEntry entry) {
        def docIssues = SortUtil.sortHeadingNumbers(entry.getOrDefault(Project.JiraDataItem.TYPE_DOCS, [])
            .collect { [action: it.action, key: it.number] }, 'key')
        return [ type: 'document sections',
                 added: docIssues.findAll { it.action == 'add' },
                 changed: docIssues.findAll { it.action == 'change' },
                 deleted: docIssues.findAll { it.action == 'delete' },
        ]

    }

    protected String getSavedDocumentName(Long versionId) {
        versionId // TODO removeme
        def suffix = (documentType) ? "-" + documentType : ""
        //return "documentHistory-${this.targetEnvironment}-${versionId}${suffix}"
        return "documentHistory-${this.targetEnvironment}${suffix}"
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
                    " elements'${issuesWithNoVersion.collect {it.key }.join(', ')}'. " +
                    'If you are not using versioning ' +
                    'and its automated document history you can ignore this warning. Otherwise, make sure ' +
                    'all the issues have a version attached to it.')
                this.allIssuesAreValid = false
            }
        }
    }

    private String createRational(DocumentHistoryEntry currentEntry) {
        def versionText = "Modifications for project version '${currentEntry.getProjectVersion()}'."

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
        def concurrentVersions = getConcurrentVersions(oldVersionsSimplified, currentEntry.getPreviousProjectVersion())

        if (currentEntry.getPreviousProjectVersion() && oldVersionsSimplified.size() == concurrentVersions.size()) {
            throw new RuntimeException('Inconsistent state found when building DocumentHistory. ' +
                "Project has as previous project version ${currentEntry.getPreviousProjectVersion()} " +
                'but no document history containing that ' +
                'version can be found. Please check the file named ' +
                "'${this.getSavedDocumentName(currentEntry.getEntryId() - 1L)}.json'" +
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

    @NonCPS
    private static List<String> getConcurrentVersions(List<Map> versions, String previousProjVersion) {
        // DO NOT remove this method. Takewhile is not supported by Jenkins and must be used in a Non-CPS method
        versions.takeWhile { it.version != previousProjVersion }
            .collect { it.id }
    }

    private DocumentHistoryEntry parseJiraDataToDocumentHistoryEntry(Map jiraData) {
        logger.debug("Parsing jira data to document history")
        def projectVersion = jiraData.version
        def previousProjectVersion = jiraData.previousVersion ?: ''
        this.allIssuesAreValid = true
        def additionsAndUpdates = jiraData.findAll { Project.JiraDataItem.TYPES.contains(it.key) }
            .collectEntries { String issueType, Map<String, Map> issues ->
                checkIfAllIssuesHaveVersions(issues.values())
                def issuesOfThisVersion = getIssueChangesForVersion(projectVersion, issueType, issues)
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

    private List<Map> getIssueChangesForVersion(String version, String issueType, Map<String, Map> issues) {
        // Filter chapter issues for this document only
        if (issueType == Project.JiraDataItem.TYPE_DOCS) {
            issues = issues.findAll { it.value.documents.contains(this.documentType)}
        }

        issues.findAll { it.value.versions?.contains(version) }
            .collect { issueKey, issue ->
                def isAnUpdate = !issue.getOrDefault('predecessors', []).isEmpty()
                if (isAnUpdate) {
                    computeIssueContent(issueType, 'change', issue)
                } else {
                    computeIssueContent(issueType, 'add', issue)
                }
            }
    }

    private static Map computeIssueContent(String issueType, String action, Map issue) {
        def result = [key: issue.key, action: action]
        if (Project.JiraDataItem.TYPE_DOCS.equalsIgnoreCase(issueType)) {
            result << [documents: issue.documents, number: issue.number]
        }
        if (action.equalsIgnoreCase('change')){
            result << [predecessors: issue.predecessors]
        }
        return result
    }

}
