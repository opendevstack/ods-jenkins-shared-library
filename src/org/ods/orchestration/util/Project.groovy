package org.ods.orchestration.util

import com.cloudbees.groovy.cps.NonCPS
import groovy.json.JsonOutput
import org.apache.http.client.utils.URIBuilder
import org.ods.orchestration.service.leva.ProjectDataBitbucketRepository
import org.ods.orchestration.usecase.ComponentMismatchException
import org.ods.orchestration.usecase.JiraUseCase
import org.ods.orchestration.usecase.OpenIssuesException
import org.ods.services.GitService
import org.ods.services.NexusService
import org.ods.util.ILogger
import org.ods.util.IPipelineSteps
import org.yaml.snakeyaml.Yaml

import java.nio.file.Paths

@SuppressWarnings(['LineLength',
        'AbcMetric',
        'IfStatementBraces',
        'Instanceof',
        'CyclomaticComplexity',
        'GStringAsMapKey',
        'ImplementationAsType',
        'ParameterCount',
        'UseCollectMany',
        'MethodCount',
        'PublicMethodsBeforeNonPublicMethods'])
class Project {

    static final String IS_GXP_PROJECT_PROPERTY = 'PROJECT.IS_GXP'
    static final String DEFAULT_TEMPLATE_VERSION = '1.2'
    static final boolean IS_GXP_PROJECT_DEFAULT = true
    static final Map<String, List<String>> MANDATORY_CHAPTERS =
        [
            'CSD': ['1', '3.1'],
            'SSDS': ['1', '2.1', '3.1', '5.4'],
        ]
    private static final Map<String, Set<String>> MANDATORY_CHAPTER_INDEX = [:]
    static {
        def index = MANDATORY_CHAPTER_INDEX.withDefault { [] as Set<String> }
        MANDATORY_CHAPTERS.each { document, headingNumbers ->
            headingNumbers.each { headingNumber ->
                index[headingNumber] << document
            }
        }
    }

    class JiraDataItem implements Map, Serializable {

        static final String TYPE_BUGS = 'bugs'
        static final String TYPE_COMPONENTS = 'components'
        static final String TYPE_EPICS = 'epics'
        static final String TYPE_MITIGATIONS = 'mitigations'
        static final String TYPE_REQUIREMENTS = 'requirements'
        static final String TYPE_RISKS = 'risks'
        static final String TYPE_TECHSPECS = 'techSpecs'
        static final String TYPE_TESTS = 'tests'
        static final String TYPE_DOCS = 'docs'
        static final String TYPE_DOCTRACKING = 'docTrackings'

        static final List TYPES = [
            TYPE_BUGS,
            TYPE_COMPONENTS,
            TYPE_EPICS,
            TYPE_MITIGATIONS,
            TYPE_REQUIREMENTS,
            TYPE_RISKS,
            TYPE_TECHSPECS,
            TYPE_TESTS,
            TYPE_DOCS,
        ]

        static final List TYPES_WITH_STATUS = [
            TYPE_BUGS,
            TYPE_EPICS,
            TYPE_MITIGATIONS,
            TYPE_REQUIREMENTS,
            TYPE_RISKS,
            TYPE_TECHSPECS,
            TYPE_TESTS,
            TYPE_DOCS,
        ]

        static final List REGULAR_ISSUE_TYPES = [
            TYPE_BUGS,
            TYPE_EPICS,
            TYPE_MITIGATIONS,
            TYPE_REQUIREMENTS,
            TYPE_RISKS,
            TYPE_TECHSPECS,
            TYPE_TESTS,
        ]

        static final String ISSUE_STATUS_TODO = 'to do'
        static final String ISSUE_STATUS_DONE = 'done'
        static final String ISSUE_STATUS_CANCELLED = 'cancelled'

        static final String ISSUE_TEST_EXECUTION_TYPE_AUTOMATED = 'automated'

        private final String type
        private final HashMap delegate

        JiraDataItem(Map map, String type) {
            this.delegate = new HashMap(map)
            this.type = type
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
        String getType() {
            return type
        }

        @NonCPS
        Map getDelegate() {
            return delegate
        }

        @NonCPS
        JiraDataItem cloneIt() {
            def bos = new ByteArrayOutputStream()
            def os = new ObjectOutputStream(bos)
            os.writeObject(this.delegate)
            def ois = new ObjectInputStream(new ByteArrayInputStream(bos.toByteArray()))

            def newDelegate = ois.readObject()
            JiraDataItem result = new JiraDataItem(newDelegate, type)
            return result
        }

        // FIXME: why can we not invoke derived methods in short form, e.g. .resolvedBugs?
        // Reason: that is because when you do this.resolvedBugs it goes to the get method for delegate dictionary
        // and does deletage.resolvedBugs  And we have no entry there
        // An option would be to put some logic for this in the get() method of this class
        @NonCPS
        private List<JiraDataItem> getResolvedReferences(String type) {
            // Reference this within jiraResolved (contains readily resolved references to other entities)
            def item = Project.this.data.jiraResolved[this.type][this.key]
            def references = item && item[type] ? item[type] : []
            return references.findAll { it != null }
        }

        @NonCPS
        List<JiraDataItem> getResolvedBugs() {
            return this.getResolvedReferences(TYPE_BUGS)
        }

        @NonCPS
        List<JiraDataItem> getResolvedComponents() {
            return this.getResolvedReferences(TYPE_COMPONENTS)
        }

        @NonCPS
        List<JiraDataItem> getResolvedEpics() {
            return this.getResolvedReferences(TYPE_EPICS)
        }

        @NonCPS
        List<JiraDataItem> getResolvedMitigations() {
            return this.getResolvedReferences(TYPE_MITIGATIONS)
        }

        @NonCPS
        List<JiraDataItem> getResolvedSystemRequirements() {
            return this.getResolvedReferences(TYPE_REQUIREMENTS)
        }

        @NonCPS
        List<JiraDataItem> getResolvedRisks() {
            return this.getResolvedReferences(TYPE_RISKS)
        }

        @NonCPS
        List<JiraDataItem> getResolvedTechnicalSpecifications() {
            return this.getResolvedReferences(TYPE_TECHSPECS)
        }

        @NonCPS
        List<JiraDataItem> getResolvedTests() {
            return this.getResolvedReferences(TYPE_TESTS)
        }

    }

    class TestType {

        static final String ACCEPTANCE = 'Acceptance'
        static final String INSTALLATION = 'Installation'
        static final String INTEGRATION = 'Integration'
        static final String UNIT = 'Unit'

    }

    class LogReportType {

        static final String CHANGES = 'changes'
        static final String TARGET = 'target'
        static final String STATE = 'state'

     }

    class GampTopic {

        static final String AVAILABILITY_REQUIREMENT = 'Availability Requirement'
        static final String CONSTRAINT = 'Constraint'
        static final String FUNCTIONAL_REQUIREMENT = 'Functional Requirement'
        static final String INTERFACE_REQUIREMENT = 'Interface Requirement'

    }

    protected static final String BUILD_PARAM_VERSION_DEFAULT = 'WIP'

    protected static String METADATA_FILE_NAME = 'metadata.yml'

    protected IPipelineSteps steps
    protected GitService git
    protected JiraUseCase jiraUseCase
    protected ILogger logger
    protected Map config
    protected String targetProject
    protected Boolean isVersioningEnabled = false
    private String _gitReleaseBranch

    protected Map data = [:]

    Project(IPipelineSteps steps, ILogger logger, Map config = [:]) {
        this.steps = steps
        this.config = config
        this.logger = logger

        this.data.build = [
            hasFailingTests: false,
            hasUnexecutedJiraTests: false,
        ]

        this.data.documentHistories = [:]
    }

    Project init() {
        this.data.buildParams = this.loadBuildParams(steps)
        this.data.metadata = this.loadMetadata(METADATA_FILE_NAME)

        return this
    }

