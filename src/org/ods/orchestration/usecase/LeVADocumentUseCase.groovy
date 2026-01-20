package org.ods.orchestration.usecase

import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVRecord

import com.cloudbees.groovy.cps.NonCPS
import org.ods.orchestration.service.CMDBService
import org.ods.orchestration.service.DocGenService
import org.ods.orchestration.service.LeVADocumentChaptersFileService
import org.ods.orchestration.util.MROPipelineUtil
import org.ods.orchestration.util.PDFUtil
import org.ods.orchestration.util.Project
import org.ods.services.JenkinsService
import org.ods.services.NexusService
import org.ods.services.OpenShiftService
import org.ods.util.ILogger
import org.ods.util.IPipelineSteps
import org.ods.orchestration.util.MROPipelineUtil.PipelineConfig

import java.math.RoundingMode
import java.nio.charset.StandardCharsets
import java.text.DecimalFormat
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.time.temporal.ChronoField
import java.util.regex.Pattern

@SuppressWarnings([
    'ClassSize',
    'UnnecessaryDefInMethodDeclaration',
    'UnnecessaryCollectCall',
    'IfStatementBraces',
    'LineLength',
    'AbcMetric',
    'Instanceof',
    'VariableName',
    'DuplicateListLiteral',
    'UnusedMethodParameter',
    'UnusedVariable',
    'ParameterCount',
    'ParameterReassignment',
    'UnnecessaryElseStatement',
    'NonFinalPublicField',
    'PropertyName',
    'MethodCount',
    'UseCollectMany',
    'ParameterName',
    'TrailingComma',
    'SpaceAroundMapEntryColon',
    'PublicMethodsBeforeNonPublicMethods'])
class LeVADocumentUseCase extends DocGenUseCase {

    protected static Map DOCUMENT_TYPE_NAMES = [
        (DocumentType.REQ as String)        : 'Requirements Specification incl. Risk Assessment Document',
        (DocumentType.DES as String)        : 'System and Software Description Document',
        (DocumentType.EVD as String)        : 'Qualification Evidence Document',
    ]

    static GAMP_CATEGORY_SENSITIVE_DOCS = [
    ]

    static Map<String, Map> DOCUMENT_TYPE_FILESTORAGE_EXCEPTIONS = [
        'SCRR-MD' : [storage: 'pdf', content: 'pdf' ]
    ]

    static Map<String, String> INTERNAL_TO_EXT_COMPONENT_TYPES = [
        (PipelineConfig.REPO_TYPE_ODS_SAAS_SERVICE as String) : 'SAAS Component',
        (PipelineConfig.REPO_TYPE_ODS_TEST         as String) : 'Automated tests',
        (PipelineConfig.REPO_TYPE_ODS_SERVICE      as String) : '3rd Party Service Component',
        (PipelineConfig.REPO_TYPE_ODS_CODE         as String) : 'ODS Software Component',
        (PipelineConfig.REPO_TYPE_ODS_INFRA        as String) : 'Infrastructure as Code Component',
        (PipelineConfig.REPO_TYPE_ODS_LIB          as String) : 'ODS library component'
    ]

    public static String DEVELOPER_PREVIEW_WATERMARK = 'Developer Preview'
    public static String WORK_IN_PROGRESS_WATERMARK = 'Work in Progress'

    private static final Pattern TEST_NAME_PATTERN = ~/(?i)^\s*+Requirement\s*+(?<requirement>\d++)\s*+_\s*+(?<name>.*?)\s*+$/

