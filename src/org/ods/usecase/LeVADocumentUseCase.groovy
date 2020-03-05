package org.ods.usecase

import groovy.json.JsonOutput

import java.time.LocalDateTime

import org.apache.commons.io.FilenameUtils
import org.ods.scheduler.LeVADocumentScheduler
import org.ods.service.DocGenService
import org.ods.service.JenkinsService
import org.ods.service.LeVADocumentChaptersFileService
import org.ods.service.NexusService
import org.ods.service.OpenShiftService
import org.ods.util.IPipelineSteps
import org.ods.util.MROPipelineUtil
import org.ods.util.PDFUtil
import org.ods.util.PipelineUtil
import org.ods.util.Project
import org.ods.util.SortUtil

class LeVADocumentUseCase extends DocGenUseCase {

    class IssueTypes {
        static final String LEVA_DOCUMENTATION = "LeVA Documentation"
    }

    enum DocumentType {
        CSD,
        DTP,
        DTR,
        CFTP,
        CFTR,
        IVP,
        IVR,
        SSDS,
        TIP,
        TIR,
        OVERALL_DTR,
        OVERALL_IVR,
        OVERALL_SSDS,
        OVERALL_TIR
    }

    private static Map DOCUMENT_TYPE_NAMES = [
            (DocumentType.CSD as String)         : "Combined Specification Document",
            (DocumentType.DTP as String)         : "Software Development Testing Plan",
            (DocumentType.DTR as String)         : "Software Development Testing Report",
            (DocumentType.CFTP as String)        : "Combined Functional and Requirements Testing Plan",
            (DocumentType.CFTR as String)        : "Combined Functional and Requirements Testing Report",
            (DocumentType.IVP as String)         : "Configuration and Installation Testing Plan",
            (DocumentType.IVR as String)         : "Configuration and Installation Testing Report",
            (DocumentType.SSDS as String)        : "Software Design Specification",
            (DocumentType.TIP as String)         : "Technical Installation Plan",
            (DocumentType.TIR as String)         : "Technical Installation Report",
            (DocumentType.OVERALL_DTR as String) : "Overall Software Development Testing Report",
            (DocumentType.OVERALL_IVR as String) : "Overall Configuration and Installation Testing Report",
            (DocumentType.OVERALL_SSDS as String): "Overall Software Design Specification",
            (DocumentType.OVERALL_TIR as String) : "Overall Technical Installation Report"
    ]

    private static String DEVELOPER_PREVIEW_WATERMARK = "Developer Preview"

    private JenkinsService jenkins
    private JiraUseCase jiraUseCase
    private LeVADocumentChaptersFileService levaFiles
    private OpenShiftService os
    private SonarQubeUseCase sq