    // CAUTION! This needs to be called from the root of the release manager repo.
    // Otherwise the Git information cannot be retrieved correctly.
    Project initGitDataAndJiraUsecase(GitService git, JiraUseCase usecase) {
        this.git = git
        if (usecase) {
            // add to notify jira back, even in this super early case
            this.jiraUseCase = usecase
        }
        def version = this.data.buildParams.version
        def changeId = this.data.buildParams.changeId
        def targetEnvironmentToken = this.data.buildParams.targetEnvironmentToken
        def baseTag = null
        def targetTag = null
        if (!getIsWorkInProgress()) {
            def tagList = git.readBaseTagList(version, changeId, targetEnvironmentToken)
            baseTag = GitTag.readLatestBaseTag(tagList, version, changeId, targetEnvironmentToken)

            if (getIsAssembleMode()) {
                if (baseTag) {
                    targetTag = baseTag.nextIterationWithBuildNumber(steps.env.BUILD_NUMBER)
                } else {
                    targetTag = new GitTag(version, changeId, 0, steps.env.BUILD_NUMBER, targetEnvironmentToken)
                }
            } else {
                if (baseTag) {
                    targetTag = baseTag.withEnvToken(targetEnvironmentToken)
                } else {
                    throw new RuntimeException("Error: unable to find latest tag for ${version}/${changeId}.")
                }
            }
        }

        this.data.git = [
            commit: git.getCommitSha(),
            url: git.getOriginUrl(),
            baseTag: baseTag ? baseTag.toString() : '',
            targetTag: targetTag ? targetTag.toString() : '',
            author: git.getCommitAuthor(),
            message: git.getCommitMessage(),
            time: git.getCommitTime(),
        ]
        this.logger.debug "Using release manager commit: ${this.data.git.commit}"
    }

    Project load(GitService git, JiraUseCase jiraUseCase) {
        this.git = git
        this.jiraUseCase = jiraUseCase

        // FIXME: the quality of this function degraded with ODS 3.1 and needs a cleanup
        // with a clear concept for versioning (scattered across various places)
        this.data.jira = [project: [ : ]]
        this.data.jira.issueTypes = this.loadJiraDataIssueTypes()
        this.data.jira << this.loadJiraData(this.jiraProjectKey)

        // Get more info of the versions from Jira
        this.data.jira.project.version = this.loadCurrentVersionDataFromJira()

        def version = null
        if (this.isVersioningEnabled) {
            version = this.getVersionName()
        }

        // FIXME: contrary to the comment below, the bug data from this method is still relevant
        // implementation needs to be cleaned up and bug data should be delivered through plugin's
        // REST endpoint, not plain Jira
        this.data.jira.bugs = this.loadJiraDataBugs(this.data.jira.tests, version) // TODO removeme when endpoint is updated
        this.data.jira = this.convertJiraDataToJiraDataItems(this.data.jira)
        this.data.jiraResolved = this.resolveJiraDataItemReferences(this.data.jira)

        this.data.jira.trackingDocs = this.loadJiraDataTrackingDocs(version)
        this.data.jira.trackingDocsForHistory = this.loadJiraDataTrackingDocs()
        this.data.jira.undone = this.computeWipJiraIssues(this.data.jira)
        this.data.jira.undoneDocChapters = this.computeWipDocChapterPerDocument(this.data.jira)

        this.logger.debug "WIP_Jira_Issues: ${this.data.jira.undone}"
        this.logger.debug "WIP_Jira_Chapters: ${this.data.jira.undoneDocChapters}"

        if (this.hasWipJiraIssues()) {
            this.logger.warn "WIP_Jira_Issues: ${this.data.jira.undone}"
            String message = ProjectMessagesUtil.generateWIPIssuesMessage(this)

            if (!this.isWorkInProgress) {
                throw new OpenIssuesException(message)
            }

            this.logger.debug "addCommentInJiraReleaseStatus: ${message}"
            this.addCommentInReleaseStatus(message)
        }

        if (this.jiraUseCase.jira) {
            logger.debug("Verify that each unit test in Jira project ${this.key} has exactly one component assigned.")
            def faultMap = [:]
            this.data.jira.tests
                .findAll { it.value.get("testType") == "Unit" }
                .each { entry ->
                    if (entry.value.get("components").size() != 1) {
                        faultMap.put(entry.key, entry.value.get("components").size())
                    }
                }
            if (faultMap.size() != 0) {
                def faultyTestIssues = faultMap.keySet()
                    .collect { key -> key + ": " + faultMap.get(key) + "; " }
                    .inject("") { temp, val -> temp + val }
                throw new IllegalArgumentException("Error: unit tests must have exactly 1 component assigned. Following unit tests have an invalid number of components: ${faultyTestIssues}")
            }
        }

        this.data.documents = [:]
        this.data.openshift = [:]

        this.jiraUseCase.updateJiraReleaseStatusBuildNumber()
        return this
    }

    @NonCPS
    Map<String, List> getWipJiraIssues() {
        return this.data.jira.undone
    }

    @NonCPS
    boolean hasWipJiraIssues() {
        def values = this.getWipJiraIssues().values()
        values = values.collect { it instanceof Map ? it.values() : it }.flatten()
        return !values.isEmpty()
    }

    @NonCPS
    protected Map<String, List> computeWipJiraIssues(Map data) {
        Map<String, List> result = [:]
        JiraDataItem.REGULAR_ISSUE_TYPES.each { type ->
            if (data.containsKey(type)) {
                result[type] = data[type].findAll { k, v -> isIssueWIP(v) }.keySet() as List<String>
            }
        }
        def docs = computeWIPDocChapters(data)
        if (docs != null) {
            result[JiraDataItem.TYPE_DOCS] = docs.keySet() as List<String>
        }

        return result
    }

    /**
     * Gets the document chapter issues and puts in a format ready to query from levadocumentusecase when retrieving
     * the sections not done
     * @param data jira data
     * @return dict with map documentTypes -> sectionsNotDoneKeys
     */
    @NonCPS
    protected Map<String,List<String>> computeWipDocChapterPerDocument(Map data) {
        Map <String, List<String>> docChaptersPerDocument = [:]
        def defaultingWrapper = docChaptersPerDocument.withDefault { [] }
        Map <String, Map> wipDocs = computeWIPDocChapters(data)

        wipDocs?.each {chapterKey, docChapter ->
            docChapter.documents.each { document ->
                defaultingWrapper[document] << chapterKey
            }
        }
        return docChaptersPerDocument
    }

    @NonCPS
    private Map<String, Map> computeWIPDocChapters(Map data) {
        def docs = data[JiraDataItem.TYPE_DOCS]
        return docs?.findAll { k, v -> isDocChapterMandatory(v) && !isIssueDone(v) }
    }

    @NonCPS
    protected boolean isIssueWIP(Map issue) {
        return (!issue.status?.equalsIgnoreCase(JiraDataItem.ISSUE_STATUS_DONE) &&
            !issue.status.equalsIgnoreCase(JiraDataItem.ISSUE_STATUS_CANCELLED))
    }

    @NonCPS
    boolean isIssueDone(Map issue) {
        return issue.status?.equalsIgnoreCase(JiraDataItem.ISSUE_STATUS_DONE)
    }

    @NonCPS
    boolean isIssueToBeShown(Map issue) {
        if (isGxp()) {
            return !issue.status?.equalsIgnoreCase(JiraDataItem.ISSUE_STATUS_CANCELLED)
        } else {
            return issue.status?.equalsIgnoreCase(JiraDataItem.ISSUE_STATUS_DONE)
        }
    }

    @NonCPS
    boolean isDocChapterMandatory(Map doc) {
        if (this.isGxp()) {
            return true
        }

        def documents = MANDATORY_CHAPTER_INDEX[doc.number]
        if (documents == null) {
            return false
        }

        return !documents.disjoint(doc.documents)
    }

    @NonCPS
    protected Map convertJiraDataToJiraDataItems(Map data) {
        JiraDataItem.TYPES.each { type ->
            if (data.containsKey(type)) {
                data[type] = data[type].collectEntries { key, item ->
                    [key, new JiraDataItem(item, type)]
                }
            } //else {
                //throw new IllegalArgumentException(
                //    "Error: Jira data does not include references to items of type '${type}'.")
            //}
        }

        return data
    }

    @NonCPS
    List<JiraDataItem> getAutomatedTests(String componentName = null, List<String> testTypes = []) {
        return this.data.jira.tests.findAll { key, testIssue ->
            return isAutomatedTest(testIssue) && hasGivenTypes(testTypes, testIssue) && hasGivenComponent(testIssue, componentName)
        }.values() as List
    }

    @NonCPS
    boolean isAutomatedTest(testIssue) {
        testIssue.executionType?.toLowerCase() == JiraDataItem.ISSUE_TEST_EXECUTION_TYPE_AUTOMATED
    }

    @NonCPS
    boolean hasGivenTypes(List<String> testTypes, testIssue) {
        def result = true
        if (testTypes) {
            result = testTypes*.toLowerCase().contains(testIssue.testType.toLowerCase())
        }
        return result
    }

