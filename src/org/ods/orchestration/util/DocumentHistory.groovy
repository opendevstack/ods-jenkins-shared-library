package org.ods.orchestration.util

import com.cloudbees.groovy.cps.NonCPS
import org.ods.orchestration.service.leva.ProjectDataBitbucketRepository
import org.ods.util.ILogger
import org.ods.util.IPipelineSteps

/**
 * This class contains the logic for keeping a consistent document version.
 */
class DocumentHistory {

    class VersionEntry implements Map, Serializable {

        private final Long id
        private HashMap delegate
        private final String projectVersion
        private final String previousProjectVersion
        String rationaleConcurrentVersions = ""

        VersionEntry(Map map, Long id, String projectVersion, String previousProjectVersion) {
            this.delegate = new HashMap(map)
            this.id = id
            this.projectVersion = projectVersion
            this.previousProjectVersion = previousProjectVersion
        }

        @NonCPS
        @Override
        int size() {
            return delegate.size()
        }

        @NonCPS
        @Override
        boolean isEmpty() {
            return delegate.isEmpty()
        }

        @NonCPS
        @Override
        boolean containsKey(Object key) {
            return delegate.containsKey(key)
        }

        @NonCPS
        @Override
        boolean containsValue(Object value) {
            return delegate.containsValue(value)
        }

        @NonCPS
        @Override
        Object get(Object key) {
            return delegate.get(key)
        }

        @NonCPS
        @Override
        Object put(Object key, Object value) {
            return delegate.put(key, value)
        }

        @NonCPS
        @Override
        Object remove(Object key) {
            return delegate.remove(key)
        }

        @NonCPS
        @Override
        void putAll(Map m) {
            delegate.putAll(m)
        }

        @NonCPS
        @Override
        void clear() {
            delegate.clear()
        }

        @NonCPS
        @Override
        Set keySet() {
            return delegate.keySet()
        }

        @NonCPS
        @Override
        Collection values() {
            return delegate.values()
        }

        @NonCPS
        @Override
        Set<Entry> entrySet() {
            return delegate.entrySet()
        }

        @NonCPS
        Long getId() {
            return id
        }

        @NonCPS
        String getProjectVersion() {
            return projectVersion
        }

        @NonCPS
        String getPreviousProjectVersion() {
            return previousProjectVersion
        }

        @NonCPS
        Map getDelegate() {
            return delegate
        }

        @NonCPS
        VersionEntry cloneIt() {
            def bos = new ByteArrayOutputStream()
            def os = new ObjectOutputStream(bos)
            os.writeObject(this.delegate)
            def ois = new ObjectInputStream(new ByteArrayInputStream(bos.toByteArray()))

            def newDelegate = ois.readObject()
            VersionEntry result = new VersionEntry(newDelegate, id, projectVersion, previousProjectVersion)
            result.rationaleConcurrentVersions = this.rationaleConcurrentVersions
            return result
        }

    }

    protected IPipelineSteps steps
    protected ILogger logger
    protected List<VersionEntry> data = []
    protected String targetEnvironment
    /**
     * Autoincremental number containing the internal version Id of the document
     */
    protected Long latestVersionId

    DocumentHistory(IPipelineSteps steps, ILogger logger, Map jiraData, String targetEnvironment,
                    Long savedVersionId = null) {
        this.steps = steps
        this.logger = logger

        if (!targetEnvironment)
            throw new RuntimeException('Variable \'targetEnvironment\' cannot be empty for computing Document History')
        this.latestVersionId = 1L

        def newDocVersionEntry = parseJiraDataToVersionEntry(jiraData)
        if (savedVersionId) {
            this.latestVersionId = savedVersionId + 1L
            this.data = this.loadSavedDocHistoryData(targetEnvironment, savedVersionId)
            newDocVersionEntry.rationaleConcurrentVersions = rationaleIfConcurrentVersionsAreFound(newDocVersionEntry)

        }
        this.data.add(newDocVersionEntry)
    }

    private VersionEntry rationaleIfConcurrentVersionsAreFound(VersionEntry currentEntry) {
        def oldVersionsSimplified = (this.data.clone() as List<VersionEntry>).collect{
            [id: it.id, previousProjectVersion: it.previousProjectVersion]
        }.findAll { it.id != currentEntry.id}
        def concurrentVersions = oldVersionsSimplified.reverse()
            .takeWhile {it.projectVersion != currentEntry.previousProjectVersion}.collect{it.id}


        if (currentEntry.previousProjectVersion && oldVersionsSimplified.size() == concurrentVersions.size())
            throw new RuntimeException('Inconsistent state found when building DocumentHistory. Project has as previous ' +
                "project version ${currentEntry.previousProjectVersion} but no document history containing that " +
                "version can be found. Please check the file named '${this.getSavedDocumentName(currentEntry.id -1L)}.json'" +
                ' in your release manager repository')

        if (concurrentVersions.isEmpty()) {
            return null
        } else {
            def pluralS = (concurrentVersions.size() ==1)? '' : 's'
            return "This document version invalidates the changes done in version${pluralS} " +
                "'${concurrentVersions.join(', ')}'."
        }

    }

    VersionEntry getVersionEntry(Long id) {
        return this.data.find {it.id == id }
    }

    private VersionEntry parseJiraDataToVersionEntry(Map<String, Map> jiraData) {
        def projectVersion = jiraData.version
        def previousProjectVersion = jiraData.previousVersion
        def additionsAndUpdates = jiraData.findAll { Project.JiraDataItem.TYPES.contains(it.key) }.collectEntries {
            issueType, Map<String, Project.JiraDataItem> issues ->
                def issuesOfThisVersion = issues.findAll {it.value.version == projectVersion}.collect{
                    issueKey, issue ->
                    def isAnUpdate = ! issue.getOrDefault('predecessors', []).isEmpty()
                    if (isAnUpdate) {
                        [key: issueKey, action: 'change', predecessors: issue.predecessors]
                    } else {
                        [key: issueKey, action: 'add']
                    }
                }
            [(issueType): issuesOfThisVersion]
        }
        def discontinuations = jiraData.discontinuationsPerType.collectEntries {
            issueType, List<String> issueKeys ->
                [(issueType): issueKeys.collect{ [key: it, action: 'discontinue']}]
        }
        def versionMap = Project.JiraDataItem.TYPES.collectEntries{ String issueType ->
            additionsAndUpdates.getOrDefault(issueType, []) + discontinuations.getOrDefault(issueType, [])
        }

        return new VersionEntry(versionMap, this.latestVersionId, projectVersion, previousProjectVersion)
    }

    protected List<VersionEntry> loadSavedDocHistoryData(Long versionIdToRetrieve) {
        new ProjectDataBitbucketRepository(steps).loadFile(getSavedDocumentName(versionIdToRetrieve)) as List<VersionEntry>
    }

    protected String getSavedDocumentName(Long versionId) {
        return "documentHistory-${this.targetEnvironment}-${versionId}"
    }

}
