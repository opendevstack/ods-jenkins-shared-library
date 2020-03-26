package org.ods.usecase

import org.apache.commons.io.FilenameUtils
import org.ods.scheduler.LeVADocumentScheduler
import org.ods.service.*
import org.ods.util.*

import java.time.LocalDateTime

class LeVADocumentUseCase extends DocGenUseCase {

    enum DocumentType {
        CSD,
        DIL,
        DTP,
        DTR,
        RA,
        CFTP,
        CFTR,
        IVP,
        IVR,
        SSDS,
        TCP,
        TCR,
        TIP,
        TIR,
        TRC,
        OVERALL_DTR,
        OVERALL_IVR,
        OVERALL_TIR
    }

    private static Map DOCUMENT_TYPE_NAMES = [
        (DocumentType.CSD as String)        : "Combined Specification Document",
        (DocumentType.DIL as String)        : "Discrepancy Log",
        (DocumentType.DTP as String)        : "Software Development Testing Plan",
        (DocumentType.DTR as String)        : "Software Development Testing Report",
        (DocumentType.CFTP as String)       : "Combined Functional and Requirements Testing Plan",
        (DocumentType.CFTR as String)       : "Combined Functional and Requirements Testing Report",
        (DocumentType.IVP as String)        : "Configuration and Installation Testing Plan",
        (DocumentType.IVR as String)        : "Configuration and Installation Testing Report",
        (DocumentType.RA as String)         : "Risk Assessment",
        (DocumentType.TRC as String)        : "Traceability Matrix",
        (DocumentType.SSDS as String)       : "System and Software Design Specification",
        (DocumentType.TCP as String)        : "Test Case Plan",
        (DocumentType.TCR as String)        : "Test Case Report",
        (DocumentType.TIP as String)        : "Technical Installation Plan",
        (DocumentType.TIR as String)        : "Technical Installation Report",
        (DocumentType.OVERALL_DTR as String): "Overall Software Development Testing Report",
        (DocumentType.OVERALL_IVR as String): "Overall Configuration and Installation Testing Report",
        (DocumentType.OVERALL_TIR as String): "Overall Technical Installation Report"
    ]

    static GAMP_CATEGORY_SENSITIVE_DOCS = [
        DocumentType.SSDS as String,
        DocumentType.CSD as String,
        DocumentType.CFTP as String,
        DocumentType.CFTR as String
    ]

    private static String DEVELOPER_PREVIEW_WATERMARK = "Developer Preview"
    private static String WORK_IN_PROGRESS_WATERMARK = "Work in Progress"
    private static String WORK_IN_PROGRESS_DOCUMENT_MESSAGE = "Attention: this document is work in progress!"

    private JenkinsService jenkins
    private JiraUseCase jiraUseCase
    private JUnitTestReportsUseCase junit
    private LeVADocumentChaptersFileService levaFiles
    private OpenShiftService os
    private SonarQubeUseCase sq

    LeVADocumentUseCase(Project project, IPipelineSteps steps, MROPipelineUtil util, DocGenService docGen, JenkinsService jenkins, JiraUseCase jiraUseCase, JUnitTestReportsUseCase junit, LeVADocumentChaptersFileService levaFiles, NexusService nexus, OpenShiftService os, PDFUtil pdf, SonarQubeUseCase sq) {
        super(project, steps, util, docGen, nexus, pdf)
        this.jenkins = jenkins
        this.jiraUseCase = jiraUseCase
        this.junit = junit
        this.levaFiles = levaFiles
        this.os = os
        this.sq = sq
    }

    /**
     * This computes the information related to the components (modules) that are being developed
     * @param documentType
     * @return
     */
    protected Map computeComponentMetadata(String documentType) {
        return this.project.components.collectEntries { component ->
            def normComponentName = component.name.replaceAll("Technology-", "")

            def repo_ = this.project.repositories.find { [it.id, it.name, it.metadata.name].contains(normComponentName) }
            if (!repo_) {
                throw new RuntimeException("Error: unable to create ${documentType}. Could not find a repository configuration with id or name equal to '${normComponentName}' for Jira component '${component.name}' in project '${this.project.key}'.")
            }

            def metadata = repo_.metadata

            return [
                component.name,
                [
                    key               : component.key,
                    componentName     : component.name,
                    componentId       : metadata.id ?: "N/A - part of this application",
                    componentType     : (repo_.type?.toLowerCase() == MROPipelineUtil.PipelineConfig.REPO_TYPE_ODS_CODE) ? "ODS Component" : "Software",
                    odsRepoType       : repo_.type?.toLowerCase(),
                    description       : metadata.description,
                    nameOfSoftware    : metadata.name,
                    references        : metadata.references ?: "N/A",
                    supplier          : metadata.supplier,
                    version           : (repo_.type?.toLowerCase() == MROPipelineUtil.PipelineConfig.REPO_TYPE_ODS_CODE) ?
                                        this.project.buildParams.version :
                                        metadata.version,
                    requirements      : component.getResolvedSystemRequirements(),
                    softwareDesignSpec: component.getResolvedTechnicalSpecifications().findAll {
                        it.softwareDesignSpec
                    }.collect {
                        [key: it.key, softwareDesignSpec: it.softwareDesignSpec]
                    }
                ]
            ]
        }
    }

    private Map obtainCodeReviewReport(List<Map> repos) {
        def reports =  repos.collect { r ->
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

            def name = this.getDocumentBasename("SCRR-MD", this.project.buildParams.version, this.steps.env.BUILD_ID, r)
            def sqReportFile = sqReportFiles.first()

            return this.pdf.convertFromMarkdown(sqReportFile, true)
        }

        return this.pdf.merge(reports)
    }

    protected Map computeTestDiscrepancies(String name, List testIssues, Map testResults) {
        def result = [
            discrepancies: "No discrepancies found.",
            conclusion   : [
                summary  : "Complete success, no discrepancies",
                statement: "It is determined that all steps of the ${name} have been successfully executed and signature of this report verifies that the tests have been performed according to the plan. No discrepancies occurred."
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

        this.jiraUseCase.matchTestIssuesAgainstTestResults(testIssues, testResults ?: [:], matchedHandler, unmatchedHandler)

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
                return (testCase.error || testCase.failure) && !failedTestIssues.any { this.jiraUseCase.checkTestsIssueMatchesTestCase(it, testCase) }
            })
        }

        // Compute test discrepancies
        def isMajorDiscrepancy = failedTestIssues || unexecutedTestIssues || extraneousFailedTestCases
        if (isMajorDiscrepancy) {
            result.discrepancies = "The following major discrepancies were found during testing."
            result.conclusion.summary = "No success - major discrepancies found"
            result.conclusion.statement = "Some discrepancies found as"

            if (failedTestIssues || extraneousFailedTestCases) {
                result.conclusion.statement += " tests did fail"
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
                    result.conclusion.statement += " and others were not executed"
                } else {
                    result.conclusion.statement += " tests were not executed"
                }
            }