    @NonCPS
    boolean hasGivenComponent(testIssue, String componentName) {
        def result = true
        if (componentName) {
            result = testIssue.getResolvedComponents().collect {
                if (!it || !it.name) {
                    throw new RuntimeException("Error with testIssue key: ${testIssue.key}, no component assigned or it is wrong.")
                }
                it.name.toLowerCase()
            }.contains(componentName.toLowerCase())
        }
        return result
    }

    @NonCPS
    Map getEnumDictionary(String name) {
        return this.data.jira.project.enumDictionary[name]
    }

    @NonCPS
    Map getProjectProperties() {
        return this.data.jira.project.projectProperties
    }

    @NonCPS
    List<JiraDataItem> getAutomatedTestsTypeAcceptance(String componentName = null) {
        return this.getAutomatedTests(componentName, [TestType.ACCEPTANCE])
    }

    @NonCPS
    List<JiraDataItem> getAutomatedTestsTypeInstallation(String componentName = null) {
        return this.getAutomatedTests(componentName, [TestType.INSTALLATION])
    }

    @NonCPS
    List<JiraDataItem> getAutomatedTestsTypeIntegration(String componentName = null) {
        return this.getAutomatedTests(componentName, [TestType.INTEGRATION])
    }

    @NonCPS
    List<JiraDataItem> getAutomatedTestsTypeUnit(String componentName = null) {
        return this.getAutomatedTests(componentName, [TestType.UNIT])
    }

    boolean getIsPromotionMode() {
        isPromotionMode(buildParams.targetEnvironmentToken)
    }

    String getTargetEnvironmentToken() {
        buildParams.targetEnvironmentToken
    }

    boolean getIsAssembleMode() {
        !getIsPromotionMode()
    }

    boolean getIsVersioningEnabled() {
        isVersioningEnabled
    }

    static boolean isPromotionMode(String targetEnvironmentToken) {
        ['Q', 'P'].contains(targetEnvironmentToken)
    }

    boolean promotingToProd() {
        buildParams.targetEnvironmentToken == 'P'
    }

    boolean getIsWorkInProgress() {
        isWorkInProgress(buildParams.version)
    }

    boolean isDeveloperPreviewMode() {
        return BUILD_PARAM_VERSION_DEFAULT.equalsIgnoreCase(this.data.buildParams.version) &&
                this.data.buildParams.targetEnvironmentToken == "D"
    }

    static boolean isWorkInProgress(String version) {
        version == BUILD_PARAM_VERSION_DEFAULT
    }

    static String envStateFileName(String targetEnvironment) {
        "${MROPipelineUtil.ODS_STATE_DIR}/${targetEnvironment}.json"
    }

    @NonCPS
    boolean isGxp() {
        String isGxp = projectProperties?."PROJECT.IS_GXP"
        return isGxp != null ? isGxp.toBoolean() : IS_GXP_PROJECT_DEFAULT
    }

    String getEnvStateFileName() {
        envStateFileName(buildParams.targetEnvironment)
    }

    Map getBuildParams() {
        return this.data.buildParams
    }

    String getEnvironmentParamsFile() {
        def envParamsFile = "${steps.env.WORKSPACE}/${buildParams.targetEnvironment}.env"
        if (!steps.fileExists(envParamsFile)) {
            envParamsFile = ''
        }
        envParamsFile
    }

    Map getEnvironmentParams(String envParamsFile) {
        def envParams = [:]
        if (envParamsFile) {
            def paramsFileContent = steps.readFile(file: envParamsFile)
            def params = paramsFileContent.split('\n')
            envParams = params.collectEntries {
                if (it.trim().size() > 0 && !it.trim().startsWith('#')) {
                    def vals = it.split('=')
                    [vals.first().trim(), vals[1..vals.size() - 1].join('=').trim()]
                } else {
                    [:]
                }
            }
        }
        envParams
    }

    void setOpenShiftData(String sessionApiUrl) {
        def envConfig = getEnvironmentConfig()
        def targetApiUrl = envConfig?.apiUrl
        if (!targetApiUrl) {
            targetApiUrl = sessionApiUrl
        }
        this.data.openshift['sessionApiUrl'] = sessionApiUrl
        this.data.openshift['targetApiUrl'] = targetApiUrl
    }

    @NonCPS
    boolean getTargetClusterIsExternal() {
        def isExternal = false
        def sessionApiUrl = this.data.openshift.sessionApiUrl
        def targetApiUrl = this.data.openshift.targetApiUrl
        def targetApiUrlMatcher = targetApiUrl =~ /:[0-9]+$/
        if (targetApiUrlMatcher.find()) {
            isExternal = sessionApiUrl != targetApiUrl
        } else {
            def sessionApiUrlWithoutPort = sessionApiUrl.split(':').dropRight(1).join(':')
            isExternal = sessionApiUrlWithoutPort != targetApiUrl
        }
        isExternal
    }

    String getSourceEnv() {
        ['dev': 'dev', 'qa': 'dev', 'prod': 'qa'].get(buildParams.targetEnvironment)
    }

    String getBaseTag() {
        this.data.git.baseTag
    }

    String getTargetTag() {
        this.data.git.targetTag
    }

    boolean getVersionedDevEnvsEnabled() {
        this.config.get('versionedDevEnvs', false)
    }

    String getSourceProject() {
        def sEnv = Project.getConcreteEnvironment(
            getSourceEnv(),
            buildParams.version.toString(),
            getVersionedDevEnvsEnabled()
        )
        "${getKey()}-${sEnv}"
    }

    static String getConcreteEnvironment(String environment, String version, boolean versionedDevEnvsEnabled) {
        if (versionedDevEnvsEnabled && environment == 'dev' && version != BUILD_PARAM_VERSION_DEFAULT) {
            def cleanedVersion = version.replaceAll('[^A-Za-z0-9-]', '-').toLowerCase()
            environment = "${environment}-${cleanedVersion}"
        } else if (environment == 'qa') {
            environment = 'test'
        }
        environment
    }

    static List<String> getBuildEnvironment(IPipelineSteps steps, boolean debug = false, boolean versionedDevEnvsEnabled = false) {
        def params = loadBuildParams(steps)

        def concreteEnv = getConcreteEnvironment(params.targetEnvironment, params.version, versionedDevEnvsEnabled)

        return [
            "DEBUG=${debug}",
            'MULTI_REPO_BUILD=true',
            "MULTI_REPO_ENV=${concreteEnv}",
            "MULTI_REPO_ENV_TOKEN=${params.targetEnvironmentToken}",
            "RELEASE_PARAM_CHANGE_ID=${params.changeId}",
            "RELEASE_PARAM_CHANGE_DESC=${params.changeDescription}",
            "RELEASE_PARAM_CONFIG_ITEM=${params.configItem}",
            "RELEASE_PARAM_VERSION=${params.version}",
            "RELEASE_STATUS_JIRA_ISSUE_KEY=${params.releaseStatusJiraIssueKey}",
        ]
    }

    @NonCPS
    List getCapabilities() {
        return this.data.metadata.capabilities
    }

    @NonCPS
    Object getCapability(String name) {
        def entry = this.getCapabilities().find { it instanceof Map ? it.find { it.key == name } : it == name }
        if (entry) {
            return entry instanceof Map ? entry[name] : true
        }

        return null
    }

    @NonCPS
    List<JiraDataItem> getBugs() {
        return this.data.jira.bugs.values() as List
    }

    @NonCPS
    List<JiraDataItem> getComponents() {
        return this.data.jira.components.values() as List
    }

    @NonCPS
    String getDescription() {
        return this.data.metadata.description
    }

    @NonCPS
    List<Map> getDocumentTrackingIssues() {
        return this.data.jira.trackingDocs.values() as List
    }

    @NonCPS
    List<Map> getDocumentTrackingIssues(List<String> labels) {
        def result = []

        def issues = this.getDocumentTrackingIssues()
        labels.each { label ->
            issues.each { issue ->
                if (issue.labels.collect { it.toLowerCase() }.contains(label.toLowerCase())) {
                    result << [key: issue.key, status: issue.status]
                }
            }
        }

        return result.unique()
    }

    @NonCPS
    List<Map> getDocumentTrackingIssuesForHistory() {
        return this.data.jira.trackingDocsForHistory.values() as List
    }

    @NonCPS
    List<Map> getDocumentTrackingIssuesForHistory(List<String> labels) {
        def result = []

        def issues = this.getDocumentTrackingIssuesForHistory()
        labels.each { label ->
            issues.each { issue ->
                if (issue.labels.collect { it.toLowerCase() }.contains(label.toLowerCase())) {
                    result << [key: issue.key, status: issue.status]
                }
            }
        }

        return result.unique()
    }