    LeVADocumentUseCase(Project project, IPipelineSteps steps, MROPipelineUtil util, DocGenService docGen, JenkinsService jenkins, JiraUseCase jiraUseCase, LeVADocumentChaptersFileService levaFiles, NexusService nexus, OpenShiftService os, PDFUtil pdf, SonarQubeUseCase sq) {
        super(project, steps, util, docGen, nexus, pdf)
        this.jenkins = jenkins
        this.jiraUseCase = jiraUseCase
        this.levaFiles = levaFiles
        this.os = os
        this.sq = sq
    }

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
                            key           : component.key,
                            componentName : component.name,
                            componentId   : metadata.id ?: "N/A - part of this application",
                            componentType : (repo_.type?.toLowerCase() == MROPipelineUtil.PipelineConfig.REPO_TYPE_ODS_CODE) ? "ODS Component" : "Software",
                            description   : metadata.description,
                            nameOfSoftware: metadata.name,
                            references    : metadata.references ?: "N/A",
                            supplier      : metadata.supplier,
                            version       : metadata.version
                    ]
            ]
        }
    }

    // FIXME: re-implement to depend on testResults, not on testIssues
    // since we want to report all executed tests (and draw conclusions from them)
    protected Map computeTestDiscrepancies(String name, List testIssues) {
        def result = [
                discrepancies: "No discrepancies found.",
                conclusion   : [
                        summary  : "Complete success, no discrepancies",
                        statement: "It is determined that all steps of the ${name} have been successfully executed and signature of this report verifies that the tests have been performed according to the plan. No discrepancies occurred."
                ]
        ]

        def failed = []
        def missing = []

        testIssues.each { issue ->
            if (!issue.isSuccess && !issue.isMissing) {
                failed << issue.key
            }

            if (!issue.isSuccess && issue.isMissing) {
                missing << issue.key
            }
        }

        if (failed.isEmpty() && !missing.isEmpty()) {
            result.discrepancies = "The following minor discrepancies were found during testing: ${missing.join(", ")}."

            result.conclusion = [
                    summary  : "Success - minor discrepancies found",
                    statement: "Some discrepancies were found as tests were not executed, this may be per design."
            ]
        } else if (!failed.isEmpty()) {
            if (!missing.isEmpty()) {
                failed.addAll(missing)
                failed.sort()
            }

            result.discrepancies = "The following major discrepancies were found during testing: ${(failed).join(", ")}."

            result.conclusion = [
                    summary  : "No success - major discrepancies found",
                    statement: "Some discrepancies occured as tests did fail. It is not recommended to continue!"
            ]
        }

        return result
    }

    String createCSD(Map repo = null, Map data = null) {
        def documentType = DocumentType.CSD as String

        def sections = this.jiraUseCase.getDocumentChapterData(documentType)
        if (!sections) {
            throw new RuntimeException("Error: unable to create ${documentType}. Could not obtain document chapter data from Jira.")
        }

        def requirements = this.project.getSystemRequirements()
            .groupBy{ it.gampTopic.toLowerCase() }.collectEntries{ k, v -> 
            [ 
                k.replaceAll(" ","").toLowerCase(), 
                SortUtil.sortIssuesByProperties(v.collect{ req ->
                [
                   key: req.key,
                   applicability: "Mandatory",
                   ursName: req.name,
                   csName: req.configSpec.name,
                   fsName: req.funcSpec.name
                ]}, ["key"])
             ]}
        def data_ = [
            metadata: this.getDocumentMetadata(this.DOCUMENT_TYPE_NAMES[documentType]),
            data: [
                sections: sections,
                requirements: requirements
            ]
        ]

        def uri = this.createDocument(documentType, null, data_, [:], null, null, this.getWatermarkText(documentType))
        this.notifyJiraTrackingIssue(documentType, "A new ${DOCUMENT_TYPE_NAMES[documentType]} has been generated and is available at: ${uri}.")
        return uri
    }

    String createDTP(Map repo = null, Map data = null) {
        def documentType = DocumentType.DTP as String

        def watermarkText
        def sections = this.jiraUseCase.getDocumentChapterData(documentType)
        if (!sections) {
            sections = this.levaFiles.getDocumentChapterData(documentType)
        } else {
            watermarkText = this.getWatermarkText(documentType)
        }

        def data_ = [
            metadata: this.getDocumentMetadata(this.DOCUMENT_TYPE_NAMES[documentType]),
            data: [
                repositories: this.project.repositories.collect { repo_ ->
                    [
                        id: repo_.id,
                        description: repo_.metadata.description,
                        url: repo_.url
                    ]
                },
                sections: sections,
                tests: this.project.getAutomatedTestsTypeUnit().collectEntries { testIssue ->
                    def techSpecsWithSoftwareDesignSpec = testIssue.getTechnicalSpecifications().findAll{ it.softwareDesignSpec }.collect{ it.key }

                    [
                        testIssue.key,
                        [
                            key: testIssue.key,
                            description: testIssue.description ?: "",
                            systemRequirement: testIssue.requirements ? testIssue.requirements.join(", ") : "N/A",
                            softwareDesignSpec: techSpecsWithSoftwareDesignSpec ? techSpecsWithSoftwareDesignSpec.join(", ") : "N/A"
                        ]
                    ]
                }
            ]
        ]

        def uri = this.createDocument(documentType, null, data_, [:], null, null, watermarkText)
        this.notifyJiraTrackingIssue(documentType, "A new ${DOCUMENT_TYPE_NAMES[documentType]} has been generated and is available at: ${uri}.")
        return uri
    }

    String createDTR(Map repo, Map data) {
        def documentType = DocumentType.DTR as String

        data = data.tests.unit

        def watermarkText
        def sections = this.jiraUseCase.getDocumentChapterData(documentType)
        if (!sections) {
            sections = this.levaFiles.getDocumentChapterData(documentType)
        } else {
            watermarkText = this.getWatermarkText(documentType)
        }

        def testIssues = this.project.getAutomatedTestsTypeUnit("Technology-${repo.id}")

        def matchedHandler = { result ->
            result.each { testIssue, testCase ->
                testIssue.isSuccess = !(testCase.error || testCase.failure || testCase.skipped)
                testIssue.isMissing = false
            }
        }

        def unmatchedHandler = { result ->
            result.each { testIssue ->
                testIssue.isSuccess = false
                testIssue.isMissing = true
            }
        }

        this.jiraUseCase.matchTestIssuesAgainstTestResults(testIssues, data?.testResults ?: [:], matchedHandler, unmatchedHandler)

        def discrepancies = this.computeTestDiscrepancies("Development Tests", testIssues)

        def data_ = [
                metadata: this.getDocumentMetadata(this.DOCUMENT_TYPE_NAMES[documentType], repo),
                data    : [
                        repo         : repo,
                        sections     : sections,
                        tests        : testIssues.collectEntries { testIssue ->
                            [
                                    testIssue.key,
                                    [
                                            key              : testIssue.key,
                                            description      : testIssue.description ?: "",
                                            // TODO: change template from isRelatedTo to systemRequirement
                                            systemRequirement: testIssue.requirements.join(", "),
                                            success          : testIssue.isSuccess ? "Y" : "N",
                                            remarks          : testIssue.isMissing ? "not executed" : ""
                                    ]
                            ]
                        },
                        testfiles    : data.testReportFiles.collect { file ->
                            [name: file.getName(), path: file.getPath()]
                        },
                        testsuites   : data.testResults,
                        discrepancies: discrepancies.discrepancies,
                        conclusion   : [
                                summary  : discrepancies.conclusion.summary,
                                statement: discrepancies.conclusion.statement
                        ]
                ]
        ]

        def files = data.testReportFiles.collectEntries { file ->
            ["raw/${file.getName()}", file.getBytes()]
        }

        def modifier = { document ->
            repo.data.documents[documentType] = document
            return document
        }

        def uri = this.createDocument(documentType, repo, data_, files, modifier, null, watermarkText)
        this.notifyJiraTrackingIssue(documentType, "A new ${DOCUMENT_TYPE_NAMES[documentType]} has been generated and is available at: ${uri}.")
        return uri
    }

    String createCFTP(Map repo = null, Map data = null) {
        def documentType = DocumentType.CFTP as String

        def sections = this.jiraUseCase.getDocumentChapterData(documentType)
        if (!sections) {
            throw new RuntimeException("Error: unable to create ${documentType}. Could not obtain document chapter data from Jira.")
        }

        def acceptanceTestIssues = this.project.getAutomatedTestsTypeAcceptance()
        def integrationTestIssues = this.project.getAutomatedTestsTypeIntegration()

        def data_ = [
                metadata: this.getDocumentMetadata(this.DOCUMENT_TYPE_NAMES[documentType]),
                data    : [
                        sections        : sections,
                        acceptanceTests : acceptanceTestIssues.collectEntries { testIssue ->
                            [
                                    testIssue.key,
                                    [
                                            key        : testIssue.key,
                                            description: testIssue.description ?: "",
                                            ur_key     : testIssue.requirements ? testIssue.requirements.join(", ") : "N/A",
                                            risk_key   : testIssue.risks ? testIssue.risks.join(", ") : "N/A"
                                    ]
                            ]
                        },
                        integrationTests: integrationTestIssues.collectEntries { testIssue ->
                            [
                                    testIssue.key,
                                    [
                                            key        : testIssue.key,
                                            description: testIssue.description ?: "",
                                            ur_key     : testIssue.requirements ? testIssue.requirements.join(", ") : "N/A",
                                            risk_key   : testIssue.risks ? testIssue.risks.join(", ") : "N/A"
                                    ]
                            ]
                        }
                ]
        ]

        def uri = this.createDocument(documentType, null, data_, [:], null, null, this.getWatermarkText(documentType))
        this.notifyJiraTrackingIssue(documentType, "A new ${DOCUMENT_TYPE_NAMES[documentType]} has been generated and is available at: ${uri}.")
        return uri
    }

    String createCFTR(Map repo, Map data) {
        def documentType = DocumentType.CFTR as String

        def acceptanceTestData = data.tests.acceptance
        def integrationTestData = data.tests.integration

        // Obtain the total number of test in report
        def numberAcceptanceTest = getNumberOfTest(acceptanceTestData.testResults)
        def numberIntegrationTest = getNumberOfTest(integrationTestData.testResults)

        def sections = this.jiraUseCase.getDocumentChapterData(documentType)
        if (!sections) {
            throw new RuntimeException("Error: unable to create ${documentType}. Could not obtain document chapter data from Jira.")
        }

        def matchedHandler = { result ->
            result.each { testIssue, testCase ->
                testIssue.isSuccess = !(testCase.error || testCase.failure || testCase.skipped)
                testIssue.isMissing = false
                testIssue.timestamp = testCase.timestamp
            }
        }

        def unmatchedHandler = { result ->
            result.each { testIssue ->
                testIssue.isSuccess = false
                testIssue.isMissing = true
            }
        }

        def acceptanceTestIssues = this.project.getAutomatedTestsTypeAcceptance()
        this.jiraUseCase.matchTestIssuesAgainstTestResults(acceptanceTestIssues, acceptanceTestData?.testResults ?: [:], matchedHandler, unmatchedHandler)
        acceptanceTestIssues = SortUtil.sortIssuesByProperties(acceptanceTestIssues ?: [], ["key"])

        def integrationTestIssues = this.project.getAutomatedTestsTypeIntegration()
        this.jiraUseCase.matchTestIssuesAgainstTestResults(integrationTestIssues, integrationTestData?.testResults ?: [:], matchedHandler, unmatchedHandler)
        integrationTestIssues = SortUtil.sortIssuesByProperties(integrationTestIssues ?: [], ["key"])

        def discrepancies = this.computeTestDiscrepancies("Functional and Requirements Tests", (acceptanceTestIssues + integrationTestIssues))

        def data_ = [
                metadata: this.getDocumentMetadata(this.DOCUMENT_TYPE_NAMES[documentType]),
                data    : [
                        sections        : sections,
                        additionalAcceptanceTests: numberAcceptanceTest - acceptanceTestIssues.count { !it.isMissing }, 
                        additionalIntegrationTests: numberIntegrationTest - integrationTestIssues.count { !it.isMissing }, 
                        testfiles       : (acceptanceTestData + integrationTestData).testReportFiles.collect { file ->
                            [name: file.getName(), path: file.getPath()]
                        },
                        conclusion      : [
                            summary  : discrepancies.conclusion.summary,
                            statement: discrepancies.conclusion.statement
                        ]
                ]
        ]

        if (!acceptanceTestIssues.isEmpty()) {
            data_.data.acceptanceTests = acceptanceTestIssues.collectEntries { testIssue ->
                [
                    testIssue.key,
                    [
                            key        : testIssue.key,
                            datetime   : testIssue.timestamp ? testIssue.timestamp.replaceAll("T", "</br>") : "N/A",
                            description: testIssue.description ?: "",
                            remarks    : testIssue.isMissing ? "not executed" : "",
                            risk_key   : testIssue.risks ? testIssue.risks.join(", ") : "N/A",
                            success    : testIssue.isSuccess ? "Y" : "N",
                            ur_key     : testIssue.requirements ? testIssue.requirements.join(", ") : "N/A"
                    ]
                ]
            }
        }

        if (!integrationTestIssues.isEmpty()) {
            data_.data.integrationTests = integrationTestIssues.collectEntries { testIssue ->
                [
                    testIssue.key,
                    [
                            key        : testIssue.key,
                            datetime   : testIssue.timestamp ? testIssue.timestamp.replaceAll("T", "</br>") : "N/A",
                            description: testIssue.description ?: "",
                            remarks    : testIssue.isMissing ? "not executed" : "",
                            risk_key   : testIssue.risks ? testIssue.risks.join(", ") : "N/A",
                            success    : testIssue.isSuccess ? "Y" : "N",
                            ur_key     : testIssue.requirements ? testIssue.requirements.join(", ") : "N/A"
                    ]
                ]
            }
        }

        def files = (acceptanceTestData + integrationTestData).testReportFiles.collectEntries { file ->
            ["raw/${file.getName()}", file.getBytes()]
        }

        def uri = this.createDocument(documentType, null, data_, files, null, null, this.getWatermarkText(documentType))
        this.notifyJiraTrackingIssue(documentType, "A new ${DOCUMENT_TYPE_NAMES[documentType]} has been generated and is available at: ${uri}.")
        return uri
    }

    String createIVP(Map repo = null, Map data = null) {
        def documentType = DocumentType.IVP as String

        def watermarkText
        def sections = this.jiraUseCase.getDocumentChapterData(documentType)
        if (!sections) {
            throw new RuntimeException("Error: unable to create ${documentType}. Could not obtain document chapter data from Jira.")
        } else {
            watermarkText = this.getWatermarkText(documentType)
        }

        def installationTestIssues = this.project.getAutomatedTestsTypeInstallation()

        // TODO factor this into a method of its own
        def testsGroupedByRepoType = installationTestIssues.collect { test ->
            def components = test.getResolvedComponents()
            test.repoTypes = components.collect { component ->
                def normalizedComponentName = component.name.replaceAll("Technology-", "")
                def repository = project.repositories.find { repository ->
                    [repository.id, repository.name].contains(normalizedComponentName)
                }

                if (!repository) {
                    throw new IllegalArgumentException("Error: unable to create ${documentType}. Could not find a repository definition with id or name equal to '${normalizedComponentName}' for Jira component '${component.name}' in project '${this.project.id}'.")
                }

                return repository.type
            } as Set

            return test
        }.groupBy { it.repoTypes }

        def testsOfRepoTypeOds = []
        def testsOfRepoTypeOdsService = []
        testsGroupedByRepoType.each { repoTypes, tests ->
            if (repoTypes.contains(MROPipelineUtil.PipelineConfig.REPO_TYPE_ODS_CODE)) {
                testsOfRepoTypeOds.addAll(tests)
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
                        tests          : installationTestIssues.collectEntries { testIssue ->
                            [
                                    testIssue.key,
                                    [
                                            key     : testIssue.key,
                                            summary : testIssue.name,
                                            techSpec: testIssue.techSpecs.join(", ")
                                    ]
                            ]
                        },
                        testsOdsService: testsOfRepoTypeOdsService,
                        testsOds       : testsOfRepoTypeOds
                ]
        ]

        def uri = this.createDocument(documentType, null, data_, [:], null, null, watermarkText)
        this.notifyJiraTrackingIssue(documentType, "A new ${DOCUMENT_TYPE_NAMES[documentType]} has been generated and is available at: ${uri}.")
        return uri
    }

    String createIVR(Map repo, Map data) {
        def documentType = DocumentType.IVR as String

        data = data.tests.installation

        def watermarkText
        def sections = this.jiraUseCase.getDocumentChapterData(documentType)
        if (!sections) {
            sections = this.levaFiles.getDocumentChapterData(documentType)
        } else {
            watermarkText = this.getWatermarkText(documentType)
        }

        def testIssues = this.project.getAutomatedTestsTypeInstallation()

        def matchedHandler = { result ->
            result.each { testIssue, testCase ->
                testIssue.isSuccess = !(testCase.error || testCase.failure || testCase.skipped)
                testIssue.isMissing = false
            }
        }

        def unmatchedHandler = { result ->
            result.each { testIssue ->
                testIssue.isSuccess = false
                testIssue.isMissing = true
            }
        }

        this.jiraUseCase.matchTestIssuesAgainstTestResults(testIssues, data?.testResults ?: [:], matchedHandler, unmatchedHandler)

        def data_ = [
                metadata: this.getDocumentMetadata(this.DOCUMENT_TYPE_NAMES[documentType]),
                data    : [
                        // TODO: change data.project.repositories to data.repositories in template
                        repositories: this.project.repositories,
                        sections    : sections,
                        tests       : testIssues.collectEntries { testIssue ->
                            [
                                    testIssue.key,
                                    [
                                            key        : testIssue.key,
                                            description: testIssue.description ?: "",
                                            remarks    : testIssue.isMissing ? "not executed" : "",
                                            success    : testIssue.isSuccess ? "Y" : "N",
                                            summary    : testIssue.name,
                                            // TODO: change template from isRelatedTo to techSpec
                                            techSpec   : testIssue.techSpecs.join(", ")
                                    ]
                            ]
                        },
                        testfiles   : data.testReportFiles.collect { file ->
                            [name: file.getName(), path: file.getPath()]
                        }
                ]
        ]

        def files = data.testReportFiles.collectEntries { file ->
            ["raw/${file.getName()}", file.getBytes()]
        }

        def uri = this.createDocument(documentType, null, data_, files, null, null, watermarkText)
        this.notifyJiraTrackingIssue(documentType, "A new ${DOCUMENT_TYPE_NAMES[documentType]} has been generated and is available at: ${uri}.")
        return uri
    }

    // TODO deleteme when implementing SSDS
    //String createSCP(Map repo = null, Map data = null) {
    //    def documentType = DocumentType.SCP as String

    //    def watermarkText
    //    def sections = this.jiraUseCase.getDocumentChapterData(documentType)
    //    if (!sections) {
    //        sections = this.levaFiles.getDocumentChapterData(documentType)
    //    } else {
    //        watermarkText = this.getWatermarkText(documentType)
    //    }

    //    def data_ = [
    //        metadata: this.getDocumentMetadata(this.DOCUMENT_TYPE_NAMES[documentType]),
    //        data: [
    //            // TODO: change data.project.repositories to data.repositories in template
    //            repositories: this.project.repositories,
    //            sections: sections
    //        ]
    //    ]

    //    def uri = this.createDocument(documentType, null, data_, [:], null, null, watermarkText)
    //    this.notifyJiraTrackingIssue(documentType, "A new ${DOCUMENT_TYPE_NAMES[documentType]} has been generated and is available at: ${uri}.")
    //    return uri
    //}

    //String createSCR(Map repo, Map data = null) {
    //    def documentType = DocumentType.SCR as String

    //    def sqReportsPath = "${PipelineUtil.SONARQUBE_BASE_DIR}/${repo.id}"
    //    def sqReportsStashName = "scrr-report-${repo.id}-${this.steps.env.BUILD_ID}"

    //    // Unstash SonarQube reports into path
    //    def hasStashedSonarQubeReports = this.jenkins.unstashFilesIntoPath(sqReportsStashName, "${this.steps.env.WORKSPACE}/${sqReportsPath}", "SonarQube Report")
    //    if (!hasStashedSonarQubeReports) {
    //        throw new RuntimeException("Error: unable to unstash SonarQube reports for repo '${repo.id}' from stash '${sqReportsStashName}'.")
    //    }

    //    // Load SonarQube report files from path
    //    def sqReportFiles = this.sq.loadReportsFromPath("${this.steps.env.WORKSPACE}/${sqReportsPath}")
    //    if (sqReportFiles.isEmpty()) {
    //        throw new RuntimeException("Error: unable to load SonarQube reports for repo '${repo.id}' from path '${this.steps.env.WORKSPACE}/${sqReportsPath}'.")
    //    }

    //    def watermarkText
    //    def sections = this.jiraUseCase.getDocumentChapterData(documentType)
    //    if (!sections) {
    //        sections = this.levaFiles.getDocumentChapterData(documentType)
    //    } else {
    //        watermarkText = this.getWatermarkText(documentType)
    //    }

    //    def data_ = [
    //        metadata: this.getDocumentMetadata(this.DOCUMENT_TYPE_NAMES[documentType], repo),
    //        data: [
    //            sections: sections
    //        ]
    //    ]

    //    def files = [:]
    //    /*
    //    // TODO: conversion of a SonarQube report results in an ambiguous NPE.
    //    // Research did not reveal any meaningful results. Further, Apache POI
    //    // depends on Commons Compress, but unfortunately Jenkins puts an older
    //    // version onto the classpath which results in an error. Therefore, iff
    //    // the NPE can be fixed, this code would need to run outside of Jenkins,
    //    // such as the DocGen service.

    //    def sonarQubePDFDoc = this.pdf.convertFromWordDoc(sonarQubeWordDoc)
    //    modifier = { document ->
    //        // Merge the current document with the SonarQube report
    //        return this.pdf.merge([ document, sonarQubePDFDoc ])
    //    }

    //    // As our plan B below, we instead add the SonarQube report into the
    //    // SCR's .zip archive.
    //    */
    //    def name = this.getDocumentBasename("SCRR", this.project.buildParams.version, this.steps.env.BUILD_ID, repo)
    //    def sqReportFile = sqReportFiles.first()
    //    files << [ "${name}.${FilenameUtils.getExtension(sqReportFile.getName())}": sqReportFile.getBytes() ]

    //    def modifier = { document ->
    //        repo.data.documents[documentType] = document
    //        return document
    //    }

    //    return this.createDocument(documentType, repo, data_, files, modifier, null, watermarkText)
    //}

    String createSSDS(Map repo = null, Map data = null) {
        def documentType = DocumentType.SSDS as String

        def sections = this.jiraUseCase.getDocumentChapterData(documentType)
        if (!sections) {
            throw new RuntimeException("Error: unable to create ${documentType}. Could not obtain document chapter data from Jira.")
        }

        def componentsMetadata = this.computeComponentMetadata(documentType).collect { it.value }
        def systemDesignSpecifications = this.project.getTechnicalSpecifications().collect { techSpec ->
            [
                    key        : techSpec.key,
                    req_key    : techSpec.requirements.join(", "),
                    description: techSpec.description
                    // TODO: prefix properties in sec5s1 with .metadata in template
                    //metadata: techSpec.components.collect { componentKey ->
                    //    return componentsMetadata[componentKey]
                    //}//.join(", ")
            ]
        }

        if (!sections."sec3s1") sections."sec3s1" = [:]
        sections."sec3s1".specifications = SortUtil.sortIssuesByProperties(systemDesignSpecifications, ["req_key", "key"])

        if (!sections."sec5s1") sections."sec5s1" = [:]
        // TODO change this to .components here and in HTML template
        sections."sec5s1".specifications = SortUtil.sortIssuesByProperties(componentsMetadata, ["key"])

        def data_ = [
                metadata: this.getDocumentMetadata(this.DOCUMENT_TYPE_NAMES[documentType], repo),
                data    : [
                        sections: sections
                ]
        ]

        def uri = this.createDocument(documentType, null, data_, [:], null, null, this.getWatermarkText(documentType))
        this.notifyJiraTrackingIssue(documentType, "A new ${DOCUMENT_TYPE_NAMES[documentType]} has been generated and is available at: ${uri}.")
        return uri
    }

    String createTIP(Map repo = null, Map data = null) {
        def documentType = DocumentType.TIP as String

        def watermarkText
        def sections = this.jiraUseCase.getDocumentChapterData(documentType)
        if (!sections) {
            sections = this.levaFiles.getDocumentChapterData(documentType)
        } else {
            watermarkText = this.getWatermarkText(documentType)
        }

        def data_ = [
                metadata: this.getDocumentMetadata(this.DOCUMENT_TYPE_NAMES[documentType]),
                data    : [
                        // TODO: change this.project.id to project_key in template
                        project_key : this.project.key,
                        // TODO: change data.project.repositories and data.repos to data.repositories in template
                        repositories: this.project.repositories,
                        sections    : sections
                ]
        ]

        def uri = this.createDocument(documentType, null, data_, [:], null, null, watermarkText)
        this.notifyJiraTrackingIssue(documentType, "A new ${DOCUMENT_TYPE_NAMES[documentType]} has been generated and is available at: ${uri}.")
        return uri
    }

    String createTIR(Map repo, Map data = null) {
        def documentType = DocumentType.TIR as String

        def pods = this.os.getPodDataForComponent(repo.id)

        def watermarkText
        def sections = this.jiraUseCase.getDocumentChapterData(documentType)
        if (!sections) {
            sections = this.levaFiles.getDocumentChapterData(documentType)
        } else {
            watermarkText = this.getWatermarkText(documentType)
        }

        def data_ = [
                metadata     : this.getDocumentMetadata(this.DOCUMENT_TYPE_NAMES[documentType], repo),
                openShiftData: [
                        ocpBuildId          : repo?.data.odsBuildArtifacts?."OCP Build Id" ?: "N/A",
                        ocpDockerImage      : repo?.data.odsBuildArtifacts?."OCP Docker image" ?: "N/A",
                        ocpDeploymentId     : repo?.data.odsBuildArtifacts?."OCP Deployment Id" ?: "N/A",
                        podName             : pods?.items[0]?.metadata?.name ?: "N/A",
                        podNamespace        : pods?.items[0]?.metadata?.namespace ?: "N/A",
                        podCreationTimestamp: pods?.items[0]?.metadata?.creationTimestamp ?: "N/A",
                        podEnvironment      : pods?.items[0]?.metadata?.labels?.env ?: "N/A",
                        podNode             : pods?.items[0]?.spec?.nodeName ?: "N/A",
                        podIp               : pods?.items[0]?.status?.podIP ?: "N/A",
                        podStatus           : pods?.items[0]?.status?.phase ?: "N/A"
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

        return this.createDocument(documentType, repo, data_, [:], modifier, null, watermarkText)
    }

    String createOverallDTR(Map repo = null, Map data = null) {
        def documentTypeName = DOCUMENT_TYPE_NAMES[DocumentType.OVERALL_DTR as String]
        def metadata = this.getDocumentMetadata(documentTypeName)

        def documentType = DocumentType.DTR as String

        def uri = this.createOverallDocument("Overall-Cover", documentType, metadata, null, this.getWatermarkText(documentType))
        this.notifyJiraTrackingIssue(documentType, "A new ${documentTypeName} has been generated and is available at: ${uri}.")
        return uri
    }

    String createOverallSSDS(Map repo = null, Map data = null) {
        def documentTypeName = DOCUMENT_TYPE_NAMES[DocumentType.OVERALL_SSDS as String]
        def metadata = this.getDocumentMetadata(documentTypeName)

        def documentType = DocumentType.SSDS as String

        def uri = this.createOverallDocument("Overall-Cover", documentType, metadata, null, this.getWatermarkText(documentType))
        this.notifyJiraTrackingIssue(documentType, "A new ${documentTypeName} has been generated and is available at: ${uri}.")
        return uri
    }

    String createOverallTIR(Map repo = null, Map data = null) {
        def documentTypeName = DOCUMENT_TYPE_NAMES[DocumentType.OVERALL_TIR as String]
        def metadata = this.getDocumentMetadata(documentTypeName)

        def documentType = DocumentType.TIR as String

        def visitor = { data_ ->
            // Append another section for the Jenkins build log
            data_.sections << [
                    heading: "Jenkins Build Log"
            ]

            // Add Jenkins build log data
            data_.jenkinsData = [
                    log: this.jenkins.getCurrentBuildLogAsText()
            ]
        }

        def uri = this.createOverallDocument("Overall-TIR-Cover", documentType, metadata, visitor, this.getWatermarkText(documentType))
        this.notifyJiraTrackingIssue(documentType, "A new ${documentTypeName} has been generated and is available at: ${uri}.")
        return uri
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
                jenkins       : [
                        buildNumber: this.steps.env.BUILD_NUMBER,
                        buildUrl   : this.steps.env.BUILD_URL,
                        jobName    : this.steps.env.JOB_NAME
                ]
        ]

        metadata.header = ["${documentTypeName}, Config Item: ${metadata.buildParameter.configItem}", "Doc ID/Version: see auto-generated cover page"]

        return metadata
    }

    private String getJiraTrackingIssueLabelForDocumentType(String documentType) {
        def environment = this.project.buildParams.targetEnvironmentToken

        def label = LeVADocumentScheduler.ENVIRONMENT_TYPE[environment].get(documentType)
        if (!label && environment.equals('D')) {
            label = documentType
        }

        return "LeVA_Doc:${label}"
    }

    List<String> getSupportedDocuments() {
        return DocumentType.values().collect { it as String }
    }

    protected String getWatermarkText(String documentType) {
        def environment = this.project.buildParams.targetEnvironmentToken

        // The watermark only applies in DEV environment (for documents not to be delivered from that environment)
        if (environment.equals('D') && !LeVADocumentScheduler.ENVIRONMENT_TYPE['D'].containsKey(documentType)) {
            return this.DEVELOPER_PREVIEW_WATERMARK
        }

        return null
    }

    void notifyJiraTrackingIssue(String documentType, String message) {
        if (!this.jiraUseCase) return
        if (!this.jiraUseCase.jira) return

        def jiraDocumentLabel = this.getJiraTrackingIssueLabelForDocumentType(documentType)

        def jqlQuery = [jql: "project = ${project.key} AND issuetype = '${IssueTypes.LEVA_DOCUMENTATION}' AND labels = ${jiraDocumentLabel}"]

        // Search for the Jira issue associated with the document
        def jiraIssues = this.jiraUseCase.jira.getIssuesForJQLQuery(jqlQuery)
        if (jiraIssues.size() != 1) {
            throw new RuntimeException("Error: Jira query returned ${jiraIssues.size()} issues: '${jqlQuery}'.")
        }

        // Add a comment to the Jira issue with a link to the report
        this.jiraUseCase.jira.appendCommentToIssue(jiraIssues.first().key, message)
    }

    protected Integer getNumberOfTest(Map testResults) {
        def count = 0
        testResults.testsuites.each { testSuite ->
            count += testSuite.testcases.size()
        }

        return count
    }
}