    private static final ZoneId UTC = ZoneId.of('UTC')
    private static final DateTimeFormatter DATE_TIME_FORMATTER = new DateTimeFormatterBuilder()
        .appendPattern(
            DateTimeFormatterBuilder.getLocalizedDateTimePattern(
                FormatStyle.MEDIUM,
                FormatStyle.LONG,
                IsoChronology.INSTANCE,
                Locale.GERMANY
            )
        ).toFormatter(Locale.UK) // English formatter using the German pattern.
    private static final DateTimeFormatter DATE_FORMATTER = new DateTimeFormatterBuilder()
        .appendPattern(
            DateTimeFormatterBuilder.getLocalizedDateTimePattern(
                FormatStyle.MEDIUM,
                null,
                IsoChronology.INSTANCE,
                Locale.GERMANY
            )
        ).toFormatter(Locale.UK) // English formatter using the German pattern.
    private static final JIRA_DATE_TIME_FORMATTER = new DateTimeFormatterBuilder()
        .parseCaseInsensitive()
        .append(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        .parseLenient()
        .appendFraction(ChronoField.MILLI_OF_SECOND, 3, 3, true)
        .appendOffset('+HHMM', '+0000')
        .parseStrict()
        .toFormatter(Locale.ENGLISH)

    // Using the deprecated build(), because get() doesn't work for unknown reasons. Version conflict?
    private static final CSVFormat SONAR_REPORT_CSV_FORMAT = CSVFormat.TDF.builder().setHeader().build()
    private static final NumberFormat SCORE_FORMAT = DecimalFormat.getInstance(Locale.UK)
    private static final NumberFormat SCORE_FORMAT_FOR_SORTING = DecimalFormat.getInstance(Locale.UK)
    static {
        SCORE_FORMAT.setRoundingMode(RoundingMode.HALF_UP)
        SCORE_FORMAT.setMaximumIntegerDigits(3)
        SCORE_FORMAT.setMinimumIntegerDigits(1)
        SCORE_FORMAT.setMinimumFractionDigits(0)
        SCORE_FORMAT.setMaximumFractionDigits(1)
        SCORE_FORMAT_FOR_SORTING.setRoundingMode(RoundingMode.HALF_UP)
        SCORE_FORMAT_FOR_SORTING.setMaximumIntegerDigits(3)
        SCORE_FORMAT_FOR_SORTING.setMinimumIntegerDigits(3)
        SCORE_FORMAT_FOR_SORTING.setMinimumFractionDigits(7)
        SCORE_FORMAT_FOR_SORTING.setMaximumFractionDigits(7)
    }

    private final JiraUseCase jiraUseCase
    private final JUnitTestReportsUseCase junit
    private final LeVADocumentChaptersFileService levaFiles
    private final OpenShiftService os
    private final SonarQubeUseCase sq
    private final BitbucketTraceabilityUseCase bbt
    private final ILogger logger
    private final CMDBUseCase cmdb
    private final ConfluenceUseCase confluence

    LeVADocumentUseCase(Project project, IPipelineSteps steps, MROPipelineUtil util, DocGenService docGen,
                        JenkinsService jenkins, JiraUseCase jiraUseCase, JUnitTestReportsUseCase junit,
                        LeVADocumentChaptersFileService levaFiles, NexusService nexus, OpenShiftService os,
                        PDFUtil pdf, SonarQubeUseCase sq, BitbucketTraceabilityUseCase bbt,
                        ConfluenceUseCase confluenceUseCase, ILogger logger) {
        super(project, steps, util, docGen, nexus, pdf, jenkins)
        this.jiraUseCase = jiraUseCase
        this.junit = junit
        this.levaFiles = levaFiles
        this.os = os
        this.sq = sq
        this.bbt = bbt
        this.logger = logger
        confluence = confluenceUseCase
        cmdb = new CMDBUseCase(new CMDBService(logger), logger)
    }

    /*
    @NonCPS
    private def getReqsWithNoGampTopic(def requirements) {
        return requirements.findAll { it.gampTopic == null }
    }

    @NonCPS
    private def getReqsGroupedByGampTopic(def requirements) {
        return requirements.findAll { it.gampTopic != null }
            .groupBy { it.gampTopic.toLowerCase() }
    }
    */

    void createREQ(Map repo = null, Map data = null) {
        def documentType = DocumentType.REQ as String

        def watermarkText = this.getWatermarkText(documentType)
        def changeLog = generateChangeLog()
        def requirements = getRequirementsWithChanges(changeLog)
        def filteredChangeLog = changeLog.collect { sortingKey, logEntry ->
            // Remove the description, which isn't needed for the actual change log.
            logEntry.collectEntries { key, value ->
                key == 'description' ? [:] : [(key): value]
            }
        }

        def metadata = this.getDocumentMetadata(DOCUMENT_TYPE_NAMES[documentType])
        metadata.orientation = "Landscape"

        def data_ = [
            metadata: metadata,
            data    : [
                requirements: requirements,
                changeLog: filteredChangeLog,
                changeHistory: this.getChangeHistory(),
            ]
        ]

        this.createDocument(DocumentType.REQ, null, data_, [:], null, getDocumentTemplateName(documentType), watermarkText)
    }

    @NonCPS
    private getChangeHistory() {
        def changeHistory = []
        def versions = project?.data?.jira?.project?.versions

        versions.each { version ->
            def release = project.getVersionData(version)
            changeHistory << [
                changeNumber: version,
                userStartDate: release.userStartDate,
                changeReason: release.description
            ]
        }

        logger.debug("Change History: ${changeHistory}")
        return changeHistory
    }

    @NonCPS
    private SortedMap<String, Map> generateChangeLog() {
        def changeLog = newReversedMap() as SortedMap
        def requirementIndex = this.project.requirementsByURL
        logger.debug "LeVADocUseCase::generateChangeLog: requirementIndex: ${requirementIndex}"
        project.issues.each { key, issue ->
            def links = issue.remoteLinks as List<Map>
            def fields = issue.fields as Map<String, Object>
            def timestamp = fields.resolutiondate as String
            logger.debug "LeVADocUseCase::generateChangeLog: $key, $timestamp"
            def dateTime = timestamp ? JIRA_DATE_TIME_FORMATTER.parse(timestamp) : null
            def date = dateTime ? DATE_FORMATTER.format(dateTime) : ""
            def logEntry = [
                timestamp: timestamp,
                date: date,
                issueKey: issue.key,
                fixVersion: fields.fixVersions[0].name,
                summary: fields.summary,
                description: fields.description,
            ]
            def reqNumPadded = ''
            def issueReqs = links?.collect { link -> requirementIndex[link.url] }?.findAll()
                ?.unique { it.metadata.requirementNumber } //TODO Should we check for the latest version?
            if (issueReqs) {
                if (issueReqs.size() == 1) {
                    def req = issueReqs[0].metadata
                    logEntry.requirement = [
                        number: req.requirementNumber,
                        name: req.pageTitle
                    ]
                } else {
                    issueReqs.sort { it.metadata.requirementNumber as Integer }
                    logEntry.requirements = issueReqs*.metadata.collect { req ->
                        [
                            number: req.requirementNumber,
                            name: req.pageTitle
                        ]
                    }
                }
                def reqNum = issueReqs.last().metadata.requirementNumber as Integer
                reqNumPadded = String.format('%010d', reqNum)
            }
            def sortingKey = (timestamp ?: '') + reqNumPadded + issue.key
            changeLog[sortingKey] = logEntry
        }
        return changeLog
    }

    @NonCPS
    private List<Map> getRequirementsWithChanges(SortedMap<String, Map> changeLog) {
        def changesByReq = getChangesByRequirement(changeLog)
        def requirements = project.requirementsByNumber.collect { number, req ->
            def metadata = req.metadata
            def properties = req.properties
            def content = req.content
            if (content) {
                content = confluence.embedImages(content, metadata.url)
            }
            def reqInfo = [
                number: number,
                latestId: metadata.latestPageId,
                version: metadata.pageVersion,
                id: metadata.pageId,
                name: metadata.pageTitle,
                requirementType: properties.requirementType,
                implementationType: properties.implementationType,
                procedure: properties.procedure,
                content: content,
            ]
            def changes = changesByReq[number].collect { change ->
                [
                    key: change.issueKey,
                    summary: change.summary,
                    description: change.description,
                ]
            }
            if (changes) {
                reqInfo << [changes: changes]
            }
            return reqInfo
        }
        return requirements
    }

    @NonCPS
    private Map<Integer, List> getChangesByRequirement(SortedMap<String, Map> changeLog) {
        def changesByReq = [:] as Map<Integer, List>
        def processReq = { req, logEntry ->
            def number = req.number as Integer
            def changesForReq = changesByReq[number]
            if (changesForReq == null) {
                changesForReq = []
                changesByReq[number] = changesForReq
            }
            changesForReq << logEntry
        }
        changeLog.values().findAll { it.fixVersion == project.buildParams.changeId }.each { logEntry ->
            if (logEntry.requirement) {
                processReq(logEntry.requirement, logEntry)
            } else {
                logEntry.requirements?.each { req ->
                    processReq(req, logEntry)
                }
            }
        }
        return changesByReq
    }

    String createDES(Map repo = null, Map data = null) {
        def documentType = DocumentType.DES as String

        def watermarkText = this.getWatermarkText(documentType)

        def metadata = this.getDocumentMetadata(DOCUMENT_TYPE_NAMES[documentType])
        def components = this.bbt.getODSComponentMetadata(project.getGitReleaseBranch())
        def dependencies = this.bbt.getODSComponentDependencies(project.getGitReleaseBranch())

        def parentCi = cmdb.loadData(this.project.buildParams.configItem)

        // data.cmdbDiagramPngImage -> CMDB parent CI attachemnt
        
        cmdb.defaultNodeSanitizerStrategy(parentCi)
        def modules = cmdb.findModules(parentCi)
        def interfaces = cmdb.findInterfaces(parentCi)
        def devEnvironment = cmdb.findEnvironments(parentCi).find { env ->
            return cmdb.isDevelopmentEnvironment(env)
        }

        def parentCiAll = combineParentWithChildren(parentCi, [devEnvironment] + interfaces + modules)
        def parentCiModules = combineParentWithChildren(parentCi, modules)
        def parentCiRelations = combineParentWithChildren(parentCi, [devEnvironment] + interfaces)

        // compute Mermaid diagram for parent Ci modules
        def parentCiModulesPngImage = generateMermaidDiagram(parentCiModules)

        // compute Mermaid diagram for parent Ci relations
        def parentCiRelationsPngImage = generateMermaidDiagram(parentCiRelations)

        // compute Mermaid diagram for all relevant entities
        def fullDiagramPngImage = generateMermaidDiagram(parentCiAll)

        // Mermaid for components
        def componentsDiagramPngImage = generateComponentDiagram(components, dependencies)

        def environment = getTargetEnvironment()

        def data_ = [
            metadata: metadata,
            environment: environment,
            data : [
                components: components,
                parentCi: parentCi,
                parentCiModules: modules,
                parentCiRelations: cmdb.toFlatData(parentCiRelations),
                parentCiModulesPngImage: parentCiModulesPngImage,
                parentCiRelationsPngImage: parentCiRelationsPngImage,
                //fullDiagramPngImage: fullDiagramPngImage,
                componentsDiagramPngImage: componentsDiagramPngImage,
                changeHistory: this.getChangeHistory(),
                references: getDocReferences(),
            ]
        ]

        def tmpDir = steps.pwd(tmp: true)
        def attachedDiagram = [
            filename: 'system-diagram.png',
            contentType: 'image/png',
            compress: false,
            content: fullDiagramPngImage.decodeBase64(),
        ]
        def modifier = { byte[] document ->
            return PDFUtil.addAttachments(document, [ attachedDiagram ], tmpDir)
        }

        def uri = this.createDocument(DocumentType.DES, null, data_, [:], modifier, getDocumentTemplateName(documentType), watermarkText)

        return uri
    }

    private String generateComponentDiagram(Map<String, Map> repos, Map dependenciesMap) {
        def mermaid = new StringBuilder('graph TD\n')
        repos.each { name, repo ->
            mermaid << "${name}(${name})\n"
        }
        repos.each { name, repo ->
            def dependencies = dependenciesMap[name]
            dependencies?.each { dependency ->
                def target = "${project.key}-${dependency}"
                mermaid << "${name} -->|depends on| ${target}\n"
            }
        }
        return renderMermaidDiagram(mermaid.toString())
    }

    private Map combineParentWithChildren(Map parent, List children) {
        def result = parent.clone() as Map
        result.children = children.clone()
        return result
    }

    private String generateMermaidDiagram(Map data) {
        return renderMermaidDiagram(cmdb.toMermaidGraphCode(data))
    }

    private String renderMermaidDiagram(String mermaidCode) {
        def fileMermaidCode = ".${UUID.randomUUID().toString()}.mmd"
        def fileDiagram = "mermaid-diagram.png"

        steps.writeFile(
            file: fileMermaidCode,
            text: mermaidCode
        )

        def puppeteerConfig = ".${UUID.randomUUID().toString()}-puppeteer-config.json"
        steps.writeFile(
            file: puppeteerConfig,
            text: '''
                    {
                      "args": ["--no-sandbox"]
                    }
                '''
        )

     	def mermaidRenderConfig = ".${UUID.randomUUID().toString()}-mermaidRenderConfig.json"
        steps.writeFile(
            file: mermaidRenderConfig,
            text: '''
                    {
                      "maxTextSize": 99999999,
                      "maxEdges": 2000
                    }
                '''
        )

        def status = steps.sh(
            script: """
                    npx @mermaid-js/mermaid-cli -i ${fileMermaidCode} -o ${fileDiagram} --puppeteerConfigFile ${puppeteerConfig} --configFile ${mermaidRenderConfig}
                """,
            returnStatus: true,
            label: "Render System Relations Diagram using Mermaid"
        )

        if (status != 0) {
            logger.error("Unable to remder System Relations Diagram using Mermaid")
            throw new RuntimeException()
        }

        def fileDiagramContents = steps.readFile(file: fileDiagram, encoding: 'Base64')

        steps.sh """
                rm ${fileMermaidCode} ${fileDiagram} ${puppeteerConfig} ${mermaidRenderConfig}
            """

        return fileDiagramContents?.strip()
    }

    String createEVD(Map repo = null, Map data = null) {
        def documentType = DocumentType.EVD as String

        def environment = getTargetEnvironment()
        def executedComponents = getComponentExecutionResults()
        def testComponents = getExecutedTestComponents()
        def tests = getTestResults(data)
        def xunit = getXUnitFiles(data)
        def testEvidence = getTestEvidences(data)

        def pullRequests = null
        def sonarReports = null
        def aquaReports = null
        if (project.isAssembleMode) {
            pullRequests = bbt.getPullRequestInfo(executedComponents)
            sonarReports = getSonarReports()
            aquaReports = getAquaReports()
        }
        def rmLog = jenkins.currentBuildLogAsText
        def attachments = getReportsAsAttachments(rmLog, executedComponents)
        attachments += xunit
        attachments += testEvidence
        executedComponents.each { component ->
            component.remove('logText')
        }

        def metadata = getDocumentMetadata(DOCUMENT_TYPE_NAMES[documentType])
        metadata.orientation = 'Landscape'
        def data_ = [
            metadata: metadata,
            environment: environment,
            env: project.buildParams.targetEnvironment.toUpperCase(Locale.ENGLISH),
            assembled: project.isAssembleMode,
            data: [
                components: executedComponents,
                testcomponents: testComponents,
                tests: tests,
                sonar: sonarReports,
                aqua: aquaReports,
                repos: pullRequests,
                changeHistory: this.getChangeHistory(),
                references: getDocReferences(),
            ]
        ]
        def watermarkText = this.getWatermarkText(documentType)

        def tmpDir = steps.pwd(tmp: true)
        def modifier = { byte[] document ->
            return PDFUtil.addAttachments(document, attachments, tmpDir)
        }

        def uri = this.createDocument(DocumentType.EVD, repo, data_, [:], modifier, getDocumentTemplateName(documentType), watermarkText)

        return uri
    }

    private List<Map> getReportsAsAttachments(String rmLog, List<Map> components) {
        def attachments = []
        attachments << [
            filename: 'release-manager.log',
            content: rmLog.getBytes(StandardCharsets.UTF_8),
            contentType: 'text/plain',
            compress: true,
        ]
        components.each { component ->
            def text = component.logText as String
            if (text) {
                attachments << [
                    filename: "${component.name}.log",
                    content : text.getBytes(StandardCharsets.UTF_8),
                    contentType: 'text/plain',
                    compress: true,
                ]
            }
        }
        [sonar: project.sonarStashes, aqua: project.aquaStashes].each { type, stashes ->
            stashes.each { component, stash ->
                steps.unstash(stash)

                def filename = type == 'sonar'
                    ? "SonarQube-${component}.docx"
                    : "AquaSec-${component}.html"

                def filePath = type == 'sonar'
                    ? "${steps.env.WORKSPACE}/artifacts/SCRR-${project.key}-${component}.docx"
                    : "${steps.env.WORKSPACE}/artifacts/SCSR-${project.key}-${component}-aqua-report-${component}.html"
                def contentType = type == 'sonar'
                    ? 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'
                    : 'text/html'
                def compress = true

                attachments << [
                    filename: filename,
                    content : new File(filePath).bytes,
                    contentType: contentType,
                    compress: compress,
                ]
            }
        }

        return attachments
    }

    @NonCPS
    private String getTargetEnvironment() {
        switch(project.targetEnvironmentToken) {
            case 'D':
                return 'Development'
            case 'Q':
                return 'Quality Assurance'
            case 'P':
                return 'Production'
        }
        return null
    }

    @NonCPS
    private List<Map> getComponentExecutionResults() {
        def logs = project.componentLogs
        return project.repositories.findAll { repo -> repo.doInstall && repo.include }.collect { repo ->
            if (!project.developerPreviewMode && repo.data.failedStage) {
                throw new RuntimeException("Component ${repo.name} failed")
            }
            def name = repo.name ?: "${project.key.toLowerCase(Locale.ENGLISH)}-${repo.id}"
            def component = [
                id: repo.id,
                name: name,
                installed: repo.include,
                git: repo.data?.git,
            ]
            if (repo.failedStage) {
                component.failedStage = repo.failedStage
            }
            def log = logs[repo.id as String]
            if (log) {
                component.logText = log
            }
            return component
        }
    }

    private List<Map> getExecutedTestComponents() {
        return project.repositories.findAll { repo ->
            repo.include &&
                (repo.type?.toLowerCase() == MROPipelineUtil.PipelineConfig.REPO_TYPE_ODS_TEST ||
                 (repo.installable == true && repo.data?.tests))
            }.collect { repo ->
            def name = repo.name ?: "${project.key.toLowerCase(Locale.ENGLISH)}-${repo.id}"
            def component = [
                id: repo.id,
                name: name,
                git: repo.data?.git,
                metadata: repo.data?.metadata,
            ]
            return component
        }
    }

    @NonCPS
    private List<Map> getTestResults(Map data) {
        def integrationTestResults = extractTestResults(data.tests?.integration, 'Integration')
        def acceptanceTestResults = extractTestResults(data.tests?.acceptance, 'Acceptance')
        def installationTestResults = extractTestResults(data.tests?.installation, 'Installation')
        def unitTestResults = extractTestResults(data.tests?.unit, 'Unit')
        def testResults = unitTestResults + installationTestResults + integrationTestResults + acceptanceTestResults
        Set<Integer> testedRequirements = [] as Set
        def tests = testResults.findAll { test -> test.result != 'Skipped' }.collect { test ->
            if (!project.developerPreviewMode) {
                if (test.result != 'Success') {
                    def msg = "Test ${test.name} of type ${test.type} failed with result ${test.result}."
                    throw new RuntimeException(msg)
                }
                testedRequirements << test.reqId
            }
            return test
        }
        if (!project.developerPreviewMode) {
            def untested = project.requirementsByNumber.keySet() - testedRequirements
            if (untested) {
                def msg = "The following requirements were not tested: ${untested.sort()}"
                throw new RuntimeException(msg)
            }
        }
        return tests
    }

    @NonCPS
    private List<Map> getXUnitFiles(Map data) {
        def result = []
        data.tests?.values()?.each { Map test ->
            test.testReportFiles?.each { File file ->
                result << [
                    filename   : file.name,
                    contentType: 'application/xml',
                    compress   : true,
                    content    : file.bytes,
                ]
            }
        }
        return result
    }

    private List<Map> getTestEvidences(Map data) {
        return data.evidences.collect { component, file ->
            [
                filename: "test-evidence-${component}.pdf",
                contentType: 'application/pdf',
                compress: true,
                content: steps.readFile(file: file, encoding: 'Base64')?.strip()?.decodeBase64(),
            ]
        }
    }

    private Map<String, String> getSonarReports() {
        def stashes = project.sonarStashes.entrySet() as ArrayList<Map.Entry>
        def reports = [:]
        for (Map.Entry<String, String> entry: stashes) {
            def component = entry.key
            def stash = entry.value
            steps.unstash(stash)
            def records = steps.readCSV(file: "artifacts/SCRR-${project.key}-${component}.csv",
                                        format: SONAR_REPORT_CSV_FORMAT) as List<CSVRecord>
            def findings = readRecords(records)
            reports[component] = findings
        }
        return reports as Map
    }

    @NonCPS
    private static List<Map> readRecords(List<CSVRecord> records) {
        return records.collect { csvRec -> readRecord(csvRec) }.findAll {csvRec ->
            def severity = csvRec.severity.toUpperCase(Locale.UK)
            return severity in ['BLOCKER', 'CRITICAL', 'MAJOR', 'HIGH', 'MEDIUM' ]
        }.unique().sort { csvRec -> csvRec.severity + csvRec.type + csvRec.rule }
    }

    @NonCPS
    private static Map<String, String> readRecord(CSVRecord csvRecord) {
        if (csvRecord.get('status') != 'OPEN') {
            return [:]
        }
        def type = csvRecord.get('type')
        if (type) {
            switch (type) {
                case 'CODE_SMELL':
                    type = 'Code Smell'
                    break;
                case 'VULNERABILITY':
                    type = 'Vulnerability'
                    break;
                case 'BUG':
                    type = 'Bug'
                    break;
                case 'SECURITY_HOTSPOT':
                    type = 'Security Hotspot'
                    break;
            }
        }
        def severity = csvRecord.get('severity')
        if (severity) {
            switch (severity) {
                case 'HIGH':
                    severity = 'High'
                    break;
                case 'BLOCKER':
                    severity = 'Blocker'
                    break;
                case 'CRITICAL':
                    severity = 'Critical'
                    break;
                case 'MEDIUM':
                    severity = 'Medium'
                    break;
                case 'MAJOR':
                    severity = 'Major'
                    break;
                case 'LOW':
                    severity = 'Low'
                    break;
                case 'MINOR':
                    severity = 'Minor'
                    break;
                case 'INFO':
                    severity = 'Info'
                    break;
            }
        }
        def file = csvRecord.get('component')
        if (file) {
            int from = file.indexOf(':')
            if (from >= 0 && ++from < file.length()) {
                file = file.substring(from)
            }
        }
        return [
            rule: csvRecord.get('rule'),
            type: type,
            severity: severity,
            file: file,
            message: csvRecord.get('message'),
        ]
    }

    @NonCPS
    private Map<String, Map> getAquaReports() {
        return project.aquaReports.collectEntries { component, report ->
            [(component): getAquaReport(report)]
        } as Map
    }

    @NonCPS
    private Map<String, Map> getAquaReport(Map<String, Object> report) {
        def vulnerabilities = []
        report.resources?.findAll { it.scanned }?.each { Map resource ->
            def info = resource.resource as Map
            def name = info.name
            def format = info.format
            def version = info.version
            resource.vulnerabilities.each { Map vul ->
                def severity = vul.aqua_severity as String
                if (severity in ['critical', 'high']) {
                    def score = vul.aqua_score ?: ''
                    if (score instanceof Number) {
                        score = SCORE_FORMAT.format(score)
                    }
                    def numericScore = score instanceof Number ? score : score ? Double.valueOf(score.toString()) : 0.0
                    def sortScore = SCORE_FORMAT_FOR_SORTING.format(100.0 - numericScore)
                    vulnerabilities << [
                        id: vul.name,
                        description: vul.description,
                        severity: severity,
                        score: score.toString(),
                        resource: name,
                        resourceFormat: format,
                        resourceVersion: version,
                        sortingKey: severity + sortScore + vul.name,
                    ]
                }
            }
        }
        vulnerabilities.sort { it.sortingKey }
        def summary = report.vulnerability_summary as Map
        def score = summary.score_average
        if (score instanceof Number) {
            score = SCORE_FORMAT.format(score)
        }
        return [
            summary: [
                total: summary.total,
                critical: summary.critical,
                high: summary.high,
                medium: summary.medium,
                low: summary.low,
                negligible: summary.negligible,
                score: score.toString(),
            ],
            vulnerabilities: vulnerabilities,
        ]  as Map
    }

    @NonCPS
    private List<Map<String, Object>> extractTestResults(Map<String, Object> tests, String type) {
        def suites = tests?.testResults?.testsuites
        if (!suites) {
            return []
        }
        def testCases = []
        suites.each { suite ->
            def component = suite.component
            suite.testcases?.each { testCase ->
                def requirement = null
                def reqId = null
                def name = testCase.name?.strip() as String
                if (name) {
                    def matcher = TEST_NAME_PATTERN.matcher(name)
                    if (matcher.matches()) {
                        name = matcher.group('name')
                        def n = matcher.group('requirement')
                        if (n) {
                            reqId = Integer.valueOf(n)
                            requirement = project.requirementsByNumber[reqId]
                        }
                    }
                }
                def timestamp = testCase.timestamp ?
                    LocalDateTime.parse(testCase.timestamp).toInstant(ZoneOffset.UTC).toEpochMilli()
                    : null
                testCases << [
                    name: name,
                    timestamp: timestamp,
                    type: type,
                    component: component,
                    reqId: reqId,
                    requirementName: requirement?.metadata?.pageTitle,
                    result: testCase.failure ? 'Failure' : testCase.error ? 'Error' :
                        testCase.skipped ? 'Skipped' : 'Success',
                ]
            }
        }
        return testCases
            .sort { t, u -> t.reqId <=> u.reqId ?: t.timestamp <=> u.timestamp ?: t.name <=> u.name } as List<Map>
   }

    @NonCPS
    private Map<String, Map> getDocReferences() {
        return project.docIds.collectEntries { type, id ->
            [(type.toString().toLowerCase(Locale.ENGLISH)): [
                id: id,
                link: "https://bisbx-vault-quality-development-demands.veevavault.com/ui/#doc_info/${id}",
            ]]
        }
    }

    @NonCPS
    private static <K, V> SortedMap<K, V> newReversedMap() {
        def comparator = Collections.reverseOrder()
        return new TreeMap<>(comparator)
    }

//    protected Map sortByEpicAndRequirementKeys(List updatedReqs) {
//        def sortedUpdatedReqs = SortUtil.sortIssuesByKey(updatedReqs)
//        def reqsGroupByEpic = sortedUpdatedReqs.findAll {
//            it.epic != null }.groupBy { it.epic }.sort()
//
//        def reqsGroupByEpicUpdated = reqsGroupByEpic.values().indexed(1).collect { index, epicStories ->
//            def aStory = epicStories.first()
//            [
//                epicName        : aStory.epicName,
//                epicTitle       : aStory.epicTitle,
//                epicDescription : this.convertImages(aStory.epicDescription ?: ''),
//                key             : aStory.epic,
//                epicIndex       : index,
//                stories         : epicStories,
//            ]
//        }
//        def output = [
//            noepics: sortedUpdatedReqs.findAll { it.epic == null },
//            epics  : reqsGroupByEpicUpdated
//        ]
//
//        return output
//    }

    /*
    @NonCPS
    private def computeKeysInDocForCSD(def data) {
        return data.collect { it.subMap(['key', 'epics']).values() }
            .flatten().unique()
    }

    @NonCPS
    private def computeKeysInDocForDTP(def data, def tests) {
        return data.collect { 'Technology-' + it.id } + tests
            .collect { [it.testKey, it.systemRequirement.split(', '), it.softwareDesignSpec.split(', ')] }
            .flatten()
    }

    @NonCPS
    private def computeKeysInDocForDTR(def data) {
        return data.collect {
            [it.key, it.systemRequirement.split(', '), it.softwareDesignSpec.split(', ')]
        }.flatten()
    }

    String createOverallDTR(Map repo = null, Map data = null) {
        def documentTypeName = DOCUMENT_TYPE_NAMES[DocumentType.EVD as String]
        def metadata = this.getDocumentMetadata(documentTypeName)
        def documentType = DocumentType.DTR as String

        def watermarkText = this.getWatermarkText(documentType, this.project.hasWipJiraIssues())

        def uri = this.createOverallDocument('Overall-Cover', documentType, metadata, null, watermarkText)
        def docVersion = this.project.getDocumentVersionFromHistories(documentType) as String
        this.updateJiraDocumentationTrackingIssue(documentType, uri, docVersion)
        return uri
    }
    */

    /*
    @NonCPS
    private def computeKeysInDocForCFTR(def data) {
        return data.collect { it.subMap(['key']).values() }.flatten()
    }


    //TODO Use this method to generate the test description everywhere
    def getTestDescription(testIssue) {
        return testIssue.description ?: testIssue.name ?: 'N/A'
    }

    @NonCPS
    private def computeKeysInDocForRA(def data) {
        return data
            .collect { it.subMap(['key', 'requirements', 'techSpecs', 'mitigations', 'tests']).values() }
            .flatten()
    }
    */


    /**
     * If the risk is associated to technical specification task which is itself associated with a User story,
     * return the technical specification task
     * otherwise, return the system requirements
     * @param risk
     * @return
     */
    /*
    private Project.JiraDataItem getRequirement(Project.JiraDataItem risk) {
        List<Project.JiraDataItem> requirements = risk.getResolvedTechnicalSpecifications()
        if (!requirements) {
            requirements = risk.getResolvedSystemRequirements()
        }
        return requirements.get(0)
    }

    private void fillRASections(def sections, def risks, def proposedMeasuresDesription) {
        if (!sections."sec4s2s1") sections."sec4s2s1" = [:]
        sections."sec4s2s1".nonGxpEvaluation = this.project.getProjectProperties()."PROJECT.NON-GXP_EVALUATION" ?: 'n/a'

        if (!sections."sec4s2s2") sections."sec4s2s2" = [:]

        if (Boolean.valueOf(this.project.getProjectProperties()."PROJECT.USES_POO")) {
            sections."sec4s2s2" = [
                usesPoo          : "true",
                lowDescription   : this.project.getProjectProperties()."PROJECT.POO_CAT.LOW",
                mediumDescription: this.project.getProjectProperties()."PROJECT.POO_CAT.MEDIUM",
                highDescription  : this.project.getProjectProperties()."PROJECT.POO_CAT.HIGH"
            ]
        }

        if (!sections."sec5") sections."sec5" = [:]
        sections."sec5".risks = SortUtil.sortIssuesByProperties(risks, ["requirementsKey", "key"])
        sections."sec5".proposedMeasures = SortUtil.sortIssuesByKey(proposedMeasuresDesription)
    }

    @NonCPS
    private def computeKeysInDocForIPV(def data) {
        return data
            .collect { it.subMap(['key', 'components', 'techSpecs']).values()  }
            .flatten()
    }

    @NonCPS
    private def computeKeysInDocForIVR(def data) {
        return data
            .collect { it.subMap(['key', 'components', 'techSpecs']).values()  }
            .flatten()
    }



    @NonCPS
    def sortTestSteps(def testSteps) {
        return testSteps?.sort(false) { it.orderId }
    }

    @NonCPS
    private def computeKeysInDocForSSDS(def techSpecs, def componentsMetadata, def modules) {
        def specs = techSpecs.collect { it.subMap(['key', 'requirements']).values() }.flatten()
        def components = componentsMetadata.collect { it.key }
        def mods = modules.collect { it.subMap(['requirementKeys', 'softwareDesignSpecKeys']).values() }.flatten()
        return specs + components + mods
    }



    @NonCPS
    private def computeKeysInDocForTIP(def data) {
        return data.collect { it.key }
    }
    */


    /*
     * Retrieves the deployment mean and fills empty values with proper defaults
     */
    /*
    protected static Map<String, Object> prepareDeploymentMeanInfo(Map<String, Map<String, Object>> deployments, String targetEnvironment) {
        Map<String, Object> deploymentMean =
            deployments?.find { it.key.endsWith('-deploymentMean') }?.value

        if (!deploymentMean) {
            return [:]
        }

        if (deploymentMean.type == 'tailor') {
            return formatTIRTailorDeploymentMean(deploymentMean)
        }

        return formatTIRHelmDeploymentMean(deploymentMean, targetEnvironment)
    }
    */

    /**
     * Retrieves all deployments.
     *
     * The processed map is suited to format the resource information in the TIR.
     * This method doesn't return deployment means.
     *
     * @return A Map with all the deployments.
     */
    /*
    protected static Map<String, Map<String, Object>> prepareDeploymentInfo(Map<String, Map<String, Object>> deployments) {
        return deployments
            ?.findAll { ! it.key.endsWith('-deploymentMean') }
            ?.collectEntries { String deploymentName, Map<String, Object> deployment ->
                def filteredFields = deployment.findAll { k, v -> k != 'podName' }
                return [(deploymentName): filteredFields]
            } as Map<String, Map<String, Object>>
    }

    private static Map<String, Map<String, Object>> formatTIRBuilds(Map<String, Map<String, Object>> builds) {
        if (!builds) {
            return [:]
        }

        return builds.collectEntries { String buildKey, Map<String, Object> build ->
            Map<String, Object> formattedBuild = build
                .collectEntries { String key, Object value -> [(StringUtils.capitalize(key)): value] }
            return [(buildKey): formattedBuild]
        } as Map<String, Map<String, Object>>
    }

    protected static Map<String, Object> formatTIRHelmDeploymentMean(Map<String, Object> mean, String targetEnvironment) {
        Map<String, Object> formattedMean = [:]

        def envConfigFiles = mean.helmEnvBasedValuesFiles?.collect{ filenamePattern ->
            filenamePattern.replace('.env.', ".${targetEnvironment}.")
        }

        // Global config files are those that are not environment specific
        def configFiles = mean.helmValuesFiles?.findAll { !envConfigFiles.contains(it) }

        formattedMean.namespace = mean.namespace ?: 'None'
        formattedMean.type = mean.type
        formattedMean.descriptorPath = mean.chartDir ?: '.'
        formattedMean.defaultCmdLineArgs = mean.helmDefaultFlags.join(' ') ?: 'None'
        formattedMean.additionalCmdLineArgs = mean.helmAdditionalFlags.join(' ') ?: 'None'
        formattedMean.configParams = HtmlFormatterUtil.toUl(mean.helmValues as Map, 'None')
        formattedMean.configFiles = HtmlFormatterUtil.toUl(configFiles as List, 'None')
        formattedMean.envConfigFiles = HtmlFormatterUtil.toUl(envConfigFiles as List, 'None')

        Map<String, Object> formattedStatus = [:]

        formattedStatus.deployStatus = (mean.helmStatus.status == "deployed")
            ? "Successfully deployed"
            : mean.helmStatus.status
        formattedStatus.resultMessage = mean.helmStatus.description
        formattedStatus.lastDeployed = mean.helmStatus.lastDeployed
        formattedStatus.resources = HtmlFormatterUtil.toUl(mean.helmStatus.resourcesByKind as Map, 'None')

        formattedMean.deploymentStatus = formattedStatus

        return formattedMean
    }

    protected static Map<String, Object> formatTIRTailorDeploymentMean(Map<String, Object> mean) {
        Map<String, String> defaultValues = [
            tailorParamFile: 'None',
            tailorParams   : 'None',
            tailorPreserve : 'No extra resources specified to be preserved'
        ].withDefault { 'N/A' }

        return mean.collectEntries { k, v ->
            [(k): v ?: defaultValues[k]]
        } as Map<String, Object>
    }



    @NonCPS
    private def computeKeysInDocForTRC(def data) {
        return data.collect { it.subMap(['key', 'risks', 'tests']).values()  }.flatten()
    }
    */


    String getDocumentTemplateName(String documentType, Map repo = null) {
        def capability = this.project.getCapability("LeVADocs")
        if (!capability) {
            return documentType
        }

        def suffix = ""
        // compute suffix based on repository type
        if (repo != null) {
            if (repo.type.toLowerCase() == PipelineConfig.REPO_TYPE_ODS_INFRA) {
                if (documentType == DocumentType.EVD as String) {
                    suffix += "-infra"
                }
            }
        }

        // compute suffix based on gamp category
        if (this.GAMP_CATEGORY_SENSITIVE_DOCS.contains(documentType)) {
            suffix += "-" + capability.GAMPCategory
        }

        return documentType + suffix
    }

    /*
    @NonCPS
    private def computeKeysInDocForTCP(def data) {
        return data.collect { it.subMap(['key', 'requirements', 'bugs']).values() }.flatten()
    }
    */

    @NonCPS
    List<String> getSupportedDocuments() {
        return DocumentType.values().collect { it as String }
    }

    String getDocumentTemplatesVersion() {
        def capability = this.project.getCapability('LeVADocs')
        return capability.templatesVersion ? "${capability.templatesVersion}" : Project.DEFAULT_TEMPLATE_VERSION
    }

    boolean shouldCreateArtifact(String documentType, Map repo) {
        List nonArtifactDocTypes = [
            DocumentType.EVD as String,
            DocumentType.DES as String
        ]

        return !(documentType && nonArtifactDocTypes.contains(documentType) && repo)
    }

    Map getFiletypeForDocumentType(String documentType) {
        if (!documentType) {
            throw new RuntimeException('Cannot lookup Null docType for storage!')
        }
        Map defaultTypes = [storage: 'zip', content: 'pdf']

        if (DOCUMENT_TYPE_NAMES.containsKey(documentType)) {
            return defaultTypes
        } else if (DOCUMENT_TYPE_FILESTORAGE_EXCEPTIONS.containsKey(documentType)) {
            return DOCUMENT_TYPE_FILESTORAGE_EXCEPTIONS.get(documentType)
        }
        return defaultTypes
    }

//    protected String convertImages(String content) {
//        def result = content
//        if (content && content.contains("<img")) {
//            result = this.jiraUseCase.convertHTMLImageSrcIntoBase64Data(content)
//        }
//        result
//    }

    /*
    protected Map computeTestDiscrepancies(String name, List testIssues, Map testResults, boolean checkDuplicateTestResults = true) {
        def result = [
            discrepancies: 'No discrepancies found.',
            conclusion   : [
                summary  : 'Complete success, no discrepancies',
                statement: "It is determined that all steps of the ${name} have been successfully executed and signature of this report verifies that the tests have been performed according to the plan. No discrepancies occurred.",
            ]
        ]

        // Match Jira test issues with test results
        def matchedHandler = { matched ->
            matched.each { testIssue, testCase ->
                testIssue.isSuccess = !(testCase.error || testCase.failure || testCase.skipped)
                testIssue.isUnexecuted = !!testCase.skipped
                testIssue.timestamp = testCase.timestamp
            }
        }

        def unmatchedHandler = { unmatched ->
            unmatched.each { testIssue ->
                testIssue.isSuccess = false
                testIssue.isUnexecuted = true
            }
        }

        this.jiraUseCase.matchStoriesAgainstTestResults(testIssues, testResults ?: [:], matchedHandler, unmatchedHandler, checkDuplicateTestResults)

        // Compute failed and missing Jira test issues
        def failedTestIssues = testIssues.findAll { testIssue ->
            return !testIssue.isSuccess && !testIssue.isUnexecuted
        }

        def unexecutedTestIssues = testIssues.findAll { testIssue ->
            return !testIssue.isSuccess && testIssue.isUnexecuted
        }

        // Compute extraneous failed test cases
        def extraneousFailedTestCases = []
        testResults.testsuites.each { testSuite ->
            extraneousFailedTestCases.addAll(testSuite.testcases.findAll { testCase ->
                return (testCase.error || testCase.failure) && !failedTestIssues.any { this.jiraUseCase.checkStoryIssueMatchesTestCase(it, testCase) }
            })
        }

        // Compute test discrepancies
        def isMajorDiscrepancy = failedTestIssues || unexecutedTestIssues || extraneousFailedTestCases
        if (isMajorDiscrepancy) {
            result.discrepancies = 'The following major discrepancies were found during testing.'
            result.conclusion.summary = 'No success - major discrepancies found'
            result.conclusion.statement = 'Some discrepancies found as'

            if (failedTestIssues || extraneousFailedTestCases) {
                result.conclusion.statement += ' tests did fail'
            }

            if (failedTestIssues) {
                result.discrepancies += " Failed tests: ${failedTestIssues.collect { it.key }.join(', ')}."
            }

            if (extraneousFailedTestCases) {
                result.discrepancies += " Other failed tests: ${extraneousFailedTestCases.size()}."
            }

            if (unexecutedTestIssues) {
                result.discrepancies += " Unexecuted tests: ${unexecutedTestIssues.collect { it.key }.join(', ')}."

                if (failedTestIssues || extraneousFailedTestCases) {
                    result.conclusion.statement += ' and others were not executed'
                } else {
                    result.conclusion.statement += ' tests were not executed'
                }
            }

            result.conclusion.statement += '.'
        }

        return result
    }
    */

    /*
    protected List<Map> computeTestsWithRequirementsAndSpecs(List<Map> tests) {
        def obtainEnum = { category, value ->
            return this.project.getEnumDictionary(category)[value as String]
        }

        tests.collect { testIssue ->
            def softwareDesignSpecs = testIssue.getResolvedTechnicalSpecifications()
                .findAll { it.softwareDesignSpec }
                .collect { it.key }
            def riskLevels = testIssue.getResolvedRisks().collect {
                def value = obtainEnum("SeverityOfImpact", it.severityOfImpact)
                return value ? value.text : "None"
            }
            def description = ''
            if (testIssue.description) {
                description += testIssue.description
            } else {
                description += testIssue.name
            }

            [
                moduleName: testIssue.components.join(", "),
                testKey: testIssue.key,
                description: this.convertImages(description ?: 'N/A'),
                systemRequirement: testIssue.requirements ? testIssue.requirements.join(", ") : "N/A",
                softwareDesignSpec: (softwareDesignSpecs.join(", ")) ?: "N/A",
                riskLevel: riskLevels ? riskLevels.join(", ") : "N/A"
            ]
        }
    }
    */

    /*
    protected List obtainCodeReviewReport(List<Map> repos) {
        def reports = repos.collect { r ->
            // resurrect?
            Map resurrectedDocument = resurrectAndStashDocument('SCRR-MD', r, false)
            this.steps.echo "Resurrected 'SCRR' for ${r.id} -> (${resurrectedDocument.found})"
            if (resurrectedDocument.found) {
                return resurrectedDocument.content
            }

            def sqReportsPath = "${PipelineUtil.SONARQUBE_BASE_DIR}/${r.id}"
            def sqReportsStashName = "scrr-report-${r.id}-${this.steps.env.BUILD_ID}"

            // Unstash SonarQube reports into path
            def hasStashedSonarQubeReports = this.jenkins.unstashFilesIntoPath(sqReportsStashName, "${this.steps.env.WORKSPACE}/${sqReportsPath}", "SonarQube Report")
            if (!hasStashedSonarQubeReports) {
                throw new RuntimeException("Error: unable to unstash SonarQube reports for repo '${r.id}' from stash '${sqReportsStashName}'.")
            }

            // Load SonarQube report files from path
            def sqReportFiles = this.sq.loadReportsFromPath("${this.steps.env.WORKSPACE}/${sqReportsPath}")
            if (sqReportFiles.isEmpty()) {
                throw new RuntimeException("Error: unable to load SonarQube reports for repo '${r.id}' from path '${this.steps.env.WORKSPACE}/${sqReportsPath}'.")
            }

            def name = this.getDocumentBasename('SCRR-MD', this.project.buildParams.version, this.steps.env.BUILD_ID, r)
            def sqReportFile = sqReportFiles.first()

            def generatedSCRR = this.pdf.convertFromMarkdown(sqReportFile, true)

            // store doc - we may need it later for partial deployments
            if (!resurrectedDocument.found) {
                def result = this.storeDocument("${name}.pdf", generatedSCRR, 'application/pdf')
                this.steps.echo "Stored 'SCRR' for later consumption -> ${result}"
            }
            return generatedSCRR
        }

        return reports
    }
    */

    /**
     * This computes the information related to the components (modules) that are being developed
     * @documentType documentType
     * @return component metadata with software design specs, requirements and info comming from the component repo
     */
    /*
    protected Map computeComponentMetadata(String documentType) {
        return this.project.components.collectEntries { component ->
            def normComponentName = component.name.replaceAll('Technology-', '')

            def gitUrl = new GitService(this.steps, logger).getOriginUrl()
            def isReleaseManagerComponent =
                gitUrl.endsWith("${this.project.key}-${normComponentName}.git".toLowerCase())
            if (isReleaseManagerComponent) {
                return [:]
            }

            def repo_ = this.project.repositories.find {
                [it.id, it.name, it.metadata.name].contains(normComponentName)
            }
            if (!repo_) {
                def repoNamesAndIds = this.project.repositories.collect { [id: it.id, name: it.name] }
                throw new RuntimeException("Error: unable to create ${documentType}. Could not find a repository " +
                    "configuration with id or name equal to '${normComponentName}' for " +
                    "Jira component '${component.name}' in project '${this.project.key}'. Please check " +
                    "the metatada.yml file. In this file there are the following repositories " +
                    "configured: ${repoNamesAndIds}")
            }

            def metadata = repo_.metadata

            def sowftwareDesignSpecs = component.getResolvedTechnicalSpecifications()
                .findAll { it.softwareDesignSpec }
                .collect { [key: it.key, softwareDesignSpec: this.convertImages(it.softwareDesignSpec)] }

            return [
                (component.name): [
                    key               : component.name,
                    componentName     : component.name,
                    componentId       : metadata.id ?: 'N/A - part of this application',
                    componentType     : INTERNAL_TO_EXT_COMPONENT_TYPES.get(repo_.type?.toLowerCase()),
                    doInstall         : PipelineConfig.INSTALLABLE_REPO_TYPES.contains(repo_.type),
                    odsRepoType       : repo_.type?.toLowerCase(),
                    description       : metadata.description,
                    nameOfSoftware    : normComponentName ?: metadata.name,
                    references        : metadata.references ?: 'N/A',
                    supplier          : metadata.supplier,
                    version           : (repo_.type?.toLowerCase() == PipelineConfig.REPO_TYPE_ODS_CODE) ?
                        this.project.buildParams.version :
                        metadata.version,
                    requirements      : component.getResolvedSystemRequirements(),
                    requirementKeys   : component.requirements,
                    softwareDesignSpecKeys: sowftwareDesignSpecs.collect { it.key },
                    softwareDesignSpec: sowftwareDesignSpecs
                ]
            ]
        }
    }
    */

    /*
    protected Map computeComponentsUnitTests(List<Map> tests) {
        def issueComponentMapping = tests.collect { test ->
            test.getResolvedComponents().collect { [test: test.key, component: it.name] }
        }.flatten()
        issueComponentMapping.groupBy { it.component }.collectEntries { c, v ->
            [(c.replaceAll("Technology-", "")): v.collect { it.test }]
        }
    }

    protected List<Map> getReposWithUnitTestsInfo(List<Map> unitTests) {
        def componentTestMapping = computeComponentsUnitTests(unitTests)
        this.project.repositories.collect {
            [
                id: it.id,
                description: it.metadata?.description,
                tests: componentTestMapping[it.id] ? componentTestMapping[it.id].join(", ") : "None defined"
            ]
        }
    }

    private Map groupTestsByRepoType(List jiraTestIssues) {
        return jiraTestIssues.collect { test ->
            def components = test.getResolvedComponents()
            test.repoTypes = components.collect { component ->
                def normalizedComponentName = component.name.replaceAll('Technology-', '')
                def repository = this.project.repositories.find { repository ->
                    [repository.id, repository.name].contains(normalizedComponentName)
                }

                if (!repository) {
                    throw new IllegalArgumentException("Error: unable to find a repository definition with id or name equal to '${normalizedComponentName}' for Jira component '${component.name}' in project '${this.project.id}'.")
                }

                return repository.type
            } as Set

            return test
        }.groupBy { it.repoTypes }
    }
    */

    protected Map getDocumentMetadata(String documentTypeName) {
        def name = this.project.name
        def configItem = project.buildParams.configItem
        def gitData = this.project.gitData
        def commitHash = gitData.commit
        def gitURL = gitData.url as String
        def rmRepo = extractRepoName(gitURL)
        def commitURL = "${bbt.baseURL}/projects/${project.key}/repos/${rmRepo}/commits/${commitHash}"
        def timestamp = Instant.now()
        def dateCreated = ZonedDateTime.ofInstant(timestamp, UTC).format(DATE_TIME_FORMATTER)
        def changeId = project.buildParams.changeId

        def metadata = [
            name          : name,
            type          : documentTypeName,
            header        : ["${documentTypeName}, Config Item: ${configItem}"],
            dateCreated   : dateCreated,
            configItem    : configItem,
            changeId      : changeId,
            changeReason  : project.getVersionData(changeId)?.description,
            openShiftURL  : project.openShiftApiUrl,
            gitURL        : gitURL,
            rmCommitHash  : commitHash,
            commitURL     : commitURL,
            build         : "${steps.env.JOB_NAME}/${steps.env.BUILD_NUMBER}",
        ]

        return metadata
    }

    @NonCPS
    private String extractRepoName(String url) {
        def repoURL = new URI(url)
        def repo = repoURL.path
        while (repo.endsWith('/')) {
            repo = repo.substring(0, repo.length() - 1)
        }
        if (repo.endsWith('.git')) {
            repo = repo.substring(0, repo.length() - 4)
        }
        def repoNameOffset = repo.lastIndexOf('/')
        if (repoNameOffset > -1) {
            repo = repo.substring(repoNameOffset + 1, repo.length())
        }
        return repo
    }

    /*
    private List<String> getJiraTrackingIssueLabelsForDocTypeAndEnvs(String documentType, List<String> envs = null) {
        def labels = []

        def environments = envs ?: this.project.buildParams.targetEnvironmentToken
        environments.each { env ->
            LeVADocumentScheduler.ENVIRONMENT_TYPE[env].get(documentType).each { label ->
                labels.add("${JiraUseCase.LabelPrefix.DOCUMENT}${label}")
            }
        }

        if (this.project.isDeveloperPreviewMode()) {
            // Assumes that every document we generate along the pipeline has a tracking issue in Jira
            labels.add("${JiraUseCase.LabelPrefix.DOCUMENT}${documentType}")
        }

        return labels
    }
    */

    protected String getWatermarkText(String documentType) {
        def result = null

        if (this.project.isDeveloperPreviewMode()) {
            result = this.DEVELOPER_PREVIEW_WATERMARK
        }

        return result
    }

    /*
    protected void updateJiraDocumentationTrackingIssue(String documentType,
                                                        String docLocation,
                                                        String documentVersionId = null) {
        if (!this.jiraUseCase) return
        if (!this.jiraUseCase.jira) return

        def jiraIssues = this.getDocumentTrackingIssues(documentType)
        def msg = "A new ${DOCUMENT_TYPE_NAMES[documentType]} has been generated and is available at: ${docLocation}."
        def sectionsNotDone = this.getSectionsNotDone(documentType)
        // Append a warning message for documents which are considered work in progress
        if (!sectionsNotDone.isEmpty()) {
            msg += " ${WORK_IN_PROGRESS_DOCUMENT_MESSAGE} See issues:" +
                " ${sectionsNotDone.join(', ')}"
        }

        // Append a warning message if there are any open tasks. Documents will not be considered final
        // TODO review me
        if (documentVersionId && !this.project.isDeveloperPreviewMode() && this.project.hasWipJiraIssues()) {
            msg += "\n *Since there are WIP issues in Jira that affect one or more documents," +
                " this document cannot be considered final.*"
        }

        if (!documentVersionId) {
            def metadata = this.getDocumentMetadata(documentType)
            documentVersionId = "${metadata.version}-${metadata.jenkins.buildNumber}"
        }

        jiraIssues.each { Map jiraIssue ->
            this.updateValidDocVersionInJira(jiraIssue.key as String, documentVersionId)
            this.jiraUseCase.jira.appendCommentToIssue(jiraIssue.key as String, msg)
        }
    }
    */

    /*
    protected List<String> getSectionsNotDone(String documentType) {
        return this.project.getWIPDocChaptersForDocument(documentType)
    }

    @NonCPS
    protected List<String> computeSectionsNotDone(Map issues = [:]) {
        if (!issues) return []
        return issues.values().findAll { !it.status?.equalsIgnoreCase('done') }.collect { it.key }
    }

    protected DocumentHistory getAndStoreDocumentHistory(String documentName, List<String> keysInDoc = []) {
        if (!this.jiraUseCase) return
        if (!this.jiraUseCase.jira) return
        // If we have already saved the version, load it from project
        if (this.project.historyForDocumentExists(documentName)) {
            return this.project.getHistoryForDocument(documentName)
        } else {
            def documentType = LeVADocumentUtil.getTypeFromName(documentName)
            def jiraData = this.project.data.jira as Map
            def environment = this.computeSavedDocumentEnvironment(documentType)
            def docHistory = new DocumentHistory(this.steps, logger, environment, documentName)
            def docChapters = this.project.getDocumentChaptersForDocument(documentType)
            def docChapterKeys = docChapters.collect { chapter ->
                chapter.key
            }
            docHistory.load(jiraData, (keysInDoc + docChapterKeys).unique())

            // Save the doc history to project class, so it can be persisted when considered
            this.project.setHistoryForDocument(docHistory, documentName)

            return docHistory
        }
    }

    protected String computeSavedDocumentEnvironment(String documentType) {
        def environment = this.project.buildParams.targetEnvironmentToken
        if (this.project.isWorkInProgress) {
            environment = Environment.values().collect { it.toString() }.find { env ->
                LeVADocumentScheduler.ENVIRONMENT_TYPE[env].containsKey(documentType)
            }
        }
        environment
    }
    */

    /*
    protected void updateValidDocVersionInJira(String jiraIssueKey, String docVersionId) {
        def documentationTrackingIssueFields = this.project.getJiraFieldsForIssueType(JiraUseCase.IssueTypes.DOCUMENTATION_TRACKING)
        def documentationTrackingIssueDocumentVersionField = documentationTrackingIssueFields[JiraUseCase.CustomIssueFields.DOCUMENT_VERSION]

        if (this.project.isVersioningEnabled) {
            if (!this.project.isDeveloperPreviewMode() && !this.project.hasWipJiraIssues()) {
                // In case of generating a final document, we add the label for the version that should be released
                this.jiraUseCase.jira.updateTextFieldsOnIssue(jiraIssueKey,
                    [(documentationTrackingIssueDocumentVersionField.id): "${docVersionId}"])
            }
        } else {
            // TODO removeme for ODS 4.0
            this.jiraUseCase.jira.updateTextFieldsOnIssue(jiraIssueKey,
                [(documentationTrackingIssueDocumentVersionField.id): "${docVersionId}"])
        }
    }

    protected List<Map> getDocumentTrackingIssues(String documentType, List<String> environments = null) {
        def jiraDocumentLabels = this.getJiraTrackingIssueLabelsForDocTypeAndEnvs(documentType, environments)
        def jiraIssues = this.project.getDocumentTrackingIssues(jiraDocumentLabels)
        if (jiraIssues.isEmpty()) {
            throw new RuntimeException("Error: no Jira tracking issue associated with document type '${documentType}'.")
        }
        return jiraIssues
    }
    protected List<Map> getDocumentTrackingIssuesForHistory(String documentType, List<String> environments = null) {
        def jiraDocumentLabels = this.getJiraTrackingIssueLabelsForDocTypeAndEnvs(documentType, environments)
        def jiraIssues = this.project.getDocumentTrackingIssuesForHistory(jiraDocumentLabels)
        if (jiraIssues.isEmpty()) {
            throw new RuntimeException("Error: no Jira tracking issue associated with document type '${documentType}'.")
        }
        return jiraIssues
    }
    */

    /*
    protected Map getDocumentSections(String documentType) {
        def sections = this.project.getDocumentChaptersForDocument(documentType)

        if (!sections) {
            throw new RuntimeException("Error: unable to create ${documentType}. " +
                'Could not obtain document chapter data from Jira.')
        }

        return sections.collectEntries { sec ->
            [(sec.section): sec + [content: this.convertImages(sec.content), show: this.project.isIssueToBeShown(sec)]]
        }
    }

    protected Map getDocumentSectionsFileOptional(String documentType) {
        def sections = this.project.getDocumentChaptersForDocument(documentType)
        sections = sections?.collectEntries { sec ->
            [(sec.section): sec + [content: this.convertImages(sec.content), show: this.project.isIssueToBeShown(sec)]]
        }

        if (!sections || sections.isEmpty()) {
            sections = this.levaFiles.getDocumentChapterData(documentType)
            if (!this.project.data.jira.undoneDocChapters) {
                this.project.data.jira.undoneDocChapters = [:]
            }
            this.project.data.jira.undoneDocChapters[documentType] = this.computeSectionsNotDone(sections)
            sections = sections?.collectEntries { key, sec ->
                [(key): sec + [content: this.convertImages(sec.content), show: true]]
            }
        }

        if (!sections) {
            throw new RuntimeException("Error: unable to create ${documentType}. " +
                'Could not obtain document chapter data from Jira nor files.')
        }
        // Extract-out the section, as needed for the DocGen interface
        return sections
    }
    */

    /**
     * Gets the valid or to be valid document version either from the current project (for documents created
     * together) or from Jira for documents generated in another environments.
     * @param document to be gathered the id of
     * @return string with the valid id
     */
    /*
    protected Long getLatestDocVersionId(String document, List<String> environments = null) {
        if (this.project.historyForDocumentExists(document)) {
            this.project.getHistoryForDocument(document).getVersion()
        } else {
            def trackingIssues = this.getDocumentTrackingIssuesForHistory(document, environments)
            this.jiraUseCase.getLatestDocVersionId(trackingIssues)
        }
    }
    */

    /**
     * gets teh document version IDS at the start ... can't do that...
     * @return Map
     */
    /*
    protected Map getReferencedDocumentsVersion() {
        if (!this.jiraUseCase) return [:]
        if (!this.jiraUseCase.jira) return [:]

        def referencedDcocs = [
            DocumentType.REQ,
            DocumentType.DES,
            DocumentType.EVD,
        ]

        referencedDcocs.collectEntries { DocumentType dt ->
            def doc = dt as String
            def version = getVersion(this.project, doc)

            return [(doc): "${this.project.buildParams.configItem} / See version created within this change",
                    ("${doc}_version" as String): version]
        }
    }

    protected String getVersion(Project project, String doc) {
        def version

        if (project.isVersioningEnabled) {
            version = project.getDocumentVersionFromHistories(doc)
            if (!version) {
                // The document has not (yet) been generated in this pipeline run.
                def envs = Environment.values().collect { it.toString() }
                def trackingIssues = this.getDocumentTrackingIssuesForHistory(doc, envs)
                version = this.jiraUseCase.getLatestDocVersionId(trackingIssues)
                if (project.isWorkInProgress ||
                    LeVADocumentScheduler.getFirstCreationEnvironment(doc) ==
                    project.buildParams.targetEnvironmentToken) {
                    // Either this is a developer preview or the history is to be updated in this environment.
                    version += 1L
                }
            }
        } else {
            // TODO removeme in ODS 4.x
            return "${project.buildParams.version}-${this.steps.env.BUILD_NUMBER}"
        }

        return "${this.steps.env.RELEASE_PARAM_VERSION}/${version}"
    }
    */

    /*
    @NonCPS
    private def computeKeysInDocForTCR(def data) {
        return data.collect { it.subMap(['key', 'requirements', 'bugs']).values() }.flatten()
    }
    */

//    protected String replaceDashToNonBreakableUnicode(theString) {
//        return theString?.replaceAll('-', '&#x2011;')
//    }

}