    @NonCPS
    List<Map> getDocumentTrackingIssuesNotDone(List<String> labels) {
        return this.getDocumentTrackingIssues(labels).findAll {
            !it.status.equalsIgnoreCase(JiraDataItem.ISSUE_STATUS_DONE)
        }
    }

    Map getGitData() {
        return this.data.git
    }

    protected URI getGitURLFromPath(String path, String remote = 'origin') {
        if (!path?.trim()) {
            throw new IllegalArgumentException("Error: unable to get Git URL. 'path' is undefined.")
        }

        if (!path.startsWith(this.steps.env.WORKSPACE)) {
            throw new IllegalArgumentException("Error: unable to get Git URL. 'path' must be inside the Jenkins workspace: ${path}")
        }

        if (!remote?.trim()) {
            throw new IllegalArgumentException("Error: unable to get Git URL. 'remote' is undefined.")
        }

        def result = null

        this.steps.dir(path) {
            result = this.steps.sh(
                    label: "Get Git URL for repository at path '${path}' and origin '${remote}'",
                    script: "git config --get remote.${remote}.url",
                    returnStdout: true
            ).trim()
        }

        return new URIBuilder(result).build()
    }

    @NonCPS
    List<JiraDataItem> getEpics() {
        return this.data.jira.epics.values() as List
    }

    @NonCPS
    Map<String, DocumentHistory> getDocumentHistories() {
        return this.data.documentHistories
    }

    String getId() {
        return this.data.jira.project.id
    }

    Map getVersion() {
        return this.data.jira.project.version
    }

    String getVersionName() {
        return this.data.jira.version
    }

    String getPreviousVersionId() {
        return this.data.jira.project.previousVersion?.id
    }

    /**
     * Obtains the mapping of Jira fields for a given issue type from the saved data
     * @param issueTypeName Jira issue type
     * @return Map containing [id: "customfield_XYZ", name:"name shown in jira"]
     */
    @NonCPS
    Map getJiraFieldsForIssueType(String issueTypeName) {
        return this.data.jira?.issueTypes[issueTypeName]?.fields ?: [:]
    }

    String getKey() {
        return this.data.metadata.id
    }

    String getJiraProjectKey() {
        def services = this.getServices()
        if (services?.jira?.project) {
            return services.jira.project
        }

        return getKey()
    }

    @NonCPS
    List<JiraDataItem> getMitigations() {
        return this.data.jira.mitigations.values() as List
    }

    String getName() {
        return this.data.metadata.name
    }

    @NonCPS
    List<Map> getRepositories() {
        return this.data.metadata.repositories
    }

    @NonCPS
    List<JiraDataItem> getRequirements() {
        return this.data.jira.requirements.values() as List
    }

    Map getEnvironments() {
        return this.data.metadata.environments
    }

    @NonCPS
    List<JiraDataItem> getRisks() {
        return this.data.jira.risks.values() as List
    }

    @NonCPS
    Map getServices() {
        return this.data.metadata.services
    }

    @NonCPS
    List<JiraDataItem> getSystemRequirements(String componentName = null, List<String> gampTopics = []) {
        return this.data.jira.requirements.findAll { key, req ->
            def result = true

            if (result && componentName) {
                result = req.getResolvedComponents().collect { it.name.toLowerCase() }.
                        contains(componentName.toLowerCase())
            }

            if (result && gampTopics) {
                result = gampTopics.collect { it.toLowerCase() }.contains(req.gampTopic.toLowerCase())
            }

            return result
        }.values() as List
    }

    @NonCPS
    List<JiraDataItem> getSystemRequirementsTypeAvailability(String componentName = null) {
        return this.getSystemRequirements(componentName, [GampTopic.AVAILABILITY_REQUIREMENT])
    }

    @NonCPS
    List<JiraDataItem> getSystemRequirementsTypeConstraints(String componentName = null) {
        return this.getSystemRequirements(componentName, [GampTopic.CONSTRAINT])
    }

    @NonCPS
    List<JiraDataItem> getSystemRequirementsTypeFunctional(String componentName = null) {
        return this.getSystemRequirements(componentName, [GampTopic.FUNCTIONAL_REQUIREMENT])
    }

    @NonCPS
    List<JiraDataItem> getSystemRequirementsTypeInterfaces(String componentName = null) {
        return this.getSystemRequirements(componentName, [GampTopic.INTERFACE_REQUIREMENT])
    }

    @NonCPS
    List<JiraDataItem> getTechnicalSpecifications(String componentName = null) {
        return this.data.jira.techSpecs.findAll { key, techSpec ->
            def result = true

            if (result && componentName) {
                result = techSpec.getResolvedComponents().collect { it.name.toLowerCase() }.
                        contains(componentName.toLowerCase())
            }

            return result
        }.values() as List
    }

    @NonCPS
    List<JiraDataItem> getTests() {
        return this.data.jira.tests.values() as List
    }

    @NonCPS
    List<JiraDataItem> getDocumentChaptersForDocument(String document) {
        def docs = this.data.jira[JiraDataItem.TYPE_DOCS] ?: [:]
        return docs.findAll { k, v -> v.documents && v.documents.contains(document) }.values() as List
    }

    @NonCPS
    List<String> getWIPDocChaptersForDocument(String documentType) {
        def docs = this.getWIPDocChapters()
        return docs[documentType] ?: []
    }

    @NonCPS
    Map getWIPDocChapters() {
        return this.data.jira.undoneDocChapters ?: [:]
    }

    Map getEnvironmentConfig() {
        def environments = getEnvironments()
        environments[buildParams.targetEnvironment]
    }

    // Deprecated in favour of getOpenShiftTargetApiUrl
    String getOpenShiftApiUrl() {
        this.data.openshift.targetApiUrl
    }

    String getOpenShiftTargetApiUrl() {
        this.data.openshift.targetApiUrl
    }

    @NonCPS
    boolean hasCapability(String name) {
        def collector = {
            return (it instanceof Map) ? it.keySet().first().toLowerCase() : it.toLowerCase()
        }

        return this.capabilities.collect(collector).contains(name.toLowerCase())
    }

    @NonCPS
    boolean hasFailingTests() {
        return this.data.build.hasFailingTests
    }

    @NonCPS
    boolean hasUnexecutedJiraTests() {
        return this.data.build.hasUnexecutedJiraTests
    }

    static boolean isTriggeredByChangeManagementProcess(steps) {
        def changeId = steps.env.changeId?.trim()
        def configItem = steps.env.configItem?.trim()
        return changeId && configItem
    }

    String getGitReleaseBranch() {
        if (null == _gitReleaseBranch) {
            _gitReleaseBranch = GitService.getReleaseBranch(buildParams.version)
        }
        return _gitReleaseBranch
    }

    void setGitReleaseBranch(String gitReleaseBranch) {
        _gitReleaseBranch = gitReleaseBranch
    }

    String getTargetProject() {
        this.targetProject
    }

    String setTargetProject(def proj) {
        this.targetProject = proj
    }

    @NonCPS
    boolean historyForDocumentExists(String document) {
        return this.getHistoryForDocument(document) ? true : false
    }

    @NonCPS
    DocumentHistory getHistoryForDocument(String document) {
        return this.documentHistories[document]
    }

    @NonCPS
    Long getDocumentVersionFromHistories(String documentType) {
        def history = getHistoryForDocument(documentType)
        if (!history) {
            // All docHistories for DTR and TIR should have the same version
            history = this.documentHistories.find {
                LeVADocumentUtil.getTypeFromName(it.key) == documentType
            }?.value
        }
        return history?.version
    }

    @NonCPS
    void setHistoryForDocument(DocumentHistory docHistory, String document) {
        this.documentHistories[document] = docHistory
    }