            result.conclusion.statement += "."
        }

        return result
    }

    String createCSD(Map repo = null, Map data = null) {
        def documentType = DocumentType.CSD as String

        def sections = this.jiraUseCase.getDocumentChapterData(documentType)
        if (!sections) {
            throw new RuntimeException("Error: unable to create ${documentType}. Could not obtain document chapter data from Jira.")
        }
        def sectionsNotDone = this.getSectionsNotDone(sections)

        def requirements = this.project.getSystemRequirements()
        def reqsWithNoGampTopic = requirements.findAll{ it.gampTopic == null }
        def reqsGroupedByGampTopic = requirements.findAll{ it.gampTopic != null }.groupBy { it.gampTopic.toLowerCase() }
        reqsGroupedByGampTopic << ["uncategorized": reqsWithNoGampTopic ]
        def requirementsForDocument = reqsGroupedByGampTopic.collectEntries { gampTopic, reqs ->
            [
                gampTopic.replaceAll(" ", "").toLowerCase(),
                SortUtil.sortIssuesByProperties(reqs.collect { req ->
                    [
                        key           : req.key,
                        applicability : "Mandatory",
                        ursName       : req.name,
                        ursDescription: req.description?: "",
                        csName        : req.configSpec.name?: "N/A",
                        csDescription : req.configSpec.description?: "",
                        fsName        : req.funcSpec.name?: "N/A",
                        fsDescription : req.funcSpec.description?: "",
                    ]
                }, ["key"])
            ]
        }

        def data_ = [
            metadata: this.getDocumentMetadata(this.DOCUMENT_TYPE_NAMES[documentType]),
            data    : [
                sections    : sections,
                requirements: requirementsForDocument
            ]
        ]

        def uri = this.createDocument(getDocumentTemplateName(documentType), null, data_, [:], null, documentType, this.getWatermarkText(documentType, sectionsNotDone))
        this.updateJiraDocumentationTrackingIssue(documentType, "A new ${DOCUMENT_TYPE_NAMES[documentType]} has been generated and is available at: ${uri}.", sectionsNotDone)
        return uri
    }

    String createDIL(Map repo = null, Map data = null) {
        def documentType = DocumentType.DIL as String

        def watermarkText = this.getWatermarkText(documentType)

        def bugs = this.project.getBugs().each { bug ->
            bug.tests = bug.getResolvedTests()
        }

        def acceptanceTestBugs = bugs.findAll { bug ->
            bug.tests.findAll { test ->
                test.testType == Project.TestType.ACCEPTANCE
            }
        }

        def integrationTestBugs = bugs.findAll { bug ->
            bug.tests.findAll { test ->
                test.testType == Project.TestType.INTEGRATION
            }
        }

        SortUtil.sortIssuesByProperties(acceptanceTestBugs, ["key"])
        SortUtil.sortIssuesByProperties(integrationTestBugs, ["key"])

        def metadata = this.getDocumentMetadata(this.DOCUMENT_TYPE_NAMES[documentType])
        metadata.orientation = "Landscape"

        def data_ = [
            metadata: metadata,
            data    : [:]
        ]

        if (!integrationTestBugs.isEmpty()) {
            data_.data.integrationTests = integrationTestBugs.collect { bug ->
                [
                    //Discrepancy ID -> BUG Issue ID
                    discrepancyID        : bug.key,
                    //Test Case No. -> JIRA (Test Case Key)
                    testcaseID           : bug.tests.collect{ it.key }.join(", "),
                    //-	Level of Test Case = Unit / Integration / Acceptance / Installation
                    level                : "Integration",
                    //Description of Failure or Discrepancy -> Bug Issue Summary
                    description          : bug.name,
                    //Remediation Action -> "To be fixed"
                    remediation          : "To be fixed",
                    //Responsible / Due Date -> JIRA (assignee, Due date)
                    responsibleAndDueDate: "${bug.assignee ? bug.assignee : 'N/A'} / ${bug.dueDate ? bug.dueDate : 'N/A'}",
                    //Outcome of the Resolution -> Bug Status
                    outcomeResolution    : bug.status,
                    //Resolved Y/N -> JIRA Status -> Done = Yes
                    resolved             : bug.status == "Done" ? "Yes" : "No"
                ]
            }
        }

        if (!acceptanceTestBugs.isEmpty()) {
            data_.data.acceptanceTests = acceptanceTestBugs.collect { bug ->
                [
                    //Discrepancy ID -> BUG Issue ID
                    discrepancyID        : bug.key,
                    //Test Case No. -> JIRA (Test Case Key)
                    testcaseID           : bug.tests.collect{ it.key }.join(", "),
                    //-	Level of Test Case = Unit / Integration / Acceptance / Installation
                    level                : "Acceptance",
                    //Description of Failure or Discrepancy -> Bug Issue Summary
                    description          : bug.name,
                    //Remediation Action -> "To be fixed"
                    remediation          : "To be fixed",
                    //Responsible / Due Date -> JIRA (assignee, Due date)
                    responsibleAndDueDate: "${bug.assignee ? bug.assignee : 'N/A'} / ${bug.dueDate ? bug.dueDate : 'N/A'}",
                    //Outcome of the Resolution -> Bug Status
                    outcomeResolution    : bug.status,
                    //Resolved Y/N -> JIRA Status -> Done = Yes
                    resolved             : bug.status == "Done" ? "Yes" : "No"
                ]
            }
        }

        def uri = this.createDocument(getDocumentTemplateName(documentType), null, data_, [:], null, documentType, watermarkText)
        this.updateJiraDocumentationTrackingIssue(documentType, "A new ${LeVADocumentUseCase.DOCUMENT_TYPE_NAMES[documentType]} has been generated and is available at: ${uri}.")
        return uri
    }

    String createDTP(Map repo = null, Map data = null) {
        def documentType = DocumentType.DTP as String

        def watermarkText
        def sections = this.jiraUseCase.getDocumentChapterData(documentType)
        if (!sections) {
            sections = this.levaFiles.getDocumentChapterData(documentType)
        } else {
            watermarkText = this.getWatermarkText(documentType, sectionsNotDone)
        }
        def sectionsNotDone = this.getSectionsNotDone(sections)

        def unitTests = this.project.getAutomatedTestsTypeUnit()

        def data_ = [
            metadata: this.getDocumentMetadata(this.DOCUMENT_TYPE_NAMES[documentType], repo),
            data    : [
                sections: sections,
                tests: this.computeTestsWithRequirementsAndSpecs(unitTests),
                modules: this.getReposWithUnitTestsInfo(unitTests)
            ]
        ]

        def uri = this.createDocument(getDocumentTemplateName(documentType), null, data_, [:], null, documentType, watermarkText)
        this.updateJiraDocumentationTrackingIssue(documentType, "A new ${DOCUMENT_TYPE_NAMES[documentType]} has been generated and is available at: ${uri}.", sectionsNotDone)
        return uri
    }

    protected List<Map> computeTestsWithRequirementsAndSpecs(List<Map> tests) {
        def obtainEnumText = { category, value ->
            this.project.getEnumDictionary(category)[value as String].text
        }

        tests.collect { testIssue ->
            def techSpecsWithSoftwareDesignSpec = testIssue.getTechnicalSpecifications().findAll{ it.softwareDesignSpec }.collect{ it.key }
			def risks = testIssue.getResolvedRisks().collect{ obtainEnumText("SeverityOfImpact", it.severityOfImpact)}

            [
                moduleName: testIssue.components.join(", "),
                testKey: testIssue.key,
                description: testIssue.description ?: "N/A",
                systemRequirement: testIssue.requirements ? testIssue.requirements.join(", ") : "N/A",
                softwareDesignSpec: techSpecsWithSoftwareDesignSpec ? techSpecsWithSoftwareDesignSpec.join(", ") : "N/A",
                riskLevel: risks ? risks.join(", ") : "N/A"
            ]
        }
    }

    protected List<Map> getReposWithUnitTestsInfo(List<Map> unitTests) {
        def componentTestMapping = computeComponentsUnitTests(unitTests)
        this.project.repositories.collect{
            [
                id: it.id,
                description: it.metadata.description,
                tests: componentTestMapping[it.id]? componentTestMapping[it.id].join(", "): "None defined"
            ]
        }
    }

    protected Map computeComponentsUnitTests(List<Map> tests) {
        def issueComponentMapping = tests.collect { test ->
            test.getResolvedComponents().collect {[test: test.key, component: it.name] }
        }.flatten()
        issueComponentMapping.groupBy{ it.component }.collectEntries { c, v ->
             [(c.replaceAll("Technology-", "")): v.collect{it.test}]
        }
    }

    String createDTR(Map repo, Map data) {
        def documentType = DocumentType.DTR as String

        def unitTestData = data.tests.unit

        def watermarkText
        def sections = this.jiraUseCase.getDocumentChapterData(documentType)
        if (!sections) {
            sections = this.levaFiles.getDocumentChapterData(documentType)
        } else {
            watermarkText = this.getWatermarkText(documentType, sectionsNotDone)
        }
        def sectionsNotDone = this.getSectionsNotDone(sections)

        def testIssues = this.project.getAutomatedTestsTypeUnit("Technology-${repo.id}")
        def discrepancies = this.computeTestDiscrepancies("Development Tests", testIssues, unitTestData.testResults)

        def obtainEnumText = { category, value -> this.project.getEnumDictionary(category)[value as String].text }

        def data_ = [
            metadata: this.getDocumentMetadata(this.DOCUMENT_TYPE_NAMES[documentType], repo),
            data    : [
                repo              : repo,
                sections          : sections,
                tests             : testIssues.collect { testIssue ->
                    def risks = testIssue.getResolvedRisks().collect{ obtainEnumText("SeverityOfImpact", it.severityOfImpact)}
                    [
                        key               : testIssue.key,
                        description       : testIssue.description ?: "N/A",
                        systemRequirement : testIssue.requirements.join(", "),
                        success           : testIssue.isSuccess ? "Y" : "N",
                        remarks           : testIssue.isMissing ? "Not executed" : "N/A",
                        softwareDesignSpec: testIssue.getTechnicalSpecifications().findAll{ it.softwareDesignSpec } ?
                                            testIssue.getTechnicalSpecifications().findAll{ it.softwareDesignSpec }.collect{ it.key }.join(", ") : "N/A",
                        riskLevel         : risks ? risks.join(", ") : "N/A"
                    ]
                },
                numAdditionalTests: junit.getNumberOfTestCases(unitTestData.testResults) - testIssues.count { !it.isMissing },
                testFiles         : SortUtil.sortIssuesByProperties(unitTestData.testReportFiles.collect { file ->
                    [name: file.name, path: file.path, text: file.text]
                } ?: [], ["name"]),
                discrepancies     : discrepancies.discrepancies,
                conclusion        : [
                    summary  : discrepancies.conclusion.summary,
                    statement: discrepancies.conclusion.statement
                ]
            ]
        ]

        def files = unitTestData.testReportFiles.collectEntries { file ->
            ["raw/${file.getName()}", file.getBytes()]
        }

        def modifier = { document ->
            repo.data.documents[documentType] = document
            return document
        }

        def uri = this.createDocument(getDocumentTemplateName(documentType), repo, data_, files, modifier, documentType, watermarkText)
        this.updateJiraDocumentationTrackingIssue(documentType, "A new ${DOCUMENT_TYPE_NAMES[documentType]} has been generated and is available at: ${uri}.", sectionsNotDone)
        return uri
    }

    String createCFTP(Map repo = null, Map data = null) {
        def documentType = DocumentType.CFTP as String

        def sections = this.jiraUseCase.getDocumentChapterData(documentType)
        if (!sections) {
            throw new RuntimeException("Error: unable to create ${documentType}. Could not obtain document chapter data from Jira.")
        }
        def sectionsNotDone = this.getSectionsNotDone(sections)

        def acceptanceTestIssues = this.project.getAutomatedTestsTypeAcceptance()
        def integrationTestIssues = this.project.getAutomatedTestsTypeIntegration()

        def data_ = [
            metadata: this.getDocumentMetadata(this.DOCUMENT_TYPE_NAMES[documentType]),
            data    : [
                sections        : sections,
                acceptanceTests : acceptanceTestIssues.collect { testIssue ->
                    [
                        key        : testIssue.key,
                        description: testIssue.description ?: "",
                        ur_key     : testIssue.requirements ? testIssue.requirements.join(", ") : "N/A",
                        risk_key   : testIssue.risks ? testIssue.risks.join(", ") : "N/A"
                    ]
                },
                integrationTests: integrationTestIssues.collect { testIssue ->
                    [
                        key        : testIssue.key,
                        description: testIssue.description ?: "",
                        ur_key     : testIssue.requirements ? testIssue.requirements.join(", ") : "N/A",
                        risk_key   : testIssue.risks ? testIssue.risks.join(", ") : "N/A"
                    ]
                }
            ]
        ]

        def uri = this.createDocument(getDocumentTemplateName(documentType), null, data_, [:], null, documentType, this.getWatermarkText(documentType, sectionsNotDone))
        this.updateJiraDocumentationTrackingIssue(documentType, "A new ${DOCUMENT_TYPE_NAMES[documentType]} has been generated and is available at: ${uri}.", sectionsNotDone)
        return uri
    }

    String createRA(Map repo = null, Map data = null) {
        def documentType = DocumentType.RA as String

        def sections = this.jiraUseCase.getDocumentChapterData(documentType)
        if (!sections) {
            throw new RuntimeException("Error: unable to create ${documentType}. Could not obtain document chapter data from Jira.")
        }
        def sectionsNotDone = this.getSectionsNotDone(sections)

        def obtainEnumShort = { category, value -> (!value)? this.project.getEnumDictionary(category)[value as String]."short" : "ERROR" }
        def obtainEnumValue = { category, value -> (!value)? this.project.getEnumDictionary(category)[value as String].value : "ERROR" }

        def risks = this.project.getRisks().collect { r ->
            def mitigationsText = r.mitigations ? r.mitigations.join(", ") : "None"
            def testsText = r.tests ? r.tests.join(", ") : "None"
            r.proposedMeasures = "Mitigations: ${mitigationsText}<br/>Tests: ${testsText}"

            def requirements = (r.getResolvedSystemRequirements() + r.getResolvedTechnicalSpecifications())
            r.requirements = requirements.collect { it.name }.join("<br/>")
            r.requirementsKey = requirements.collect { it.key }.join("<br/>")

            r.gxpRelevance = obtainEnumShort("GxPRelevance", r.gxpRelevance)
            r.probabilityOfOccurrence = obtainEnumShort("ProbabilityOfOccurrence", r.probabilityOfOccurrence)
            r.severityOfImpact = obtainEnumShort("SeverityOfImpact", r.severityOfImpact)
            r.probabilityOfDetection = obtainEnumShort("ProbabilityOfDetection", r.probabilityOfDetection)
            r.riskPriority = obtainEnumValue("RiskPriority", r.riskPriority)
            return r
        }

        def proposedMeasuresDesription = this.project.getRisks().collect { r ->
            (r.getResolvedTests().collect {
                if (!it) throw new IllegalArgumentException("Error: test for requirement ${r.key} could not be obtained. Check if all of ${r.tests.join(", ")} exist in JIRA")
                [key: it.key, name: it.name, description: it.description, type: "test", referencesRisk: r.key]
            } + r.getResolvedMitigations().collect { [key: it?.key, name: it?.name, description: it?.description, type: "mitigation", referencesRisk: r.key] })
        }.flatten()

        if (!sections."sec4s2s2") sections."sec4s2s2" = [:]

        if (this.project.getProjectProperties()."PROJECT.USES_POO" == "true") {
            sections."sec4s2s2" = [
                usesPoo          : "true",
                lowDescription   : this.project.getProjectProperties()."PROJECT.POO_CAT.LOW",
                mediumDescription: this.project.getProjectProperties()."PROJECT.POO_CAT.MEDIUM",
                highDescription  : this.project.getProjectProperties()."PROJECT.POO_CAT.HIGH"
            ]
        }

        if (!sections."sec5") sections."sec5" = [:]
        sections."sec5".risks = SortUtil.sortIssuesByProperties(risks, ["key"])
        sections."sec5".proposedMeasures = SortUtil.sortIssuesByProperties(proposedMeasuresDesription, ["key"])

        def metadata = this.getDocumentMetadata(this.DOCUMENT_TYPE_NAMES[documentType])
        metadata.orientation = "Landscape"

        def data_ = [
            metadata: metadata,
            data    : [
                sections: sections
            ]
        ]

        def uri = this.createDocument(getDocumentTemplateName(documentType), null, data_, [:], null, documentType, this.getWatermarkText(documentType, sectionsNotDone))
        this.updateJiraDocumentationTrackingIssue(documentType, "A new ${DOCUMENT_TYPE_NAMES[documentType]} has been generated and is available at: ${uri}.", sectionsNotDone)
        return uri
    }

    String createCFTR(Map repo, Map data) {
        def documentType = DocumentType.CFTR as String

        def acceptanceTestData = data.tests.acceptance
        def integrationTestData = data.tests.integration

        def sections = this.jiraUseCase.getDocumentChapterData(documentType)
        if (!sections) {
            throw new RuntimeException("Error: unable to create ${documentType}. Could not obtain document chapter data from Jira.")
        }
        def sectionsNotDone = this.getSectionsNotDone(sections)

        def acceptanceTestIssues = SortUtil.sortIssuesByProperties(this.project.getAutomatedTestsTypeAcceptance(), ["key"])
        def integrationTestIssues = SortUtil.sortIssuesByProperties(this.project.getAutomatedTestsTypeIntegration(), ["key"])
        def discrepancies = this.computeTestDiscrepancies("Integration and Acceptance Tests", (acceptanceTestIssues + integrationTestIssues), junit.combineTestResults([acceptanceTestData.testResults, integrationTestData.testResults]))

        def data_ = [
            metadata: this.getDocumentMetadata(this.DOCUMENT_TYPE_NAMES[documentType]),
            data    : [
                sections                     : sections,
                numAdditionalAcceptanceTests : junit.getNumberOfTestCases(acceptanceTestData.testResults) - acceptanceTestIssues.count { !it.isMissing },
                numAdditionalIntegrationTests: junit.getNumberOfTestCases(integrationTestData.testResults) - integrationTestIssues.count { !it.isMissing },
                conclusion                   : [
                    summary  : discrepancies.conclusion.summary,
                    statement: discrepancies.conclusion.statement
                ]
            ]
        ]

        if (!acceptanceTestIssues.isEmpty()) {
            data_.data.acceptanceTests = acceptanceTestIssues.collect { testIssue ->
                [
                    key        : testIssue.key,
                    datetime   : testIssue.timestamp ? testIssue.timestamp.replaceAll("T", "</br>") : "N/A",
                    description: testIssue.description ?: "",
                    remarks    : testIssue.isMissing ? "not executed" : "",
                    risk_key   : testIssue.risks ? testIssue.risks.join(", ") : "N/A",
                    success    : testIssue.isSuccess ? "Y" : "N",
                    ur_key     : testIssue.requirements ? testIssue.requirements.join(", ") : "N/A"
                ]
            }
        }

        if (!integrationTestIssues.isEmpty()) {
            data_.data.integrationTests = integrationTestIssues.collect { testIssue ->
                [
                    key        : testIssue.key,
                    datetime   : testIssue.timestamp ? testIssue.timestamp.replaceAll("T", "</br>") : "N/A",
                    description: testIssue.description ?: "",
                    remarks    : testIssue.isMissing ? "not executed" : "",
                    risk_key   : testIssue.risks ? testIssue.risks.join(", ") : "N/A",
                    success    : testIssue.isSuccess ? "Y" : "N",
                    ur_key     : testIssue.requirements ? testIssue.requirements.join(", ") : "N/A"
                ]
            }
        }

        def files = (acceptanceTestData.testReportFiles + integrationTestData.testReportFiles).collectEntries { file ->
            ["raw/${file.getName()}", file.getBytes()]
        }

        def uri = this.createDocument(getDocumentTemplateName(documentType), null, data_, files, null, documentType, this.getWatermarkText(documentType, sectionsNotDone))
        this.updateJiraDocumentationTrackingIssue(documentType, "A new ${DOCUMENT_TYPE_NAMES[documentType]} has been generated and is available at: ${uri}.", sectionsNotDone)
        return uri
    }

    String createIVP(Map repo = null, Map data = null) {
        def documentType = DocumentType.IVP as String

        def watermarkText
        def sections = this.jiraUseCase.getDocumentChapterData(documentType)
        if (!sections) {
            throw new RuntimeException("Error: unable to create ${documentType}. Could not obtain document chapter data from Jira.")
        } else {
            watermarkText = this.getWatermarkText(documentType, sectionsNotDone)
        }
        def sectionsNotDone = this.getSectionsNotDone(sections)

        def installationTestIssues = this.project.getAutomatedTestsTypeInstallation()

        def testsGroupedByRepoType = groupTestsByRepoType(installationTestIssues)

        def testsOfRepoTypeOdsCode = []
        def testsOfRepoTypeOdsService = []
        testsGroupedByRepoType.each { repoTypes, tests ->
            if (repoTypes.contains(MROPipelineUtil.PipelineConfig.REPO_TYPE_ODS_CODE)) {
                testsOfRepoTypeOdsCode.addAll(tests)
            }

            if (repoTypes.contains(MROPipelineUtil.PipelineConfig.REPO_TYPE_ODS_SERVICE)) {
                testsOfRepoTypeOdsService.addAll(tests)
            }
        }

        def data_ = [
            metadata: this.getDocumentMetadata(DOCUMENT_TYPE_NAMES[documentType]),
            data    : [
                repositories   : this.project.repositories.collect { [id: it.id, type: it.type, data: [git: [url: it.data.git == null ? null : it.data.git.url]]] },
                sections       : sections,
                tests          : SortUtil.sortIssuesByProperties(installationTestIssues.collect { testIssue ->
                    [
                        key     : testIssue.key,
                        summary : testIssue.name,
                        techSpec: testIssue.techSpecs.join(", ")
                    ]
                }, ["key"]),
                testsOdsService: testsOfRepoTypeOdsService,
                testsOdsCode   : testsOfRepoTypeOdsCode
            ]
        ]

        def uri = this.createDocument(getDocumentTemplateName(documentType), null, data_, [:], null, documentType, watermarkText)
        this.updateJiraDocumentationTrackingIssue(documentType, "A new ${DOCUMENT_TYPE_NAMES[documentType]} has been generated and is available at: ${uri}.", sectionsNotDone)
        return uri
    }

    String createTCR(Map repo = null, Map data = null) {
        String documentType = DocumentType.TCR as String

        def watermarkText
        def sections = this.jiraUseCase.getDocumentChapterData(documentType)
        if (!sections) {
            throw new RuntimeException("Error: unable to create ${documentType}. Could not obtain document chapter data from Jira.")
        } else {
            watermarkText = this.getWatermarkText(documentType, sectionsNotDone)
        }
        def sectionsNotDone = this.getSectionsNotDone(sections)

        def integrationTestData = data.tests.integration
        def integrationTestIssues = this.project.getAutomatedTestsTypeIntegration()

        def acceptanceTestData = data.tests.acceptance
        def acceptanceTestIssues = this.project.getAutomatedTestsTypeAcceptance()

        def matchedHandler = { result ->
            result.each { testIssue, testCase ->
                testIssue.isSuccess = !(testCase.error || testCase.failure || testCase.skipped
                    || !testIssue.getResolvedBugs().findAll{ bug -> bug.status?.toLowerCase() != "done"}.isEmpty()
                    || testIssue.isUnexecuted)
                testIssue.comment = testIssue.isUnexecuted ? "This Test Case has not been executed" : ""
                testIssue.timestamp = testIssue.isUnexecuted ? "N/A" : testCase.timestamp
                testIssue.isMissing = false
                testIssue.actualResult = testIssue.isSuccess ? "expected result verified by automated test" :
                                         !testIssue.isUnexecuted ? "test failed. Correction will be tracked by Jira issue task \"bug\" listed below (\"" + this.project.key.toUpperCase() +"\")." : "not executed"
            }
        }

        def unmatchedHandler = { result ->
            result.each { testIssue ->
                testIssue.isSuccess = false
                testIssue.isMissing = true
                testIssue.comment = testIssue.isUnexecuted ? "This Test Case has not been executed" : ""
                testIssue.actualResult = !testIssue.isUnexecuted ? "test failed. Correction will be tracked by Jira issue task \"bug\" listed below (\"" + this.project.key.toUpperCase() +"\")." : "not executed"
            }
        }

        this.jiraUseCase.matchTestIssuesAgainstTestResults(integrationTestIssues, integrationTestData?.testResults ?: [:], matchedHandler, unmatchedHandler)
        this.jiraUseCase.matchTestIssuesAgainstTestResults(acceptanceTestIssues, acceptanceTestData?.testResults ?: [:], matchedHandler, unmatchedHandler)

        def data_ = [
            metadata: this.getDocumentMetadata(DOCUMENT_TYPE_NAMES[documentType]),
            data    : [
                sections            : sections,
                integrationTests    : SortUtil.sortIssuesByProperties(integrationTestIssues.collect { testIssue ->
                    [
                        key         : testIssue.key,
                        description : testIssue.description,
                        requirements: testIssue.requirements ? testIssue.requirements.join(", ") : "N/A",
                        isSuccess   : testIssue.isSuccess,
                        bugs        : testIssue.bugs ? testIssue.bugs.join(", ") : (testIssue.comment ? "": "N/A"),
                        steps       : testIssue.steps,
                        timestamp   : testIssue.timestamp ? testIssue.timestamp.replaceAll("T", " ") : "N/A",
                        comment     : testIssue.comment,
                        actualResult: testIssue.actualResult
                    ]
                }, ["key"]),
                acceptanceTests     : SortUtil.sortIssuesByProperties(acceptanceTestIssues.collect { testIssue ->
                    [
                        key         : testIssue.key,
                        description : testIssue.description,
                        requirements: testIssue.requirements ? testIssue.requirements.join(", ") : "N/A",
                        isSuccess   : testIssue.isSuccess,
                        bugs        : testIssue.bugs ? testIssue.bugs.join(", ") : (testIssue.comment ? "": "N/A"),
                        steps       : testIssue.steps,
                        timestamp   : testIssue.timestamp ? testIssue.timestamp.replaceAll("T", " ") : "N/A",
                        comment     : testIssue.comment,
                        actualResult: testIssue.actualResult
                    ]
                }, ["key"]),
                integrationTestFiles: SortUtil.sortIssuesByProperties(integrationTestData.testReportFiles.collect { file ->
                    [name: file.name, path: file.path, text: file.text]
                } ?: [], ["name"]),
                acceptanceTestFiles : SortUtil.sortIssuesByProperties(acceptanceTestData.testReportFiles.collect { file ->
                    [name: file.name, path: file.path, text: file.text]
                } ?: [], ["name"]),
            ]
        ]

        def uri = this.createDocument(getDocumentTemplateName(documentType), null, data_, [:], null, documentType, watermarkText)
        this.updateJiraDocumentationTrackingIssue(documentType, "A new ${DOCUMENT_TYPE_NAMES[documentType]} has been generated and is available at: ${uri}.", sectionsNotDone)
        return uri
    }

    String createTCP(Map repo = null, Map data = null) {
        String documentType = DocumentType.TCP as String

        def watermarkText
        def sections = this.jiraUseCase.getDocumentChapterData(documentType)
        if (!sections) {
            throw new RuntimeException("Error: unable to create ${documentType}. Could not obtain document chapter data from Jira.")
        } else {
            watermarkText = this.getWatermarkText(documentType, sectionsNotDone)
        }
        def sectionsNotDone = this.getSectionsNotDone(sections)

        def integrationTestIssues = this.project.getAutomatedTestsTypeIntegration()
        def acceptanceTestIssues = this.project.getAutomatedTestsTypeAcceptance()

        def data_ = [
            metadata: this.getDocumentMetadata(DOCUMENT_TYPE_NAMES[documentType]),
            data    : [
                sections        : sections,
                integrationTests: SortUtil.sortIssuesByProperties(integrationTestIssues.collect { testIssue ->
                    [
                        key         : testIssue.key,
                        description : testIssue.description,
                        requirements: testIssue.requirements ? testIssue.requirements.join(", ") : "N/A",
                        bugs        : testIssue.bugs ? testIssue.bugs.join(", ") : "N/A",
                        steps       : testIssue.steps
                    ]
                }, ["key"]),
                acceptanceTests : SortUtil.sortIssuesByProperties(acceptanceTestIssues.collect { testIssue ->
                    [
                        key         : testIssue.key,
                        description : testIssue.description,
                        requirements: testIssue.requirements ? testIssue.requirements.join(", ") : "N/A",
                        bugs        : testIssue.bugs ? testIssue.bugs.join(", ") : "N/A",
                        steps       : testIssue.steps
                    ]
                }, ["key"])
            ]
        ]

        def uri = this.createDocument(getDocumentTemplateName(documentType), null, data_, [:], null, documentType, watermarkText)
        this.updateJiraDocumentationTrackingIssue(documentType, "A new ${DOCUMENT_TYPE_NAMES[documentType]} has been generated and is available at: ${uri}.", sectionsNotDone)
        return uri
    }

    String createIVR(Map repo, Map data) {
        def documentType = DocumentType.IVR as String

        def installationTestData = data.tests.installation

        def watermarkText
        def sections = this.jiraUseCase.getDocumentChapterData(documentType)
        if (!sections) {
            throw new RuntimeException("Error: unable to create ${documentType}. Could not obtain document chapter data from Jira.")
        } else {
            watermarkText = this.getWatermarkText(documentType, sectionsNotDone)
        }
        def sectionsNotDone = this.getSectionsNotDone(sections)

        def installationTestIssues = this.project.getAutomatedTestsTypeInstallation()
        def discrepancies = this.computeTestDiscrepancies("Installation Tests", installationTestIssues, installationTestData.testResults)

        def testsOfRepoTypeOdsCode = []
        def testsOfRepoTypeOdsService = []
        def testsGroupedByRepoType = groupTestsByRepoType(installationTestIssues)
        testsGroupedByRepoType.each { repoTypes, tests ->
            if (repoTypes.contains(MROPipelineUtil.PipelineConfig.REPO_TYPE_ODS_CODE)) {
                testsOfRepoTypeOdsCode.addAll(tests)
            }

            if (repoTypes.contains(MROPipelineUtil.PipelineConfig.REPO_TYPE_ODS_SERVICE)) {
                testsOfRepoTypeOdsService.addAll(tests)
            }
        }

        def data_ = [
            metadata: this.getDocumentMetadata(this.DOCUMENT_TYPE_NAMES[documentType]),
            data    : [
                repositories      : this.project.repositories.collect { [id: it.id, type: it.type, data: [git: [url: it.data.git == null ? null : it.data.git.url]]] },
                sections          : sections,
                tests             : SortUtil.sortIssuesByProperties(installationTestIssues.collect { testIssue ->
                    [
                        key        : testIssue.key,
                        description: testIssue.description ?: "",
                        remarks    : testIssue.isMissing ? "not executed" : "",
                        success    : testIssue.isSuccess ? "Y" : "N",
                        summary    : testIssue.name,
                        techSpec   : testIssue.techSpecs.join(", ")
                    ]
                }, ["key"]),
                numAdditionalTests: junit.getNumberOfTestCases(installationTestData.testResults) - installationTestIssues.count { !it.isMissing },
                testFiles         : SortUtil.sortIssuesByProperties(installationTestData.testReportFiles.collect { file ->
                    [name: file.name, path: file.path, text: file.text]
                } ?: [], ["name"]),
                discrepancies     : discrepancies.discrepancies,
                conclusion        : [
                    summary  : discrepancies.conclusion.summary,
                    statement: discrepancies.conclusion.statement
                ],
                testsOdsService   : testsOfRepoTypeOdsService,
                testsOdsCode      : testsOfRepoTypeOdsCode
            ]
        ]

        def files = data.tests.installation.testReportFiles.collectEntries { file ->
            ["raw/${file.getName()}", file.getBytes()]
        }

        def uri = this.createDocument(getDocumentTemplateName(documentType), null, data_, files, null, documentType, watermarkText)
        this.updateJiraDocumentationTrackingIssue(documentType, "A new ${DOCUMENT_TYPE_NAMES[documentType]} has been generated and is available at: ${uri}.", sectionsNotDone)
        return uri
    }

    String createSSDS(Map repo = null, Map data = null) {
        def documentType = DocumentType.SSDS as String

        def sections = this.jiraUseCase.getDocumentChapterData(documentType)
        if (!sections) {
            throw new RuntimeException("Error: unable to create ${documentType}. Could not obtain document chapter data from Jira.")
        }
        def sectionsNotDone = this.getSectionsNotDone(sections)

        def componentsMetadata = SortUtil.sortIssuesByProperties(this.computeComponentMetadata(documentType).collect { it.value }, ["key"])
        def systemDesignSpecifications = this.project.getTechnicalSpecifications()
            .findAll { it.systemDesignSpec }
            .collect { techSpec ->
                [
                    key        : techSpec.key,
                    req_key    : techSpec.requirements.join(", ")?:"None",
                    description: techSpec.systemDesignSpec
                ]
            }

        if (!sections."sec3s1") sections."sec3s1" = [:]
        sections."sec3s1".specifications = SortUtil.sortIssuesByProperties(systemDesignSpecifications, ["req_key", "key"])

        if (!sections."sec5s1") sections."sec5s1" = [:]
        sections."sec5s1".components = componentsMetadata.collect { c ->
            [
                key           : c.key,
                nameOfSoftware: c.nameOfSoftware,
                componentType : c.componentType,
                componentId   : c.componentId,
                description   : c.description,
                supplier      : c.supplier,
                version       : c.version,
                references    : c.references
            ]
        }

        // Get the components that we consider modules in SSDS (the ones you have to code)
        def modules = componentsMetadata.findAll { it.odsRepoType.toLowerCase() == MROPipelineUtil.PipelineConfig.REPO_TYPE_ODS_CODE.toLowerCase() }.collect { component ->
            // We will set-up a double loop in the template. For moustache limitations we need to have lists
            component.requirements = component.requirements.collect { r ->
                [key: r.key, name: r.name, reqDescription: r.description, gampTopic: r.gampTopic?:"uncategorized"]
            }.groupBy { it.gampTopic.toLowerCase() }.collect { k, v -> [gampTopic: k, requirementsofTopic: v] }

            return component
        }

        if (!sections."sec10") sections."sec10" = [:]
        sections."sec10".modules = modules

        // Code review report
        def codeRepos = this.project.repositories.findAll{ it.type?.toLowerCase() == MROPipelineUtil.PipelineConfig.REPO_TYPE_ODS_CODE.toLowerCase() }
        def codeReviewReport = obtainCodeReviewReport(codeRepos)

        def modifier = { document ->
            // Merge the current document with the code review report
            return this.pdf.merge([ document, codeReviewReport])
        }

        def data_ = [
            metadata: this.getDocumentMetadata(this.DOCUMENT_TYPE_NAMES[documentType], repo),
            data    : [
                sections: sections
            ]
        ]

        def uri = this.createDocument(getDocumentTemplateName(documentType), null, data_, [:], modifier, documentType, this.getWatermarkText(documentType, sectionsNotDone))
        this.updateJiraDocumentationTrackingIssue(documentType, "A new ${DOCUMENT_TYPE_NAMES[documentType]} has been generated and is available at: ${uri}.", sectionsNotDone)
        return uri
    }

    String getDocumentTemplateName(String documentType) {
        def capability = this.project.getCapability("LeVADocs")
        if (!capability) {
            return documentType
        }

        return this.GAMP_CATEGORY_SENSITIVE_DOCS.contains(documentType) ? documentType + "-" + capability.GAMPCategory : documentType
    }

    String createTIP(Map repo = null, Map data = null) {
        def documentType = DocumentType.TIP as String

        def watermarkText
        def sections = this.jiraUseCase.getDocumentChapterData(documentType)
        if (!sections) {
            sections = this.levaFiles.getDocumentChapterData(documentType)
        } else {
            watermarkText = this.getWatermarkText(documentType, sectionsNotDone)
        }
        def sectionsNotDone = this.getSectionsNotDone(sections)

        def data_ = [
            metadata: this.getDocumentMetadata(this.DOCUMENT_TYPE_NAMES[documentType]),
            data    : [
                project_key : this.project.key,
                repositories: this.project.repositories,
                sections    : sections
            ]
        ]

        def uri = this.createDocument(getDocumentTemplateName(documentType), null, data_, [:], null, documentType, watermarkText)
        this.updateJiraDocumentationTrackingIssue(documentType, "A new ${DOCUMENT_TYPE_NAMES[documentType]} has been generated and is available at: ${uri}.", sectionsNotDone)
        return uri
    }

    String createTIR(Map repo, Map data) {
        def documentType = DocumentType.TIR as String

        def watermarkText
        def sections = this.jiraUseCase.getDocumentChapterData(documentType)
        if (!sections) {
            sections = this.levaFiles.getDocumentChapterData(documentType)
        } else {
            watermarkText = this.getWatermarkText(documentType, sectionsNotDone)
        }
        def sectionsNotDone = this.getSectionsNotDone(sections)

        if (!data.pod) {
            this.steps.echo "Repo data 'pod' not populated, retrieving latest pod of component ${repo.id}..."
            data.pod = os.getPodDataForComponent(this.project.key, repo.id)
        }

        def data_ = [
            metadata     : this.getDocumentMetadata(this.DOCUMENT_TYPE_NAMES[documentType], repo),
            openShiftData: [
                ocpBuildId          : data.odsBuildArtifacts?."OCP Build Id" ?: "N/A",
                ocpDockerImage      : data.odsBuildArtifacts?."OCP Docker image" ?: "N/A",
                ocpDeploymentId     : data.odsBuildArtifacts?."OCP Deployment Id" ?: "N/A",
                podName             : data.pod?.metadata?.name ?: "N/A",
                podNamespace        : data.pod?.metadata?.namespace ?: "N/A",
                podCreationTimestamp: data.pod?.metadata?.creationTimestamp ?: "N/A",
                podEnvironment      : data.pod?.metadata?.labels?.env ?: "N/A",
                podNode             : data.pod?.spec?.nodeName ?: "N/A",
                podIp               : data.pod?.status?.podIP ?: "N/A",
                podStatus           : data.pod?.status?.phase ?: "N/A"
            ],
            data         : [
                repo    : repo,
                sections: sections
            ]
        ]

        def modifier = { document ->
            repo.data.documents[documentType] = document
            return document
        }

        return this.createDocument(getDocumentTemplateName(documentType), repo, data_, [:], modifier, documentType, watermarkText)
    }

    String createTRC(Map repo = null, Map data = null) {
        def documentType = DocumentType.TRC as String

        def sections = this.jiraUseCase.getDocumentChapterData(documentType)
        if (!sections) {
            throw new RuntimeException("Error: unable to create ${documentType}. Could not obtain document chapter data from Jira.")
        }
        def sectionsNotDone = this.getSectionsNotDone(sections)

        def systemRequirements = this.project.getSystemRequirements().collect { r ->
            [
                key        : r.key,
                name       : r.name,
                description: r.description,
                risks      : r.risks.join(", "),
                tests      : r.tests.join(", ")
            ]
        }

        if (!sections."sec4") sections."sec4" = [:]
        sections."sec4".systemRequirements = SortUtil.sortIssuesByProperties(systemRequirements, ["key"])

        def data_ = [
            metadata: this.getDocumentMetadata(this.DOCUMENT_TYPE_NAMES[documentType], repo),
            data    : [
                sections: sections
            ]
        ]

        def uri = this.createDocument(getDocumentTemplateName(documentType), null, data_, [:], null, documentType, this.getWatermarkText(documentType, sectionsNotDone))
        this.updateJiraDocumentationTrackingIssue(documentType, "A new ${DOCUMENT_TYPE_NAMES[documentType]} has been generated and is available at: ${uri}.", sectionsNotDone)
        return uri
    }

    String createOverallDTR(Map repo = null, Map data = null) {
        def documentTypeName = DOCUMENT_TYPE_NAMES[DocumentType.OVERALL_DTR as String]
        def metadata = this.getDocumentMetadata(documentTypeName)

        def documentType = DocumentType.DTR as String

        def uri = this.createOverallDocument("Overall-Cover", documentType, metadata, null, this.getWatermarkText(documentType))
        this.updateJiraDocumentationTrackingIssue(documentType, "A new ${documentTypeName} has been generated and is available at: ${uri}.")
        return uri
    }

    String createOverallTIR(Map repo = null, Map data = null) {
        def documentTypeName = DOCUMENT_TYPE_NAMES[DocumentType.OVERALL_TIR as String]
        def metadata = this.getDocumentMetadata(documentTypeName)

        def documentType = DocumentType.TIR as String

        def visitor = { data_ ->
            // Prepend a section for the Jenkins build log
            data_.sections.add(0, [
                heading: "Jenkins Build Log"
            ])

            // Add Jenkins build log data
            data_.jenkinsData = [
                log: this.jenkins.getCurrentBuildLogAsText()
            ]
        }

        def uri = this.createOverallDocument("Overall-TIR-Cover", documentType, metadata, visitor, this.getWatermarkText(documentType))
        this.updateJiraDocumentationTrackingIssue(documentType, "A new ${documentTypeName} has been generated and is available at: ${uri}.")
        return uri
    }

    private Map groupTestsByRepoType(List jiraTestIssues) {
        return jiraTestIssues.collect { test ->
            def components = test.getResolvedComponents()
            test.repoTypes = components.collect { component ->
                def normalizedComponentName = component.name.replaceAll("Technology-", "")
                def repository = project.repositories.find { repository ->
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

    Map getDocumentMetadata(String documentTypeName, Map repo = null) {
        def name = this.project.name
        if (repo) {
            name += ": ${repo.id}"
        }

        def metadata = [
            id            : null, // unused
            name          : name,
            description   : this.project.description,
            type          : documentTypeName,
            version       : this.steps.env.RELEASE_PARAM_VERSION,
            date_created  : LocalDateTime.now().toString(),
            buildParameter: this.project.buildParams,
            git           : repo ? repo.data.git : this.project.gitData,
            openShift     : [apiUrl: this.project.getOpenShiftApiUrl()],
            jenkins       : [
                buildNumber: this.steps.env.BUILD_NUMBER,
                buildUrl   : this.steps.env.BUILD_URL,
                jobName    : this.steps.env.JOB_NAME
            ]
        ]

        metadata.header = ["${documentTypeName}, Config Item: ${metadata.buildParameter.configItem}", "Doc ID/Version: see auto-generated cover page"]

        return metadata
    }

    private List<String> getJiraTrackingIssueLabelsForDocumentType(String documentType) {
        def environment = this.project.buildParams.targetEnvironmentToken
        def labels = []

        LeVADocumentScheduler.ENVIRONMENT_TYPE[environment].get(documentType).each { label ->
            labels.add("Doc:${label}")
        }

        if (environment.equals('D')) {
            // Assumes that every document we generate along the pipeline has a tracking issue in Jira
            labels.add("Doc:${documentType}")
        }

        return labels
    }

    List<String> getSupportedDocuments() {
        return DocumentType.values().collect { it as String }
    }

    protected String getWatermarkText(String documentType, List<Map> sectionsNotDone = []) {
        def environment = this.project.buildParams.targetEnvironmentToken

        // The watermark only applies in DEV environment (for documents not to be delivered from that environment)
        if (environment.equals('D') && !LeVADocumentScheduler.ENVIRONMENT_TYPE['D'].containsKey(documentType)) {
            return this.DEVELOPER_PREVIEW_WATERMARK
        }

        // The watermark applies when any tracking issue for the sections of document is not in status DONE
        if (!sectionsNotDone.isEmpty()) {
            return this.WORK_IN_PROGRESS_WATERMARK
        }

        return null
    }

    void updateJiraDocumentationTrackingIssue(String documentType, String message, List<Map> sectionsNotDone = []) {
        if (!this.jiraUseCase) return
        if (!this.jiraUseCase.jira) return

        def jiraDocumentLabels = this.getJiraTrackingIssueLabelsForDocumentType(documentType)

        def jiraIssues = this.project.getDocumentTrackingIssues(jiraDocumentLabels)
        if (jiraIssues.isEmpty()) {
            throw new RuntimeException("Error: no Jira tracking issue associated with document type '${documentType}'.")
        }

        // Append a warning message for documents which are considered work in progress
        if (!sectionsNotDone.isEmpty()) {
            message += " ${this.WORK_IN_PROGRESS_DOCUMENT_MESSAGE} See issues: ${sectionsNotDone.collect { it.key }.join(', ')}"
        }

        def metadata = this.getDocumentMetadata(documentType)
        def documentationTrackingIssueFields = this.project.getJiraFieldsForIssueType(JiraUseCase.IssueTypes.DOCUMENTATION_TRACKING)
        def documentationTrackingIssueDocumentVersionField = documentationTrackingIssueFields["Document Version"]

        jiraIssues.each { jiraIssue ->
            this.jiraUseCase.jira.updateTextFieldsOnIssue(jiraIssue.key, [(documentationTrackingIssueDocumentVersionField.id): "${metadata.version}-${metadata.jenkins.buildNumber}"])
            this.jiraUseCase.jira.appendCommentToIssue(jiraIssue.key, message)
        }
    }

    protected List<Map> getSectionsNotDone (Map issues = [:]) {
        if (!issues) return []
        return issues.values().findAll { !it.status.equalsIgnoreCase("done") }
    }

    String getDocumentTemplatesVersion() {
        def capability = this.project.getCapability("LeVADocs")
        return capability.templatesVersion
    }
}