    static Map loadBuildParams(IPipelineSteps steps) {
        def releaseStatusJiraIssueKey = steps.env.releaseStatusJiraIssueKey?.trim()
        if (isTriggeredByChangeManagementProcess(steps) && !releaseStatusJiraIssueKey) {
            throw new IllegalArgumentException(
                    "Error: unable to load build param 'releaseStatusJiraIssueKey': undefined")
        }

        def version = steps.env.version?.trim() ?: BUILD_PARAM_VERSION_DEFAULT
        def targetEnvironment = (steps.env.environment?.trim() ?: 'dev').toLowerCase()
        if (!['dev', 'qa', 'prod'].contains(targetEnvironment)) {
            throw new IllegalArgumentException("Error: 'environment' build param must be one of 'DEV', 'QA' or 'PROD'.")
        }
        def targetEnvironmentToken = targetEnvironment[0].toUpperCase()

        def changeId = steps.env.changeId?.trim() ?: 'UNDEFINED'
        def configItem = steps.env.configItem?.trim() ?: 'UNDEFINED'
        def changeDescription = steps.env.changeDescription?.trim() ?: 'UNDEFINED'
        // Set rePromote=true if an existing tag should be deployed again
        def rePromote = true
        if (steps.env.rePromote && 'false'.equalsIgnoreCase(steps.env.rePromote.trim())) {
            rePromote = false
        }

        return [
            changeDescription: changeDescription,
            changeId: changeId,
            configItem: configItem,
            releaseStatusJiraIssueKey: releaseStatusJiraIssueKey,
            targetEnvironment: targetEnvironment,
            targetEnvironmentToken: targetEnvironmentToken,
            version: version,
            rePromote: rePromote,
        ]
    }

    /**
     * Checks if the JIRA version supports the versioning feature
     * If jira or JiraUsecase is not enabled -> false
     * If templates version is 1.0 -> false
     * Otherwise, check from Jira
     * @ true if versioning is enabled
     */
    boolean checkIfVersioningIsEnabled(String projectKey, String versionName) {
        if (!this.jiraUseCase) return false
        if (!this.jiraUseCase.jira) return false
        def levaDocsCapability = this.getCapability('LeVADocs')
        if (levaDocsCapability && levaDocsCapability?.templatesVersion == '1.0') {
            return false
        }
        return this.jiraUseCase.jira.isVersionEnabledForDelta(projectKey, versionName)
    }

    /**
     * Checks if the JIRA components match the repositories
     * If jira or JiraUsecase is not enabled -> false
     * Otherwise, check from Jira
     * @result true if jira is enabled and there is no mismatch, and false if not enabled
     * @throw ComponentMismatchException if there is a component mismatch
     */
    boolean checkComponentsMismatch() {
        if (!this.jiraUseCase) return false
        if (!this.jiraUseCase.jira) return false

        def match = jiraUseCase.jira.checkComponentsMismatch(this.key, this.getVersionFromReleaseStatusIssue())
        if (match.deployableState != "DEPLOYABLE") {
            throw new ComponentMismatchException(match.message)
        }

        return true
    }

    /**
     * Get jira components if enabled
     * @result Map of jira components if jira is enabled, otherwise null
     */
    Map getJiraProjectComponents() {
        if (this.jiraUseCase && this.jiraUseCase.jira) {
            return this.jiraUseCase.jira.getProjectComponents(this.key)
        } else {
            return null
        }
    }

    protected Map loadJiraData(String projectKey) {
        def result = [
                components: [:],
                epics: [:],
                mitigations: [:],
                project: [:],
                requirements: [:],
                risks: [:],
                techSpecs: [:],
                tests: [:],
        ]

        if (this.jiraUseCase && this.jiraUseCase.jira) {
            // FIXME: getVersionFromReleaseStatusIssue loads data from Jira and should therefore be called not more
            // than once. However, it's also called via this.project.versionFromReleaseStatusIssue in JiraUseCase.groovy.
            def currentVersion = this.getVersionFromReleaseStatusIssue() // TODO why is param.version not sufficient here?

            this.isVersioningEnabled = this.checkIfVersioningIsEnabled(projectKey, currentVersion)
            if (this.isVersioningEnabled) {
                // We detect the correct version even if the build is WIP
                logger.info("Project has versioning enabled.")
                result = this.loadJiraDataForCurrentVersion(projectKey, currentVersion)
            } else {
                // TODO remove in ODS 4.0 version
                logger.info("Versioning not supported for this release")
                result = this.loadFullJiraData(projectKey)
            }
        } else {
            logger.warn("WARNING: Could *not* retrieve data from Jira.")
            if (! this.jiraUseCase) {
                logger.warn("WARNING: Reason: this.jiraUseCase has no value")
            } else {
                if (! this.jiraUseCase.jira) {
                    logger.warn("WARNING: Reason: this.jiraUseCase.jira has no value")
                }
            }
            logger.warn("WARNING: Without Jira data, we might not work as expected.")
        }

        return result
    }

    protected String getVersionFromReleaseStatusIssue() {
        // TODO review if it's possible to use Project.getVersionName()?
        return this.jiraUseCase.getVersionFromReleaseStatusIssue()
    }

    protected Map loadFullJiraData(String projectKey) {
        def result = this.jiraUseCase.jira.getDocGenData(projectKey)
        if (result?.project?.id == null) {
            throw new IllegalArgumentException(
                    "Error: unable to load documentation generation data from Jira. 'project.id' is undefined.")
        }
        def docChapterData = this.getDocumentChapterData(projectKey)
        result << [(JiraDataItem.TYPE_DOCS as String): docChapterData]
        return result
    }

    protected Map loadVersionJiraData(String projectKey, String versionName) {
        def result = this.jiraUseCase.jira.getDeltaDocGenData(projectKey, versionName)
        if (result?.project?.id == null) {
            throw new IllegalArgumentException(
                "Error: unable to load documentation generation data from Jira. 'project.id' is undefined.")
        }

        def docChapterData = this.getDocumentChapterData(projectKey, versionName)
        result << [(JiraDataItem.TYPE_DOCS as String): docChapterData]
        return result
    }

    protected Map<String, Map> getDocumentChapterData(String projectKey, String versionName = null) {
        def docChapters = this.jiraUseCase.getDocumentChapterData(projectKey, versionName)
        if (docChapters.isEmpty() && !this.isVersioningEnabled) {
            //TODO remove for ODS 4.0
            //If versioning is not enabled, the query should always return results. If not, there is an issue with
            // the jira project itself.
            throw new IllegalStateException("Error: could not find document chapters for project ${projectKey}.")
        } else {
            return docChapters
        }
    }

    protected Map loadJiraDataForCurrentVersion(String projectKey, String versionName) {
        def result = [:]
        def newData = this.loadVersionJiraData(projectKey, versionName)

        // Get more info of the versions from Jira
        def predecessors = newData.precedingVersions ?: []
        def previousVersionId = null
        if (predecessors && ! predecessors.isEmpty()) {
            previousVersionId = predecessors.first()
        }

        if (previousVersionId) {
            logger.info("loadJiraData: Found a predecessor project version with ID '${previousVersionId}'. Loading its data.")
            def savedDataFromOldVersion = this.loadSavedJiraData(previousVersionId)
            def mergedData = this.mergeJiraData(savedDataFromOldVersion, newData)
            result << this.addKeyAndVersionToComponentsWithout(mergedData)
            result.previousVersion = previousVersionId
        } else {
            logger.info("loadJiraData: No predecessor project version found. Loading only data from Jira.")
            result << this.addKeyAndVersionToComponentsWithout(newData)
        }

        // Get more info of the versions from Jira
        result.project << [previousVersion: this.loadVersionDataFromJira(previousVersionId)]

        return result
    }

    protected Map loadJiraDataBugs(Map tests, String versionName = null) {
        if (!this.jiraUseCase) return [:]
        if (!this.jiraUseCase.jira) return [:]

        def fields = ['assignee', 'duedate', 'issuelinks', 'status', 'summary']
        def jql = "project = ${this.jiraProjectKey} AND issuetype = Bug AND status != Done"

        if (versionName) {
            fields << 'fixVersions'
            jql = jql + " AND fixVersion = '${versionName}'"
        }

        def jqlQuery = [
            fields: fields,
            jql: jql,
            expand: []
        ]

        def jiraBugs = this.jiraUseCase.jira.getIssuesForJQLQuery(jqlQuery) ?: []

        return jiraBugs.collectEntries { jiraBug ->
            def bug = [
                key: jiraBug.key,
                name: jiraBug.fields.summary,
                assignee: jiraBug.fields.assignee ? [jiraBug.fields.assignee.displayName, jiraBug.fields.assignee.name, jiraBug.fields.assignee.emailAddress].find { it != null } : "Unassigned",
                dueDate: '', // TODO: currently unsupported for not being enabled on a Bug issue
                status: jiraBug.fields.status.name,
                versions: jiraBug.fields.fixVersions.collect { it.name }
            ]

            def testKeys = []
            if (jiraBug.fields.issuelinks) {
                testKeys = jiraBug.fields.issuelinks.findAll {
                    it.type.name == 'Blocks' && it.outwardIssue &&
                            it.outwardIssue.fields.issuetype.name == 'Test'
                }.collect { it.outwardIssue.key }
            }

            // Add relations from bug to tests
            bug.tests = testKeys

            // Add relations from tests to bug
            testKeys.each { testKey ->
                if (!tests[testKey].bugs) {
                    tests[testKey].bugs = []
                }

                tests[testKey].bugs << bug.key
            }

            return [jiraBug.key, bug]
        }
    }

    protected Map loadCurrentVersionDataFromJira() {
        loadVersionDataFromJira(this.buildParams.version)
    }

    Map loadVersionDataFromJira(String versionName) {
        if (!this.jiraUseCase) return [:]
        if (!this.jiraUseCase.jira) return [:]

        return this.jiraUseCase.jira.getVersionsForProject(this.jiraProjectKey).find { version ->
            versionName == version.name
        }
    }

    protected Map loadJiraDataTrackingDocs(String versionName = null) {
        if (!this.jiraUseCase) return [:]
        if (!this.jiraUseCase.jira) return [:]

        def jql = "project = ${this.jiraProjectKey} AND issuetype = '${JiraUseCase.IssueTypes.DOCUMENTATION_TRACKING}'"

        if (versionName) {
            jql = jql + " AND fixVersion = '${versionName}'"
        }

        def jqlQuery = [
            jql: jql
        ]

        def jiraIssues = this.jiraUseCase.jira.getIssuesForJQLQuery(jqlQuery)
        if (jiraIssues.isEmpty()) {
            def message = "Error: Jira data does not include references to items of type '${JiraDataItem.TYPE_DOCTRACKING}'"
            if (versionName) {
                message += " for version '${versionName}'"
            }
            message += "."

            throw new IllegalArgumentException(message)
        }

        return jiraIssues.collectEntries { jiraIssue ->
            [
                jiraIssue.key,
                [
                    key: jiraIssue.key,
                    name: jiraIssue.fields.summary,
                    description: jiraIssue.fields.description,
                    status: jiraIssue.fields.status.name,
                    labels: jiraIssue.fields.labels,
                ],
            ]
        }
    }

    protected Map loadJiraDataIssueTypes() {
        if (!this.jiraUseCase) return [:]
        if (!this.jiraUseCase.jira) return [:]

        def jiraIssueTypes = this.jiraUseCase.jira.getIssueTypes(this.jiraProjectKey)
        return jiraIssueTypes.values.collectEntries { jiraIssueType ->
            [
                jiraIssueType.name,
                [
                    id: jiraIssueType.id,
                    name: jiraIssueType.name,
                    fields: this.jiraUseCase.jira.getIssueTypeMetadata(this.jiraProjectKey, jiraIssueType.id).values.collectEntries { value ->
                        [
                            value.name,
                            [
                                id:   value.fieldId,
                                name: value.name,
                            ]
                        ]
                    },
                ]
            ]
        }
    }

    protected Map loadMetadata(String filename = METADATA_FILE_NAME) {
        if (filename == null) {
            throw new IllegalArgumentException("Error: unable to parse project meta data. 'filename' is undefined.")
        }

        def file = Paths.get(this.steps.env.WORKSPACE, filename).toFile()
        if (!file.exists()) {
            throw new RuntimeException("Error: unable to load project meta data. File '${this.steps.env.WORKSPACE}/${filename}' does not exist.")
        }

        def result = new Yaml().load(file.text)

        // Check for existence of required attribute 'id'
        if (!result?.id?.trim()) {
            throw new IllegalArgumentException("Error: unable to parse project meta data. Required attribute 'id' is undefined.")
        }

        // Check for existence of required attribute 'name'
        if (!result?.name?.trim()) {
            throw new IllegalArgumentException("Error: unable to parse project meta data. Required attribute 'name' is undefined.")
        }

        if (result.description == null) {
            result.description = ''
        }

        if (result.repositories == null) {
            result.repositories = []
        }

        result.repositories.eachWithIndex { repo, index ->
            // Check for existence of required attribute 'repositories[i].id'
            if (!repo.id?.trim()) {
                throw new IllegalArgumentException(
                        "Error: unable to parse project meta data. Required attribute 'repositories[${index}].id' is undefined.")
            }

            repo.data = [
                openshift: [:],
                documents: [:],
            ]

            // Set repo type, if not provided
            if (!repo.type?.trim()) {
                repo.type = MROPipelineUtil.PipelineConfig.REPO_TYPE_ODS_CODE
            }

            // Resolve repo URL
            def gitURL = this.getGitURLFromPath(this.steps.env.WORKSPACE, 'origin')
            if (repo.name?.trim()) {
                repo.url = gitURL.resolve("${repo.name}.git").toString()
                repo.remove('name')
            } else {
                repo.url = gitURL.resolve("${result.id.toLowerCase()}-${repo.id}.git").toString()
            }

            this.logger.debug("Resolved Git URL for repo '${repo.id}' to '${repo.url}'")

            // Resolve repo branch, if not provided
            if (!repo.branch?.trim()) {
                this.logger.debug("Could not determine Git branch for repo '${repo.id}' " +
                        "from project meta data. Assuming 'master'.")
                repo.branch = 'master'
            }
            this.logger.debug("Set default (used for WIP) git branch for repo '${repo.id}' to ${repo.branch} ")
        }

        if (result.capabilities == null) {
            result.capabilities = []
        }

        def levaDocsCapabilities = result.capabilities.findAll { it instanceof Map && it.containsKey('LeVADocs') }
        if (levaDocsCapabilities) {
            if (levaDocsCapabilities.size() > 1) {
                throw new IllegalArgumentException(
                        "Error: unable to parse project metadata. More than one 'LeVADoc' capability has been defined.")
            }

            def levaDocsCapability = levaDocsCapabilities.first()

            def gampCategory = levaDocsCapability.LeVADocs?.GAMPCategory
            if (!gampCategory) {
                throw new IllegalArgumentException(
                        "Error: 'LeVADocs' capability has been defined but contains no 'GAMPCategory'.")
            }

            def templatesVersion = levaDocsCapability.LeVADocs?.templatesVersion
            if (!templatesVersion) {
                levaDocsCapability.LeVADocs.templatesVersion = DEFAULT_TEMPLATE_VERSION
            }
            levaDocsCapability.LeVADocs.templatesVersion = "${levaDocsCapability.LeVADocs.templatesVersion}"
        }

        if (result.environments == null) {
            result.environments = [:]
        }

        return result
    }

    void reportPipelineStatus(String message = '', boolean isError = false) {
        if (!this.jiraUseCase) {
            logger.warn("reportPipelineStatus: Could *NOT* update release status because jiraUseCase has invalid value.")
            return
        }
        this.jiraUseCase.updateJiraReleaseStatusResult(message, isError)
    }

    void addCommentInReleaseStatus(String message) {
        if (!this.jiraUseCase) {
            logger.warn("addCommentInReleaseStatus: Could *NOT* add comment because jiraUseCase has invalid value.")
            return
        }
        this.jiraUseCase.addCommentInReleaseStatus(message)
    }

    protected Map resolveJiraDataItemReferences(Map data) {
        this.resolveJiraDataItemReferences(data, JiraDataItem.TYPES)
    }

    @NonCPS
    protected Map resolveJiraDataItemReferences(Map data, List<String> jiraTypes) {
        Map result = [:]
        data.each { String type, values ->
            if (!jiraTypes.contains(type)) {
                return
            }

            result[type] = [:]
            values.each { String key, JiraDataItem item ->
                updateResultType(result, type, key, jiraTypes, item, data)
            }
        }

        return result
    }

    @NonCPS
    protected void updateResultType(Map result,
                                    String type,
                                    String key,
                                    List<String> jiraTypes,
                                    JiraDataItem item,
                                    Map data) {
        result[type][key] = [:]
        jiraTypes.each { referenceType ->
            if (item.containsKey(referenceType)) {
                updateResultTypeKey(item, referenceType, result, type, key, data)
            }
        }
    }

    @NonCPS
    protected void updateResultTypeKey(JiraDataItem item,
                                       String referenceType,
                                       Map result,
                                       String type,
                                       String key,
                                       Map data) {
        result[type][key][referenceType] = []
        item[referenceType].eachWithIndex { referenceKey, index ->
            def value = data[referenceType][referenceKey]
            if (value != null) {
                result[type][key][referenceType][index] = value
            }
        }
    }

    void setHasFailingTests(boolean status) {
        this.data.build.hasFailingTests = status
    }

    void setHasUnexecutedJiraTests(boolean status) {
        this.data.build.hasUnexecutedJiraTests = status
    }

    @NonCPS
    String toString() {
        // Don't serialize resolved Jira data items
        def result = this.data.subMap(['build', 'buildParams', 'metadata', 'git', 'jira'])

        if (!services?.jira && capabilities?.empty) {
            result.remove('jira')
        }

        return JsonOutput.prettyPrint(JsonOutput.toJson(result))
    }

    List<String> getMainReleaseManagerEnv() {
        def mroSharedLibVersion = this.steps.sh(
                script: "env | grep 'library.ods-mro-jenkins-shared-library.version' | cut -d= -f2",
                returnStdout: true,
                label: 'getting ODS shared lib version'
        ).trim()

        return [
                "ods.build.rm.${getKey()}.repo.url=${gitData.url}",
                "ods.build.rm.${getKey()}.repo.commit.sha=${gitData.commit}",
                "ods.build.rm.${getKey()}.repo.commit.msg=${gitData.message}",
                "ods.build.rm.${getKey()}.repo.commit.timestamp=${gitData.time}",
                "ods.build.rm.${getKey()}.repo.commit.author=${gitData.author}",
                "ods.build.rm.${getKey()}.repo.branch=${gitData.baseTag}",
                "ods.build.orchestration.lib.version=${mroSharedLibVersion}",
        ]
    }

    String getReleaseManagerBitbucketHostUrl() {
        return steps.env.BITBUCKET_URL ?: "https://${steps.env.BITBUCKET_HOST}"
    }

    String getTailorPrivateKeyCredentialsId() {
        def secretName = steps.env.TAILOR_PRIVATE_KEY_SECRET ?: 'tailor-private-key'
        "${getKey()}-cd-${secretName}"
    }

    String getHelmPrivateKeyCredentialsId() {
        def secretName = steps.env.HELM_PRIVATE_KEY_SECRET ?: 'helm-private-key'
        "${getKey()}-cd-${secretName}"
    }

    String getSourceRegistrySecretName() {
        'mro-image-pull'
    }

    boolean getForceGlobalRebuild() {
        return (this.data.metadata.allowPartialRebuild &&
                this.config.get(NexusService.NEXUS_REPO_EXISTS_KEY, false)) ? false : true
    }

    void addConfigSetting(def key, def value) {
        this.config.put(key, value)
    }

    protected Map loadSavedJiraData(String savedVersion) {
        new ProjectDataBitbucketRepository(steps).loadFile(savedVersion)
    }

    /**
     * Saves the project data to the
     * @return filenames saved
     */
    List<String> saveProjectData() {
        def bitbucketRepo = new ProjectDataBitbucketRepository(steps)
        def fileNames = []
        if (this.isAssembleMode) {
            fileNames.add(this.saveVersionData(bitbucketRepo))
        }
        fileNames.addAll(this.documentHistories.collect { docName, dh ->
            dh.saveDocHistoryData(bitbucketRepo)
        })
        return fileNames
    }

    /**
     * Saves the materialized jira data for this and old versions
     * @return File name created
     */
    String saveVersionData(ProjectDataBitbucketRepository repository) {
        def savedEntities = ['components',
                             'mitigations',
                             'requirements',
                             'risks',
                             'tests',
                             'techSpecs',
                             'epics',
                             'version',
                              'docs',
                             'precedingVersions',]
        def dataToSave = this.data.jira.findAll { savedEntities.contains(it.key) }
        logger.debug('Saving Jira data for the version ' + JsonOutput.toJson(this.getVersionName()))

        repository.save(dataToSave, this.getVersionName())
    }

    Map mergeJiraData(Map oldData, Map newData) {
        def mergeMaps = { Map left, Map right ->
            def keys = (left.keySet() + right.keySet()).toSet()
            keys.collectEntries { key ->
                if (JiraDataItem.TYPES.contains(key)) {
                    if (!left[key] || left[key].isEmpty()) {
                        [(key): right[key]]
                    } else if (!right[key] || right[key].isEmpty()) {
                        [(key): left[key]]
                    } else {
                        [(key): left[key] + right[key]]
                    }
                } else {
                    [(key): right[key]]
                }
            }
        }

        // Here we update the existing links in 3 ways:
        // - Deleting links of removing issues
        // - Adding links to new issues
        // - Updating links for changes in issues (changing key 1 for key 2)
        def updateIssues = { Map<String,Map> left, Map<String,Map> right ->
            def updateLink = { String issueType, String issueToUpdateKey, Map link ->
                if (! left[issueType][issueToUpdateKey][link.linkType]) {
                    left[issueType][issueToUpdateKey][link.linkType] = []
                }
                if (link.action == 'add') {
                    left[issueType][issueToUpdateKey][link.linkType] << link.origin
                } else if (link.action == 'discontinue') {
                    left[issueType][issueToUpdateKey][link.linkType].removeAll { it == link.origin }
                } else if (link.action == 'change') {
                    left[issueType][issueToUpdateKey][link.linkType] << link.origin
                    left[issueType][issueToUpdateKey][link.linkType].removeAll { it == link."replaces" }
                }
                // Remove potential duplicates in place
                left[issueType][issueToUpdateKey][link.linkType].unique(true)
            }

            def reverseLinkIndex = buildChangesInLinks(left, right)
            left.findAll { JiraDataItem.TYPES.contains(it.key) }.each { issueType, issues ->
                issues.values().each { Map issueToUpdate ->
                    def linksToUpdate = reverseLinkIndex[issueToUpdate.key] ?: []
                    linksToUpdate.each { Map link ->
                        try {
                            updateLink(issueType, issueToUpdate.key, link)
                        } catch (Exception e) {
                            throw new IllegalStateException("Error found when updating link ${link} for issue " +
                                "${issueToUpdate.key} from a previous version. Error message: ${e.message}", e)
                        }
                    }
                }
            }
            return left
        }

        def updateIssueLinks = { issue, index ->
            issue.collectEntries { String type, value ->
                if (JiraDataItem.TYPES.contains(type)) {
                    def newLinks = value.collect { link ->
                        def newLink = index[link]
                        newLink?:link
                    }.unique()
                    [(type): newLinks]
                } else {
                    [(type): value]
                }
            }
        }

        def updateLinks = { data, index ->
            data.collectEntries { issueType, content ->
                if (JiraDataItem.TYPES.contains(issueType)) {
                    def updatedIssues = content.collectEntries { String issueKey, Map issue ->
                        def updatedIssue = updateIssueLinks(issue, index)
                        [(issueKey): updatedIssue]
                    }
                    [(issueType): updatedIssues]
                } else {
                    [(issueType): content]
                }
            }
        }

        if (!oldData || oldData.isEmpty()) {
            newData
        } else {
            oldData[JiraDataItem.TYPE_COMPONENTS] = this.mergeComponentsLinks(oldData, newData)
            def discontinuations = (newData.discontinuedKeys ?: []) +
                this.getComponentDiscontinuations(oldData, newData)
            newData.discontinuations = discontinuations
            // Expand some information from old saved data
            def newDataExpanded = expandPredecessorInformation (oldData, newData, discontinuations)
            newDataExpanded << [discontinuationsPerType: discontinuationsPerType(oldData, discontinuations)]

            // Update data from previous version
            def oldDataWithUpdatedLinks = updateIssues(oldData, newDataExpanded)
            def successorIndex = getSuccessorIndex(newDataExpanded)
            def newDataExpandedWithUpdatedLinks = updateLinks(newDataExpanded, successorIndex)
            def obsoleteKeys = discontinuations + successorIndex.keySet()
            def oldDataWithoutObsoletes = removeObsoleteIssues(oldDataWithUpdatedLinks, obsoleteKeys)

            // merge old component data to new for the existing components
            newDataExpandedWithUpdatedLinks[JiraDataItem.TYPE_COMPONENTS] = newDataExpandedWithUpdatedLinks[JiraDataItem.TYPE_COMPONENTS]
                .collectEntries { compN, v ->
                    [ (compN): (oldDataWithoutObsoletes[JiraDataItem.TYPE_COMPONENTS][compN] ?: v)]
                }
            mergeMaps(oldDataWithoutObsoletes, newDataExpandedWithUpdatedLinks)
        }
    }

    /**
     * Return old components with the links coming from the new data. This is because we are not receiving all
     * the old links from the docgen reports for the components. and we need a special merge.
     * @param oldComponents components of the saved data
     * @param newComponents components for the new data
     * @return merged components with all the links
     */
    @NonCPS
    private Map mergeComponentsLinks(Map oldComponents, Map newComponents) {
        oldComponents[JiraDataItem.TYPE_COMPONENTS].collectEntries { compName, oldComp ->
            def newComp = newComponents[JiraDataItem.TYPE_COMPONENTS][compName] ?: [:]
            def updatedComp = mergeJiraItemLinks(oldComp, newComp)
            [(compName): updatedComp]
        }
    }

    @NonCPS
    private static mergeJiraItemLinks(Map oldItem, Map newItem, List discontinuations = []) {
        Map oldItemWithCurrentLinks = oldItem.collectEntries { key, value ->
            if (JiraDataItem.TYPES.contains(key)) {
                [(key): value - discontinuations]
            } else {
                [(key): value]
            }
        }.findAll { key, value ->
            !JiraDataItem.TYPES.contains(key) || value
        }
        (oldItemWithCurrentLinks.keySet() + newItem.keySet()).collectEntries { String type ->
            if (JiraDataItem.TYPES.contains(type)) {
                [(type): ((newItem[type] ?: []) + (oldItemWithCurrentLinks[type] ?: [])).unique()]
            } else {
                [(type): newItem[type] ?: oldItemWithCurrentLinks[type]]
            }
        }
    }

    @NonCPS
    private Map<String, List<String>> discontinuationsPerType (Map savedData, List<String> discontinuations) {
        savedData.findAll { JiraDataItem.TYPES.contains(it.key) }
            .collectEntries { String issueType, Map issues ->
                def discontinuationsPerType = issues.values().findAll { discontinuations.contains(it.key) }
                [(issueType): discontinuationsPerType]
            }
    }

    @NonCPS
    private List<String> getComponentDiscontinuations(Map oldData, Map newData) {
        def oldComponents = (oldData[JiraDataItem.TYPE_COMPONENTS] ?: [:]).keySet()
        def newComponents = (newData[JiraDataItem.TYPE_COMPONENTS] ?: [:]).keySet()
        (oldComponents - newComponents) as List
    }

    @NonCPS
    void clear() {
        this.data = null
    }

    @NonCPS
    private Map addKeyAndVersionToComponentsWithout(Map jiraData) {
        def currentVersion = jiraData.version
        (jiraData[JiraDataItem.TYPE_COMPONENTS] ?: [:]).each { k, component ->
            jiraData[JiraDataItem.TYPE_COMPONENTS][k].key = k
            if (! component.versions) {
                jiraData[JiraDataItem.TYPE_COMPONENTS][k].versions = [currentVersion]
            }
        }
        jiraData
    }

    @NonCPS
    private static List getDiscontinuedLinks(Map savedData, List<String> discontinuations) {
        savedData.findAll { JiraDataItem.TYPES.contains(it.key) }.collect {
            issueType, Map issues ->
            def discontinuedLinks = issues.findAll { discontinuations.contains(it.key) }
                .collect { key, issue ->
                    def issueLinks = issue.findAll { JiraDataItem.TYPES.contains(it.key) }
                    issueLinks.collect { String linkType, List linkedIssues ->
                        linkedIssues.collect { targetKey ->
                            [origin: issue.key, target: targetKey, linkType: issueType, action: 'discontinue']
                        }
                    }.flatten()
                }.flatten()
            return discontinuedLinks
        }.flatten()
    }

    @NonCPS
    private static Map<String, List> buildChangesInLinks(Map oldData, Map updates) {
        def discontinuedLinks = getDiscontinuedLinks(oldData, (updates.discontinuations ?: []))
        def additionsAndChanges = getAdditionsAndChangesInLinks(updates)

        return (discontinuedLinks + additionsAndChanges).groupBy { it.target }
    }

    @NonCPS
    private static List getAdditionsAndChangesInLinks(Map newData) {
        def getLink = { String issueType, Map issue, String targetKey, Boolean isAnUpdate ->
            if (isAnUpdate) {
                issue.predecessors.collect {
                    [origin: issue.key, target: targetKey, linkType: issueType, action: 'change', replaces: it]
                }
            } else {
                [origin: issue.key, target: targetKey, linkType: issueType, action: 'add']
            }
        }

        newData.findAll { JiraDataItem.TYPES.contains(it.key) }.collect { issueType, issues ->
            issues.collect { String issueKey, Map issue ->
                def isAnUpdate = ! (issue.predecessors ?: []).isEmpty()

                def issueLinks = issue.findAll { JiraDataItem.TYPES.contains(it.key) }
                issueLinks.collect { String linkType, List linkedIssues ->
                    linkedIssues.collect { getLink(issueType, issue, it, isAnUpdate) }.flatten()
                }
            }
        }.flatten()
    }

    @NonCPS
    private static Map removeObsoleteIssues(Map jiraData, List<String> keysToRemove) {
        def result = jiraData.collectEntries { issueType, content ->
            if (JiraDataItem.TYPES.contains(issueType)) {
                [(issueType): content.findAll { ! keysToRemove.contains(it.key) } ]
            } else {
                [(issueType): content]
            }
        }
        return result
    }

    /**
     * Expected format is:
     *   issueType.issue."expandedPredecessors" -> [key:"", version:""]
     *   Note that an issue can only have a single successor in a single given version.
     *   An issue can only have multiple successors if they belong to different succeeding versions.
     * @param jiraData map of jira data
     * @return a Map with the issue keys as values and their respective predecessor keys as keys
     */
    @NonCPS
    private static Map getSuccessorIndex(Map jiraData) {
        def index = [:]
        jiraData.findAll { JiraDataItem.TYPES.contains(it.key) }.values().each { issueGroup ->
            issueGroup.values().each { issue ->
                (issue.expandedPredecessors ?: []).each { index[it.key] = issue.key }
            }
        }
        return index
    }

    /**
     * Recover the information about "preceding" issues for all the new ones that are an update on previously
     * released ones. That way we can provide all the changes in the documents
     * @param savedData data from old versions retrieved by the pipeline
     * @param newData data for the current version
     * @return Map new data with the issue predecessors expanded
     */
    @NonCPS
    private static Map expandPredecessorInformation(Map savedData, Map newData, List discontinuations) {
        def expandPredecessor = { String issueType, String issueKey, String predecessor ->
            def predecessorIssue = (savedData[issueType] ?: [:])[predecessor]
            if (!predecessorIssue) {
                throw new RuntimeException("Error: new issue '${issueKey}' references key '${predecessor}' " +
                    "of type '${issueType}' that cannot be found in the saved data for version '${savedData.version}'." +
                    "Existing issue list is '[${(savedData[issueType] ?: [:]).keySet().join(', ')}]'")
            }
            def existingPredecessors = (predecessorIssue.expandedPredecessors ?: [:])
            def result = [[key: predecessorIssue.key, versions: predecessorIssue.versions]]

            if (existingPredecessors) {
                result << existingPredecessors
            }
            result.flatten()
        }

        newData.collectEntries { issueType, content ->
            if (JiraDataItem.TYPES.contains(issueType)) {
                def updatedIssues = content.collectEntries { String issueKey, Map issue ->
                    def predecessors = issue.predecessors ?: []
                    if (predecessors.isEmpty()) {
                        [(issueKey): issue]
                    } else {
                        def expandedPredecessors = predecessors.collect { predecessor ->
                            expandPredecessor(issueType, issueKey, predecessor)
                        }.flatten()
                        // Get old links from predecessor (just one allowed)
                        def predecessorIssue = savedData.get(issueType).get(predecessors.first())
                        def updatedIssue = mergeJiraItemLinks(predecessorIssue, issue, discontinuations)

                        [(issueKey): (updatedIssue + [expandedPredecessors: expandedPredecessors])]
                    }
                }
                [(issueType): updatedIssues]
            } else {
                [(issueType): content]
            }
        }
    }

    void addFakeRepository(String component) {
        def rmURL = git.getOriginUrl()
        def gitURL = this.getGitURLFromPath(this.steps.env.WORKSPACE, 'origin')
        def repo = [
            id: component,
            data: [
                openshift: [:],
                documents: [:],
            ],
            include: false,
            type: MROPipelineUtil.PipelineConfig.REPO_TYPE_ODS_CODE,
            url: gitURL.resolve("${this.key.toLowerCase()}-${component}.git").toString(),
            branch: 'master'
        ]

        if (rmURL == repo.url) {
            logger.debug("Not adding release manager")
        } else {
            this.data.metadata.repositories.add(repo)
        }
    }
}
