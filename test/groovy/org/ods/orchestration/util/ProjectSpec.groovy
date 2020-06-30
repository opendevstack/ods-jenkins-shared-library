package org.ods.orchestration.util

import java.nio.file.Files

import org.apache.http.client.utils.URIBuilder
import org.ods.services.GitService
import org.ods.orchestration.service.*
import org.ods.orchestration.usecase.*
import org.ods.util.IPipelineSteps
import org.ods.util.Logger
import org.yaml.snakeyaml.Yaml

import static util.FixtureHelper.*

import util.*

class ProjectSpec extends SpecHelper {

    GitService git
    IPipelineSteps steps
    JiraUseCase jiraUseCase
    Logger logger
    Project project
    File metadataFile

    def createProject(Map<String, Closure> mixins = [:]) {
        Project project = Spy(constructorArgs: [steps, logger])

        if (mixins.containsKey("getGitURLFromPath")) {
            project.getGitURLFromPath(*_) >> { mixins["getGitURLFromPath"]() }
        } else {
            project.getGitURLFromPath(*_) >> { return new URIBuilder("https://github.com/my-org/my-pipeline-repo.git").build() }
        }

        if (mixins.containsKey("loadJiraData")) {
            project.loadJiraData(*_) >> { mixins["loadJiraData"]() }
        } else {
            project.loadJiraData(*_) >> { return createProjectJiraData() }
        }

        if (mixins.containsKey("loadJiraDataBugs")) {
            project.loadJiraDataBugs(*_) >> { mixins["loadJiraDataBugs"]() }
        } else {
            project.loadJiraDataBugs(*_) >> { return createProjectJiraDataBugs() }
        }

        if (mixins.containsKey("loadJiraDataDocs")) {
            project.loadJiraDataDocs(*_) >> { mixins["loadJiraDataDocs"]() }
        } else {
            project.loadJiraDataDocs(*_) >> { return createProjectJiraDataDocs() }
        }

        if (mixins.containsKey("loadJiraDataIssueTypes")) {
            project.loadJiraDataIssueTypes(*_) >> { mixins["loadJiraDataIssueTypes"]() }
        } else {
            project.loadJiraDataIssueTypes(*_) >> { return createProjectJiraDataIssueTypes() }
        }

        return project
    }

    def setup() {
        git = Mock(GitService)
        jiraUseCase = Mock(JiraUseCase)
        steps = Spy(util.PipelineSteps)
        logger = Mock(Logger)
        steps.env.WORKSPACE = ""

        metadataFile = new FixtureHelper().getResource("/project-metadata.yml")
        Project.METADATA_FILE_NAME = metadataFile.getAbsolutePath()

        project = createProject().init().load(git, jiraUseCase)
    }

    def "get build environment for DEBUG"() {
        when:
        def result = Project.getBuildEnvironment(steps)

        then:
        result.find { it == "DEBUG=false" }

        when:
        result = Project.getBuildEnvironment(steps, true)

        then:
        result.find { it == "DEBUG=true" }

        when:
        result = Project.getBuildEnvironment(steps, false)

        then:
        result.find { it == "DEBUG=false" }
    }

    def "get build environment for MULTI_REPO_BUILD"() {
        when:
        def result = Project.getBuildEnvironment(steps)

        then:
        result.find { it == "MULTI_REPO_BUILD=true" }
    }

    def "get build environment for MULTI_REPO_ENV"() {
        when:
        steps.env.environment = null
        def result = Project.getBuildEnvironment(steps)

        then:
        result.find { it == "MULTI_REPO_ENV=dev" }

        when:
        steps.env.environment = ""
        result = Project.getBuildEnvironment(steps)

        then:
        result.find { it == "MULTI_REPO_ENV=dev" }

        when:
        steps.env.environment = "qa"
        result = Project.getBuildEnvironment(steps)

        then:
        result.find { it == "MULTI_REPO_ENV=test" }
    }

    def "get build environment for MULTI_REPO_ENV_TOKEN"() {
        when:
        steps.env.environment = "dev"
        def result = Project.getBuildEnvironment(steps)

        then:
        result.find { it == "MULTI_REPO_ENV_TOKEN=D" }

        when:
        steps.env.environment = "qa"
        result = Project.getBuildEnvironment(steps)

        then:
        result.find { it == "MULTI_REPO_ENV_TOKEN=Q" }

        when:
        steps.env.environment = "prod"
        result = Project.getBuildEnvironment(steps)

        then:
        result.find { it == "MULTI_REPO_ENV_TOKEN=P" }
    }

    def "get build environment for RELEASE_PARAM_CHANGE_ID"() {
        when:
        steps.env.environment = "dev"
        steps.env.version = "0.1"
        def result = Project.getBuildEnvironment(steps)

        then:
        result.find { it == "RELEASE_PARAM_CHANGE_ID=UNDEFINED" }

        when:
        steps.env.changeId = ""
        steps.env.environment = "dev"
        steps.env.version = "0.1"
        result = Project.getBuildEnvironment(steps)

        then:
        result.find { it == "RELEASE_PARAM_CHANGE_ID=UNDEFINED" }

        when:
        steps.env.changeId = "myId"
        steps.env.environment = "dev"
        steps.env.version = "0.1"
        result = Project.getBuildEnvironment(steps)

        then:
        result.find { it == "RELEASE_PARAM_CHANGE_ID=myId" }
    }

    def "get build environment for RELEASE_PARAM_CHANGE_DESC"() {
        when:
        steps.env.changeDescription = null
        def result = Project.getBuildEnvironment(steps)

        then:
        result.find { it == "RELEASE_PARAM_CHANGE_DESC=UNDEFINED" }

        when:
        steps.env.changeDescription = ""
        result = Project.getBuildEnvironment(steps)

        then:
        result.find { it == "RELEASE_PARAM_CHANGE_DESC=UNDEFINED" }

        when:
        steps.env.changeDescription = "myDescription"
        result = Project.getBuildEnvironment(steps)

        then:
        result.find { it == "RELEASE_PARAM_CHANGE_DESC=myDescription" }
    }

    def "get build environment for RELEASE_PARAM_CONFIG_ITEM"() {
        when:
        steps.env.configItem = null
        def result = Project.getBuildEnvironment(steps)

        then:
        result.find { it == "RELEASE_PARAM_CONFIG_ITEM=UNDEFINED" }

        when:
        steps.env.configItem = ""
        result = Project.getBuildEnvironment(steps)

        then:
        result.find { it == "RELEASE_PARAM_CONFIG_ITEM=UNDEFINED" }

        when:
        steps.env.configItem = "myItem"
        result = Project.getBuildEnvironment(steps)

        then:
        result.find { it == "RELEASE_PARAM_CONFIG_ITEM=myItem" }
    }

    def "get build environment for RELEASE_PARAM_VERSION"() {
        when:
        steps.env.version = null
        def result = Project.getBuildEnvironment(steps)

        then:
        result.find { it == "RELEASE_PARAM_VERSION=WIP" }

        when:
        steps.env.version = ""
        result = Project.getBuildEnvironment(steps)

        then:
        result.find { it == "RELEASE_PARAM_VERSION=WIP" }

        when:
        steps.env.version = "0.1"
        result = Project.getBuildEnvironment(steps)

        then:
        result.find { it == "RELEASE_PARAM_VERSION=0.1" }
    }

    def "get versioned dev ens"() {
        when:
        def result = new Project(steps, logger).getVersionedDevEnvsEnabled()

        then:
        result == false

        when:
        result = new Project(steps, logger, [versionedDevEnvs: false]).getVersionedDevEnvsEnabled()

        then:
        result == false

        when:
        result = new Project(steps, logger, [versionedDevEnvs: true]).getVersionedDevEnvsEnabled()

        then:
        result == true
    }

    def "get concrete environments"() {
        expect:
        Project.getConcreteEnvironment(environment, version, versionedDevEnvsEnabled) == result

        where:
        environment | version | versionedDevEnvsEnabled || result
        "dev"       | "WIP"   | true                    || "dev"
        "dev"       | "tiger" | false                   || "dev"
        "dev"       | "tiger" | true                    || "dev-tiger"
        "dev"       | "Tiger" | true                    || "dev-tiger"
        "dev"       | "1.0"   | true                    || "dev-1-0"
        "dev"       | "1/0"   | true                    || "dev-1-0"
        "dev"       | "ähm"   | true                    || "dev--hm"
        "qa"        | "lion"  | true                    || "test"
        "qa"        | "lion"  | false                   || "test"
        "prod"      | "lion"  | true                    || "prod"
        "prod"      | "lion"  | false                   || "prod"
    }

    def "get environment params"() {
        steps.readFile(file: '/path/to/dev.env') >> content

        expect:
        project.getEnvironmentParams('/path/to/dev.env') == result

        where:
        content                               || result
        "FOO=bar"                             || ['FOO': 'bar']
        "FOO=bar\nBAZ=qux"                    || ['FOO': 'bar', 'BAZ': 'qux']
        "FOO=bar\n# BAZ=qux\nABC=def"         || ['FOO': 'bar', 'ABC': 'def']
        "FOO= bar \n # BAZ=qux\n\n\n ABC=def" || ['FOO': 'bar', 'ABC': 'def']
    }

    def "get environment params file"() {
        steps.env.environment = 'dev'
        steps.env.WORKSPACE = '/path/to/workspace'
        steps.fileExists('/path/to/workspace/dev.env') >> exists

        expect:
        project.getEnvironmentParamsFile() == result

        where:
        exists  || result
        true    || '/path/to/workspace/dev.env'
        false   || ''
    }

    def "target cluster is not external when no API URL is configured"() {
        project.setOpenShiftData('https://api.example.openshift.com:443')

        expect:
        project.getTargetClusterIsExternal() == false
    }

    def "target cluster can be external when an API URL is configured"() {
        given:
        steps.env.environment = environment
        def metadataFile = Files.createTempFile("metadata", ".yml").toFile()
        Project.METADATA_FILE_NAME = metadataFile.getAbsolutePath()

        metadataFile.text = """
            id: myId
            name: myName
            repositories:
              - id: A
                name: A
                url: http://git.com
            environments:
              prod:
                apiUrl: ${configuredProdApiUrl}
        """

        when:
        project.init()
        project.setOpenShiftData('https://api.example.openshift.com:443')

        then:
        project.getTargetClusterIsExternal() == result

        where:
        environment | configuredProdApiUrl || result
        'dev'       | 'https://api.other.openshift.com'   || false
        'prod'      | 'https://api.other.openshift.com'   || true
        'prod'      | 'https://api.example.openshift.com' || false
    }

    def "get capabilities"() {
        given:
        def metadataFile = Files.createTempFile("metadata", ".yml").toFile()
        Project.METADATA_FILE_NAME = metadataFile.getAbsolutePath()

        metadataFile.text = """
            id: myId
            name: myName
            repositories:
              - id: A
                name: A
                url: http://git.com
            capabilities:
              - Zephyr
              - LeVADocs:
                  GAMPCategory: 5
        """

        when:
        project.init()

        then:
        project.getCapabilities() == [
            "Zephyr",
            [
                "LeVADocs": [
                    GAMPCategory: 5,
                    templatesVersion: "1.0"
                ]
            ]
        ]

        cleanup:
        metadataFile.delete()
    }

    def "get capability"() {
        given:
        def metadataFile = Files.createTempFile("metadata", ".yml").toFile()
        Project.METADATA_FILE_NAME = metadataFile.getAbsolutePath()

        metadataFile.text = """
            id: myId
            name: myName
            repositories:
              - id: A
                name: A
                url: http://git.com
            capabilities:
              - Zephyr
              - LeVADocs:
                  GAMPCategory: 5
        """

        when:
        project.init()

        then:
        project.getCapability("Zephyr")

        then:
        project.getCapability("LeVADocs") == [
            GAMPCategory: 5,
            templatesVersion: "1.0"
        ]

        when:
        project.getCapability("LeVADocs").GAMPCategory = 3

        then:
        project.getCapability("LeVADocs").GAMPCategory == 3

        cleanup:
        metadataFile.delete()
    }

    def "has capability"() {
        given:
        def metadataFile = Files.createTempFile("metadata", ".yml").toFile()
        Project.METADATA_FILE_NAME = metadataFile.getAbsolutePath()

        metadataFile.text = """
            id: myId
            name: myName
            repositories:
              - id: A
                name: A
                url: http://git.com
            capabilities:
              - Zephyr
              - LeVADocs:
                  GAMPCategory: 5
        """

        when:
        project.init()

        then:
        project.hasCapability("Zephyr")

        then:
        project.hasCapability("LeVADocs")

        then:
        !project.hasCapability("other")

        cleanup:
        metadataFile.delete()
    }

    def "get document tracking issue"() {
        when:
        def result = project.getDocumentTrackingIssues(["Doc:TIP"])

        then:
        result == [
            [key: "NET-318", status: "DONE"]
        ]

        when:
        result = project.getDocumentTrackingIssues(["Doc:TIP", "Doc:TIP_Q", "Doc:TIP_P"])

        then:
        result == [
            [key: "NET-318", status: "DONE"],
            [key: "NET-7", status: "DONE"],
            [key: "NET-20", status: "DONE"]
        ]
    }

    def "get Git URL from path"() {
        given:
        def project = new Project(steps, logger)

        def path = "${steps.env.WORKSPACE}/a/b/c"
        def origin = "upstream"

        when:
        def result = project.getGitURLFromPath(path, origin)

        then:
        1 * steps.dir(path, _)

        then:
        1 * steps.sh({ it.script == "git config --get remote.${origin}.url" && it.returnStdout }) >> new URI("https://github.com/my-org/my-repo.git").toString()

        then:
        result == new URI("https://github.com/my-org/my-repo.git")
    }

    def "get Git URL from path without origin"() {
        given:
        def project = new Project(steps, logger)

        def path = "${steps.env.WORKSPACE}/a/b/c"

        when:
        def result = project.getGitURLFromPath(path)

        then:
        1 * steps.dir(path, _)

        then:
        1 * steps.sh({ it.script == "git config --get remote.origin.url" && it.returnStdout }) >> new URI("https://github.com/my-org/my-repo.git").toString()

        then:
        result == new URI("https://github.com/my-org/my-repo.git")
    }

    def "get Git URL from path with invalid path"() {
        given:
        def project = new Project(steps, logger)

        when:
        project.getGitURLFromPath(null)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to get Git URL. 'path' is undefined."

        when:
        project.getGitURLFromPath("")

        then:
        e = thrown(IllegalArgumentException)
        e.message == "Error: unable to get Git URL. 'path' is undefined."

        when:
        steps.env.WORKSPACE = "myWorkspace"
        def path = "myPath"
        project.getGitURLFromPath(path)

        then:
        e = thrown(IllegalArgumentException)
        e.message == "Error: unable to get Git URL. 'path' must be inside the Jenkins workspace: ${path}"
    }

    def "get Git URL from path with invalid remote"() {
        given:
        def project = new Project(steps, logger)

        def path = "${steps.env.WORKSPACE}/a/b/c"

        when:
        project.getGitURLFromPath(path, null)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to get Git URL. 'remote' is undefined."

        when:
        project.getGitURLFromPath(path, "")

        then:
        e = thrown(IllegalArgumentException)
        e.message == "Error: unable to get Git URL. 'remote' is undefined."
    }

    def "is triggered by change management process"() {
        when:
        steps.env.changeId = "0815"
        steps.env.configItem = "myItem"
        def result = Project.isTriggeredByChangeManagementProcess(steps)

        then:
        result

        when:
        steps.env.changeId = "0815"
        steps.env.configItem = null
        result = Project.isTriggeredByChangeManagementProcess(steps)

        then:
        !result

        when:
        steps.env.changeId = null
        steps.env.configItem = "myItem"
        result = Project.isTriggeredByChangeManagementProcess(steps)

        then:
        !result

        when:
        steps.env.changeId = null
        steps.env.configItem = null
        result = Project.isTriggeredByChangeManagementProcess(steps)

        then:
        !result
    }

    def "compute wip jira issues"() {
        given:
        def data = [:]
        Project.JiraDataItem.TYPES_WITH_STATUS.each { type ->
            data[type] = [
                "${type}-1": [
                    status: "TODO"
                ],
                "${type}-2": [
                    status: "DOING"
                ],
                "${type}-3": [
                    status: "DONE"
                ]
            ]
        }

        def expected = [:]
        Project.JiraDataItem.TYPES_WITH_STATUS.each { type ->
            expected[type] = [ "${type}-1", "${type}-2" ]
        }

        when:
        def result = project.computeWipJiraIssues(data)

        then:
        result == expected
    }

    def "get wip Jira issues for an empty collection"() {
        setup:
        def data = [project: [:], components: [:]]
        Project.JiraDataItem.TYPES_WITH_STATUS.each { type ->
            data[type] = [:]
        }

        def expected = [docChapters: [:]]
        Project.JiraDataItem.TYPES_WITH_STATUS.each { type ->
            expected[type] = []
        }

        project = createProject([
            "loadJiraData": {
                return data
            },
            "loadJiraDataBugs": {
                return [:]
            }
        ]).init()

        when:
        project.load(git, jiraUseCase)

        then:
        !project.hasWipJiraIssues()
        0 * project.reportPipelineStatus(*_)

        then:
        project.getWipJiraIssues() == expected
    }

    def "get wip Jira issues for a collection of DONE issues"() {
        setup:
        def data = [project: [:], components: [:]]
        Project.JiraDataItem.TYPES_WITH_STATUS.each { type ->
            data[type] = [
                "${type}-1": [
                    status: "DONE"
                ],
                "${type}-2": [
                    status: "DONE"
                ],
                "${type}-3": [
                    status: "DONE"
                ]
            ]
        }

        def expected = [docChapters: [:]]
        Project.JiraDataItem.TYPES_WITH_STATUS.each { type ->
            expected[type] = []
        }

        project = createProject([
            "loadJiraData": {
                return data
            },
            "loadJiraDataBugs": {
                return [
                    "bugs-1": [
                        status: "DONE"
                    ],
                    "bugs-2": [
                        status: "DONE"
                    ],
                    "bugs-3": [
                        status: "DONE"
                    ]
                ]
            }
        ]).init()

        when:
        project.load(git, jiraUseCase)

        then:
        !project.hasWipJiraIssues()
        0 * project.reportPipelineStatus(*_)

        then:
        project.getWipJiraIssues() == expected
    }

    def "get wip Jira issues for a mixed collection of DONE and other issues"() {
        setup:
        def data = [project: [:], components: [:]]
        Project.JiraDataItem.TYPES_WITH_STATUS.each { type ->
            data[type] = [
                "${type}-1": [
                    status: "TODO"
                ],
                "${type}-2": [
                    status: "DOING"
                ],
                "${type}-3": [
                    status: "DONE"
                ]
            ]
        }

        def expected = [docChapters: [:]]
        Project.JiraDataItem.TYPES_WITH_STATUS.each { type ->
            expected[type] = [ "${type}-1", "${type}-2" ]
        }

        def expectedMessage = "Pipeline-generated documents are watermarked '${LeVADocumentUseCase.WORK_IN_PROGRESS_WATERMARK}' since the following issues are work in progress: "
        Project.JiraDataItem.TYPES_WITH_STATUS.each { type ->
            expectedMessage += "\n\n${type.capitalize()}: ${type}-1, ${type}-2"
        }

        project = createProject([
            "loadJiraData": {
                return data
            },
            "loadJiraDataBugs": {
                return [
                    "bugs-1": [
                        status: "TODO"
                    ],
                    "bugs-2": [
                        status: "DOING"
                    ],
                    "bugs-3": [
                        status: "DONE"
                    ]
                ]
            }
        ]).init()

        when:
        project.load(git, jiraUseCase)

        then:
        project.hasWipJiraIssues()
        1 * project.addCommentInReleaseStatus(expectedMessage)

        then:
        project.getWipJiraIssues() == expected
    }

    def "get wip Jira issues for a collection of document chapters"() {
        setup:
        def data = [project: [:], components: [:]]
        Project.JiraDataItem.TYPES_WITH_STATUS.each { type ->
            data[type] = [:]
        }

        def expected = [docChapters: ["myDocumentType": ["docChapters-1", "docChapters-2"]]]
        Project.JiraDataItem.TYPES_WITH_STATUS.each { type ->
            expected[type] = []
        }

        project = createProject([
            "loadJiraData": {
                return data
            },
            "loadJiraDataBugs": {
                return [:]
            }
        ]).init()

        when:
        project.load(git, jiraUseCase)

        project.data.jira.undone.docChapters["myDocumentType"] = ["docChapters-1", "docChapters-2"]

        then:
        project.hasWipJiraIssues()

        then:
        project.getWipJiraIssues() == expected
    }

    def "load initial version"() {
        given:
        def component1 = [key: "CMP-1", name: "Component 1"]
        def epic1 = [key: "EPC-1", name: "Epic 1", status: "OPEN"]
        def mitigation1 = [key: "MTG-1", name: "Mitigation 1", status: "OPEN"]
        def requirement1 = [key: "REQ-1", name: "Requirement 1", status: "OPEN"]
        def risk1 = [key: "RSK-1", name: "Risk 1", status: "OPEN"]
        def techSpec1 = [key: "TS-1", name: "Technical Specification 1", status: "OPEN"]
        def test1 = [key: "TST-1", name: "Test 1", status: "OPEN"]
        def test2 = [key: "TST-2", name: "Test 2", status: "OPEN"]
        def doc1 = [key: "DOC-1", name: "Doc 1", status: "OPEN"]

        // Define key-based references
        component1.epics = [epic1.key]
        component1.mitigations = [mitigation1.key]
        component1.requirements = [requirement1.key]
        component1.risks = [risk1.key]
        component1.tests = [test1.key, test2.key]
        component1.techSpecs = [techSpec1.key]

        epic1.components = [component1.key]
        epic1.requirements = [requirement1.key]

        mitigation1.components = [component1.key]
        mitigation1.requirements = [requirement1.key]
        mitigation1.risks = [risk1.key]

        requirement1.components = [component1.key]
        requirement1.epics = [epic1.key]
        requirement1.mitigations = [mitigation1.key]
        requirement1.risks = [risk1.key]
        requirement1.tests = [test1.key, test2.key]

        risk1.components = [component1.key]
        risk1.requirements = [requirement1.key]
        risk1.tests = [test1.key, test2.key]

        techSpec1.components = [component1.key]

        test1.components = [component1.key]
        test1.requirements = [requirement1.key]
        test1.risks = [risk1.key]

        test2.components = [component1.key]
        test2.requirements = [requirement1.key]
        test2.risks = [risk1.key]

        when:
        project.load(this.git, this.jiraUseCase)

        then:
        1 * project.loadJiraData(_) >> [
            project     : [name: "my-project"],
            bugs        : [],
            components  : [(component1.key): component1],
            epics       : [(epic1.key): epic1],
            mitigations : [(mitigation1.key): mitigation1],
            requirements: [(requirement1.key): requirement1],
            risks       : [(risk1.key): risk1],
            tests       : [(test1.key): test1, (test2.key): test2],
            techSpecs   : [(techSpec1.key): techSpec1],
            docs        : [(doc1.key): doc1]
        ]

        1 * project.convertJiraDataToJiraDataItems(_)
        1 * project.resolveJiraDataItemReferences(_)
        1 * project.loadJiraDataBugs(_) >> createProjectJiraDataBugs()
        1 * project.loadJiraDataDocs() >> createProjectJiraDataDocs()
        1 * project.loadJiraDataIssueTypes() >> createProjectJiraDataIssueTypes()
        1 * jiraUseCase.updateJiraReleaseStatusBuildNumber()

        then:
        def components = project.components
        components.first() == component1

        // Unresolved references
        components.first().epics == [epic1.key]
        components.first().mitigations == [mitigation1.key]
        components.first().requirements == [requirement1.key]
        components.first().risks == [risk1.key]
        components.first().tests == [test1.key, test2.key]
        components.first().techSpecs == [techSpec1.key]

        // Resolved references
        components.first().getResolvedEpics() == [epic1]
        components.first().getResolvedMitigations() == [mitigation1]
        components.first().getResolvedSystemRequirements() == [requirement1]
        components.first().getResolvedRisks() == [risk1]
        components.first().getResolvedTests() == [test1, test2]
        components.first().getResolvedTechnicalSpecifications() == [techSpec1]

        // Resolved transitive references
        components.first().getResolvedEpics().first().getResolvedComponents() == [component1]
        components.first().getResolvedEpics().first().getResolvedSystemRequirements() == [requirement1]
        components.first().getResolvedEpics().first().getResolvedSystemRequirements().first().getResolvedMitigations() == [mitigation1]
        components.first().getResolvedEpics().first().getResolvedSystemRequirements().first().getResolvedTests() == [test1, test2]
        components.first().getResolvedEpics().first().getResolvedSystemRequirements().first().getResolvedTests().first().getResolvedRisks() == [risk1]
    }

    def "load build param changeDescription"() {
        when:
        steps.env.changeDescription = null
        def result = Project.loadBuildParams(steps)

        then:
        result.changeDescription == "UNDEFINED"

        when:
        steps.env.changeDescription = ""
        result = Project.loadBuildParams(steps)

        then:
        result.changeDescription == "UNDEFINED"

        when:
        steps.env.changeDescription = "myDescription"
        result = Project.loadBuildParams(steps)

        then:
        result.changeDescription == "myDescription"
    }

    def "load build param changeId"() {
        when:
        steps.env.changeId = null
        steps.env.environment = "dev"
        steps.env.version = "0.1"
        def result = Project.loadBuildParams(steps)

        then:
        result.changeId == "UNDEFINED"

        when:
        steps.env.changeId = ""
        steps.env.environment = "dev"
        steps.env.version = "0.1"
        result = Project.loadBuildParams(steps)

        then:
        result.changeId == "UNDEFINED"

        when:
        steps.env.changeId = "myId"
        steps.env.environment = "dev"
        steps.env.version = "0.1"
        result = Project.loadBuildParams(steps)

        then:
        result.changeId == "myId"
    }

    def "load build param configItem"() {
        when:
        steps.env.configItem = null
        def result = Project.loadBuildParams(steps)

        then:
        result.configItem == "UNDEFINED"

        when:
        steps.env.configItem = ""
        result = Project.loadBuildParams(steps)

        then:
        result.configItem == "UNDEFINED"

        when:
        steps.env.configItem = "myItem"
        result = Project.loadBuildParams(steps)

        then:
        result.configItem == "myItem"
    }

    def "load build param releaseStatusJiraIssueKey"() {
        when:
        steps.env.releaseStatusJiraIssueKey = "JIRA-1"
        def result = Project.loadBuildParams(steps)

        then:
        result.releaseStatusJiraIssueKey == "JIRA-1"

        when:
        steps.env.releaseStatusJiraIssueKey = " JIRA-1 "
        result = Project.loadBuildParams(steps)

        then:
        result.releaseStatusJiraIssueKey == "JIRA-1"

        when:
        steps.env.changeId = "1"
        steps.env.configItem = "my-config-item"
        steps.env.releaseStatusJiraIssueKey = null
        result = Project.loadBuildParams(steps)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to load build param 'releaseStatusJiraIssueKey': undefined"

        when:
        steps.env.changeId = null
        steps.env.configItem = null
        steps.env.releaseStatusJiraIssueKey = null
        result = Project.loadBuildParams(steps)

        then:
        result.releaseStatusJiraIssueKey == null
    }

    def "load build param targetEnvironment"() {
        when:
        steps.env.environment = null
        def result = Project.loadBuildParams(steps)

        then:
        result.targetEnvironment == "dev"

        when:
        steps.env.environment = ""
        result = Project.loadBuildParams(steps)

        then:
        result.targetEnvironment == "dev"

        when:
        steps.env.environment = "qa"
        result = Project.loadBuildParams(steps)

        then:
        result.targetEnvironment == "qa"
    }

    def "load build param targetEnvironmentToken"() {
        when:
        steps.env.environment = "dev"
        def result = Project.loadBuildParams(steps)

        then:
        result.targetEnvironmentToken == "D"

        when:
        steps.env.environment = "qa"
        result = Project.loadBuildParams(steps)

        then:
        result.targetEnvironmentToken == "Q"

        when:
        steps.env.environment = "prod"
        result = Project.loadBuildParams(steps)

        then:
        result.targetEnvironmentToken == "P"
    }

    def "load build param version"() {
        when:
        steps.env.version = null
        def result = Project.loadBuildParams(steps)

        then:
        result.version == "WIP"

        when:
        steps.env.version = ""
        result = Project.loadBuildParams(steps)

        then:
        result.version == "WIP"

        when:
        steps.env.version = "0.1"
        result = Project.loadBuildParams(steps)

        then:
        result.version == "0.1"
    }

    def "load Jira data"() {
        setup:
        def docGenData

        def jira = Mock(JiraService) {
            getDocGenData(_) >> {
                return docGenData
            }
        }

        def projectObj = new Project(steps, logger)
        projectObj.git = git
        projectObj.jiraUseCase = new JiraUseCase(projectObj, steps, Mock(MROPipelineUtil), jira, logger)

        def projectKey = "DEMO"

        project = createProject([
            "loadJiraData": {
                return projectObj.loadJiraData(projectKey)
            }
        ])

        when:
        docGenData = null
        project.loadJiraData(projectKey)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to load documentation generation data from Jira. 'project.id' is undefined."

        when:
        docGenData = [:]
        project.loadJiraData(projectKey)

        then:
        e = thrown(IllegalArgumentException)
        e.message == "Error: unable to load documentation generation data from Jira. 'project.id' is undefined."

        when:
        docGenData = [project: [:]]
        project.loadJiraData(projectKey)

        then:
        e = thrown(IllegalArgumentException)
        e.message == "Error: unable to load documentation generation data from Jira. 'project.id' is undefined."

        when:
        docGenData = [project: [id: null]]
        project.loadJiraData(projectKey)

        then:
        e = thrown(IllegalArgumentException)
        e.message == "Error: unable to load documentation generation data from Jira. 'project.id' is undefined."

        when:
        docGenData = [project: [id: "4711"]]
        def result = project.loadJiraData(projectKey)

        then:
        result.project.id == "4711"

        when:
        docGenData = [project: [id: 4711]]
        result = project.loadJiraData(projectKey)

        then:
        result.project.id == "4711"
    }

    def "load metadata"() {
        when:
        def result = project.loadMetadata()

        // Verify annotations to the metadata.yml file are made
        def expected = new Yaml().load(new File(Project.METADATA_FILE_NAME).text)
        expected.repositories.each { repo ->
            repo.branch = "master"
            repo.data = [ documents: [:] ]
            repo.url = "https://github.com/my-org/net-${repo.id}.git"
        }

        expected.capabilities = [
            [LeVADocs: [GAMPCategory: "5", templatesVersion: "1.0" ]]
        ]

        then:
        result == expected
    }

    def "load metadata with invalid file"() {
        when:
        project.loadMetadata(null)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to parse project meta data. 'filename' is undefined."
    }

    def "load project metadata with non-existent file"() {
        when:
        def filename = "non-existent"
        project.loadMetadata(filename)

        then:
        def e = thrown(RuntimeException)
        e.message == "Error: unable to load project meta data. File '${steps.env.WORKSPACE}/${filename}' does not exist."
    }

    def "load project metadata with invalid id"() {
        given:
        def metadataFile = Files.createTempFile("metadata", ".yml").toFile()

        when:
        metadataFile.text = """
            name: myName
        """

        project.loadMetadata(metadataFile.getAbsolutePath())

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to parse project meta data. Required attribute 'id' is undefined."

        cleanup:
        metadataFile.delete()
    }

    def "load project metadata with invalid name"() {
        given:
        def metadataFile = Files.createTempFile("metadata", ".yml").toFile()

        when:
        metadataFile.text = """
            id: myId
        """

        project.loadMetadata(metadataFile.getAbsolutePath())

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to parse project meta data. Required attribute 'name' is undefined."

        cleanup:
        metadataFile.delete()
    }

    def "load project metadata with invalid description"() {
        given:
        def metadataFile = Files.createTempFile("metadata", ".yml").toFile()

        when:
        metadataFile.text = """
            id: myId
            name: myName
            repositories:
              - id: A
        """

        def result = project.loadMetadata(metadataFile.getAbsolutePath())

        then:
        result.description == ""

        cleanup:
        metadataFile.delete()
    }

    def "load project metadata with undefined repositories"() {
        given:
        def metadataFile = Files.createTempFile("metadata", ".yml").toFile()

        when:
        metadataFile.text = """
            id: myId
            name: myName
        """

        def result = project.loadMetadata(metadataFile.getAbsolutePath())

        then:
        result.repositories == []

        cleanup:
        metadataFile.delete()
    }

    def "load project metadata with invalid repository id"() {
        given:
        def metadataFile = Files.createTempFile("metadata", ".yml").toFile()

        when:
        metadataFile.text = """
            id: myId
            name: myName
            repositories:
              - name: A
        """

        project.loadMetadata(metadataFile.getAbsolutePath())

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to parse project meta data. Required attribute 'repositories[0].id' is undefined."

        when:
        metadataFile.text = """
            id: myId
            name: myName
            repositories:
              - id: A
              - name: B
        """

        project.loadMetadata(metadataFile.getAbsolutePath())

        then:
        e = thrown(IllegalArgumentException)
        e.message == "Error: unable to parse project meta data. Required attribute 'repositories[1].id' is undefined."

        cleanup:
        metadataFile.delete()
    }

    def "load project metadata with invalid repository url"() {
        given:
        def metadataFile = Files.createTempFile("metadata", ".yml").toFile()

        when:
        metadataFile.text = """
            id: myId
            name: myName
            repositories:
              - name: A
        """

        project.loadMetadata(metadataFile.getAbsolutePath())

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to parse project meta data. Required attribute 'repositories[0].id' is undefined."

        when:
        metadataFile.text = """
            id: myId
            name: myName
            repositories:
              - id: A
              - name: B
        """

        project.loadMetadata(metadataFile.getAbsolutePath())

        then:
        e = thrown(IllegalArgumentException)
        e.message == "Error: unable to parse project meta data. Required attribute 'repositories[1].id' is undefined."

        cleanup:
        metadataFile.delete()
    }

    def "load project metadata with LeVADocs"() {
        given:
        def metadataFile = Files.createTempFile("metadata", ".yml").toFile()
        Project.METADATA_FILE_NAME = metadataFile.getAbsolutePath()

        metadataFile.text = """
            id: myId
            name: myName
            repositories:
              - id: A
                name: A
                url: http://git.com
            capabilities:
              - LeVADocs:
                  GAMPCategory: 5
                  templatesVersion: "2.0"
        """

        when:
        def result = project.init()

        then:
        result.getCapability("LeVADocs").GAMPCategory == 5
        result.getCapability("LeVADocs").templatesVersion == "2.0"

        cleanup:
        metadataFile.delete()
    }

    def "load project metadata with LeVADocs capabilities but without templatesVersion"() {
        given:
        def metadataFile = Files.createTempFile("metadata", ".yml").toFile()
        Project.METADATA_FILE_NAME = metadataFile.getAbsolutePath()

        metadataFile.text = """
            id: myId
            name: myName
            repositories:
              - id: A
                name: A
                url: http://git.com
            capabilities:
              - LeVADocs:
                  GAMPCategory: 5
        """

        when:
        def result = project.init()

        then:
        result.getCapability("LeVADocs").templatesVersion == "1.0"

        cleanup:
        metadataFile.delete()
    }

    def "load project metadata with multiple LeVADocs capabilities"() {
        given:
        def metadataFile = Files.createTempFile("metadata", ".yml").toFile()
        metadataFile.text = """
            id: myId
            name: myName
            repositories:
              - id: A
                name: A
                url: http://git.com
            capabilities:
              - LeVADocs:
                  GAMPCategory: 1
              - LeVADocs:
                  GAMPCategory: 3
        """

        when:
        project.loadMetadata(metadataFile.getAbsolutePath())

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to parse project metadata. More than one 'LeVADoc' capability has been defined."

        cleanup:
        metadataFile.delete()
    }

    def "load project metadata with LeVADocs capabilities but without GAMPCategory"() {
        given:
        def metadataFile = Files.createTempFile("metadata", ".yml").toFile()
        metadataFile.text = """
            id: myId
            name: myName
            repositories:
              - id: A
                name: A
                url: http://git.com
            capabilities:
              - LeVADocs:
        """

        when:
        project.loadMetadata(metadataFile.getAbsolutePath())

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: 'LeVADocs' capability has been defined but contains no 'GAMPCategory'."

        cleanup:
        metadataFile.delete()
    }

    def "load saved data from the previousVersion"() {
        given:
        def firstVersion = '1'
        def secondVersion = '2'
        def newVersionData = [
            project     : [name: "my-project"],
            version: secondVersion,
            predecessors: [firstVersion],
            bugs        : [:],
            components  : [:],
            epics       : [:],
            mitigations : [:],
            requirements: [:],
            risks       : [:],
            tests       : [:],
            techSpecs   : [:],
            docs        : [:]
        ]
        project = createProject([
            "loadJiraData": { return newVersionData }
        ]).init()

        when:
        project.load(this.git, this.jiraUseCase)

        then:
        1 * project.loadJiraData(_) >> newVersionData

        then:
        1 * project.loadSavedJiraData(firstVersion)

    }

    def "load only new data for initial release"() {
        given:
        def firstVersion = '1'
        def newVersionData = [
            project     : [name: "my-project"],
            version: firstVersion,
            predecessors: [],
            bugs        : [:],
            components  : [:],
            epics       : [:],
            mitigations : [:],
            requirements: [:],
            risks       : [:],
            tests       : [:],
            techSpecs   : [:],
            docs        : [:]
        ]
        project = createProject([
            "loadJiraData": { return newVersionData }
        ]).init()

        when:
        project.load(this.git, this.jiraUseCase)

        then:
        1 * project.loadJiraData(_) >> newVersionData

        then:
        0 * project.loadSavedJiraData(_)
    }

    def "do initial load if no previousVersion information is listed"() {
        given:
        def firstVersion = '1'
        def noPreviousReleases1 = [
            project     : [name: "my-project"],
            version: firstVersion,
            bugs        : [:],
            components  : [:],
            epics       : [:],
            mitigations : [:],
            requirements: [:],
            risks       : [:],
            tests       : [:],
            techSpecs   : [:],
            docs        : [:]
        ]
        project = createProject([
            "loadJiraData": { return noPreviousReleases1 }
        ]).init()

        when:
        project.load(this.git, this.jiraUseCase)

        then:
        1 * project.loadJiraData(_)

        then:
        0 * project.loadSavedJiraData(_)

    }
    def "do initial load if no previousVersions information is empty"() {
        given:
        def firstVersion = '1'

        def noPreviousReleases1 = [
            project     : [name: "my-project"],
            version: firstVersion,
            predecessors: [],
            bugs        : [:],
            components  : [:],
            epics       : [:],
            mitigations : [:],
            requirements: [:],
            risks       : [:],
            tests       : [:],
            techSpecs   : [:],
            docs        : [:]
        ]
        project = createProject([
            "loadJiraData": { return noPreviousReleases1 }
        ]).init()

        when:
        project.load(this.git, this.jiraUseCase)

        then:
        1 * project.loadJiraData(_)

        then:
        0 * project.loadSavedJiraData(_)

    }

    def "merge new test added"() {
        given:
        def firstVersion = '1'
        def secondVersion = '2'

        def cmp ={  name ->  [key: "CMP-${name}" as String, name: "Component 1"]}
        def req = {  name, String version = null ->  [key: "REQ-${name}" as String, description:name, version:version] }
        def ts = {  name, String version = null ->  [key: "TS-${name}" as String, description:name, version:version] }
        def rsk = {  name, String version = null ->  [key: "RSK-${name}" as String, description:name, version:version] }
        def tst = {  name, String version = null ->  [key: "TST-${name}" as String, description:name, version:version] }
        def mit = {  name, String version = null ->  [key: "MIT-${name}" as String, description:name, version:version] }

        def req1 = req('1', firstVersion)
        def rsk1 = rsk('1', firstVersion)
        def tst1 = tst('1', firstVersion)
        def tst2 = tst('2', secondVersion)

        req1 << [risks: [rsk1.key], tests: [tst1.key]]
        rsk1 << [requirements: [req1.key], tests: [tst1.key]]
        tst1 << [requirements: [req1.key], risks: [rsk1.key]]

        tst2 << [requirements: [req1.key], risks: [rsk1.key]]
        def req1Updated = req1.clone() + [tests: [tst1.key, tst2.key]]
        def rsk1Updated = rsk1.clone() + [tests: [tst1.key, tst2.key]]

        def storedData = [
            components  : [:],
            epics       : [:],
            mitigations : [:],
            requirements: [(req1.key): req1],
            risks       : [(rsk1.key): rsk1],
            tests       : [(tst1.key): tst1],
            techSpecs   : [:],
            docs        : [:]
        ]
        def newVersionData = [
            project     : [name: "my-project"],
            version: secondVersion,
            predecessors: [firstVersion],
            bugs        : [:],
            components  : [:],
            epics       : [:],
            mitigations : [:],
            requirements: [:],
            risks       : [:],
            tests       : [(tst2.key): tst2],
            techSpecs   : [:],
            docs        : [:]
            ]

        def mergedData = [
            components  : [:],
            epics       : [:],
            mitigations : [:],
            requirements: [(req1Updated.key): req1Updated ],
            risks       : [(rsk1Updated.key): rsk1Updated],
            tests       : [(tst1.key): tst1,
                (tst2.key): tst2],
            techSpecs   : [:],
            docs        : [:]
        ]
        project = createProject([
            //"loadSavedJiraData": { return storedData },
            "loadJiraData": { return newVersionData }
        ]).init()

        when:
        project.load(this.git, this.jiraUseCase)

        then:
        1 * project.loadSavedJiraData(_) >> storedData
        1 * project.loadJiraData(_) >> newVersionData

        then:
        1 * project.mergeJiraData(storedData, newVersionData)
        1 * project.convertJiraDataToJiraDataItems(_)
        1 * project.resolveJiraDataItemReferences(_)

        then:
        issueListIsEquals(project.components , mergedData.components.values() as List)
        issueListIsEquals(project.mitigations, mergedData.mitigations.values() as List)
        issueListIsEquals(project.getTechnicalSpecifications(), mergedData.techSpecs.values() as List)
        issueListIsEquals(project.getSystemRequirements(), mergedData.requirements.values() as List)
        issueListIsEquals(project.risks, mergedData.risks.values() as List)
        issueListIsEquals(project.tests, mergedData.tests.values() as List)

        def reqResult = project.getSystemRequirements().first()
        reqResult.risks == req1Updated.risks
        reqResult.tests == req1Updated.tests
        issueListIsEquals(reqResult.getResolvedRisks(), [rsk1Updated])
        issueListIsEquals(reqResult.getResolvedTests(), [tst1, tst2])

        def rskResult = project.getRisks().first()
        rskResult.requirements == rsk1Updated.requirements
        rskResult.tests == rsk1Updated.tests
        issueListIsEquals(rskResult.getResolvedSystemRequirements(), [rsk1Updated])
        issueListIsEquals(rskResult.getResolvedTests(), [tst1, tst2])
    }

    def "merge new risk and test added"() {
        given:
        def firstVersion = '1'
        def secondVersion = '2'

        def cmp ={  name ->  [key: "CMP-${name}" as String, name: "Component 1"]}
        def req = {  name, String version = null ->  [key: "REQ-${name}" as String, description:name, version:version] }
        def ts = {  name, String version = null ->  [key: "TS-${name}" as String, description:name, version:version] }
        def rsk = {  name, String version = null ->  [key: "RSK-${name}" as String, description:name, version:version] }
        def tst = {  name, String version = null ->  [key: "TST-${name}" as String, description:name, version:version] }
        def mit = {  name, String version = null ->  [key: "MIT-${name}" as String, description:name, version:version] }

        def req1 = req('1', firstVersion)
        def rsk1 = rsk('1', firstVersion)
        def tst1 = tst('1', firstVersion)
        def rsk2 = rsk('addedRisk', secondVersion)
        def tst2 = tst('addedTest', secondVersion)

        req1 << [risks: [rsk1.key], tests: [tst1.key]]
        rsk1 << [requirements: [req1.key], tests: [tst1.key]]
        tst1 << [requirements: [req1.key], risks: [rsk1.key]]

        rsk2 << [requirements: [req1.key], tests: [tst2.key]]
        tst2 << [requirements: [req1.key], risks: [rsk2.key]]
        def req1Updated = req1.clone() + [risks: [rsk1.key, rsk2.key], tests: [tst1.key, tst2.key]]


        def storedData = [
            components  : [:],
            epics       : [:],
            mitigations : [:],
            requirements: [(req1.key): req1],
            risks       : [(rsk1.key): rsk1],
            tests       : [(tst1.key): tst1],
            techSpecs   : [:],
            docs        : [:]
        ]
        def newVersionData = [
            project     : [name: "my-project"],
            version: secondVersion,
            predecessors: [firstVersion],
            bugs        : [:],
            components  : [:],
            epics       : [:],
            mitigations : [:],
            requirements: [:],
            risks       : [(rsk2.key): rsk2],
            tests       : [(tst2.key): tst2],
            techSpecs   : [:],
            docs        : [:]
        ]

        def mergedData = [
            components  : [:],
            epics       : [:],
            mitigations : [:],
            requirements: [(req1Updated.key): req1Updated ],
            risks       : [(rsk1.key): rsk1,
                           (rsk2.key): rsk2],
            tests       : [(tst1.key): tst1,
                           (tst2.key): tst2],
            techSpecs   : [:],
            docs        : [:]
        ]
        project = createProject([
            //"loadSavedJiraData": { return storedData },
            "loadJiraData": { return newVersionData }
        ]).init()

        when:
        project.load(this.git, this.jiraUseCase)

        then:
        1 * project.loadSavedJiraData(_) >> storedData
        1 * project.loadJiraData(_) >> newVersionData

        then:
        1 * project.mergeJiraData(storedData, newVersionData)
        1 * project.convertJiraDataToJiraDataItems(_)
        1 * project.resolveJiraDataItemReferences(_)

        then:
        issueListIsEquals(project.components, mergedData.components.values() as List)
        issueListIsEquals(project.mitigations, mergedData.mitigations.values() as List)
        issueListIsEquals(project.getTechnicalSpecifications(), mergedData.techSpecs.values() as List)
        issueListIsEquals(project.getSystemRequirements(), mergedData.requirements.values() as List)
        issueListIsEquals(project.risks, mergedData.risks.values() as List)
        issueListIsEquals(project.tests, mergedData.tests.values() as List)

        def reqResult = project.getSystemRequirements().first()
        reqResult.risks == req1Updated.risks
        reqResult.tests == req1Updated.tests
        issueListIsEquals(reqResult.getResolvedRisks(), [rsk1, rsk2])
        issueListIsEquals(reqResult.getResolvedTests(), [tst1, tst2])
    }

    def "merge modification of a risk"() {
        given:
        def betaVersion = '0.1'
        def firstVersion = '1'
        def secondVersion = '2'

        def cmp ={  name ->  [key: "CMP-${name}" as String, name: "Component 1"]}
        def req = {  name, String version = null ->  [key: "REQ-${name}" as String, description:name, version:version] }
        def ts = {  name, String version = null ->  [key: "TS-${name}" as String, description:name, version:version] }
        def rsk = {  name, String version = null ->  [key: "RSK-${name}" as String, description:name, version:version] }
        def tst = {  name, String version = null ->  [key: "TST-${name}" as String, description:name, version:version] }
        def mit = {  name, String version = null ->  [key: "MIT-${name}" as String, description:name, version:version] }

        def req0 = req('betaReq', betaVersion)
        def req1 = req('1', firstVersion)
        def req2 = req('midReq', firstVersion)
        def req3 = req('newerReq', secondVersion)
        def rsk1 = rsk('toModify', firstVersion)
        def tst1 = tst('1', secondVersion)
        def rsk2 = rsk('modification', secondVersion)


        req1 << [risks: [rsk1.key], tests: [tst1.key]]
        req2 << [predecessors: [[key: req0.key, version: req0.version]]]
        rsk1 << [requirements: [req1.key], tests: [tst1.key]]
        tst1 << [requirements: [req1.key], risks: [rsk1.key]]

        rsk2 << [requirements: [req1.key], tests: [tst1.key], predecessors: [rsk1.key]]
        req3 << [predecessors: [req2.key]]
        def req1Updated = req1.clone() + [risks: [rsk2.key]]
        def tst1Updated = tst1.clone() + [risks: [rsk2.key]]
        def rsk2WithDetails = rsk2.clone()
        rsk2WithDetails << [predecessors: [key: rsk1.key, version: rsk1.version]]
        def req3withDetails = req3.clone()
        req3withDetails << [predecessors: [[key: req2.key, version: req2.version],[key: req0.key, version: req0.version]]]

        def storedData = [
            components  : [:],
            epics       : [:],
            mitigations : [:],
            requirements: [(req1.key): req1, (req2.key): req2],
            risks       : [(rsk1.key): rsk1],
            tests       : [(tst1.key): tst1],
            techSpecs   : [:],
            docs        : [:]
        ]
        def newVersionData = [
            project     : [name: "my-project"],
            version: secondVersion,
            predecessors: [firstVersion],
            bugs        : [:],
            components  : [:],
            epics       : [:],
            mitigations : [:],
            requirements: [(req3.key):req3],
            risks       : [(rsk2.key): rsk2],
            tests       : [:],
            techSpecs   : [:],
            docs        : [:]
        ]

        def mergedData = [
            components  : [:],
            epics       : [:],
            mitigations : [:],
            requirements: [(req1Updated.key): req1Updated, (req3withDetails.key): req3withDetails],
            risks       : [(rsk2WithDetails.key): rsk2WithDetails],
            tests       : [(tst1Updated.key): tst1Updated],
            techSpecs   : [:],
            docs        : [:]
        ]
        project = createProject([
            //"loadSavedJiraData": { return storedData },
            "loadJiraData": { return newVersionData }
        ]).init()

        when:
        project.load(this.git, this.jiraUseCase)

        then:
        1 * project.loadSavedJiraData(_) >> storedData
        1 * project.loadJiraData(_) >> newVersionData

        then:
        1 * project.mergeJiraData(storedData, newVersionData)
        1 * project.convertJiraDataToJiraDataItems(_)
        1 * project.resolveJiraDataItemReferences(_)

        then:
        issueListIsEquals(project.components, mergedData.components.values() as List)
        issueListIsEquals(project.mitigations, mergedData.mitigations.values() as List)
        issueListIsEquals(project.getTechnicalSpecifications(), mergedData.techSpecs.values() as List)
        issueListIsEquals(project.getSystemRequirements(), mergedData.requirements.values() as List)
        issueListIsEquals(project.risks, mergedData.risks.values() as List)
        issueListIsEquals(project.tests, mergedData.tests.values() as List)

        def reqResult = project.getSystemRequirements().first()
        reqResult.risks == req1Updated.risks
        issueListIsEquals(reqResult.getResolvedRisks(), [rsk2])

        def tstResult = project.tests.first()
        tstResult.risks == tst1Updated.risks
        issueListIsEquals(tstResult.getResolvedRisks(), [rsk2])
    }

    def "merge deletion of a test"() {
        given:
        def firstVersion = '1'
        def secondVersion = '2'

        def cmp ={  name ->  [key: "CMP-${name}" as String, name: "Component 1"]}
        def req = {  name, String version = null ->  [key: "REQ-${name}" as String, description:name, version:version] }
        def ts = {  name, String version = null ->  [key: "TS-${name}" as String, description:name, version:version] }
        def rsk = {  name, String version = null ->  [key: "RSK-${name}" as String, description:name, version:version] }
        def tst = {  name, String version = null ->  [key: "TST-${name}" as String, description:name, version:version] }
        def mit = {  name, String version = null ->  [key: "MIT-${name}" as String, description:name, version:version] }

        def req1 = req('1', firstVersion)
        def rsk1 = rsk('1', firstVersion)
        def tst1 = tst('1', firstVersion)
        def tst2 = tst('toDelete', firstVersion)

        req1 << [risks: [rsk1.key], tests: [tst1.key, tst2.key]]
        rsk1 << [requirements: [req1.key], tests: [tst1.key, tst2.key]]
        tst1 << [requirements: [req1.key], risks: [rsk1.key]]
        tst2 << [requirements: [req1.key], risks: [rsk1.key]]

        def req1Updated = req1.clone() + [tests: [tst1.key]]
        def rsk1Updated = rsk1.clone() + [tests: [tst1.key]]

        def storedData = [
            components  : [:],
            epics       : [:],
            mitigations : [:],
            requirements: [(req1.key): req1],
            risks       : [(rsk1.key): rsk1],
            tests       : [(tst1.key): tst1, (tst2.key): tst2],
            techSpecs   : [:],
            docs        : [:]
        ]
        def newVersionData = [
            project     : [name: "my-project"],
            version: secondVersion,
            predecessors: [firstVersion],
            bugs        : [:],
            components  : [:],
            epics       : [:],
            mitigations : [:],
            requirements: [:],
            risks       : [:],
            tests       : [:],
            techSpecs   : [:],
            docs        : [:],
            discontinuations: [tst2.key]
        ]

        def mergedData = [
            components  : [:],
            epics       : [:],
            mitigations : [:],
            requirements: [(req1Updated.key): req1Updated ],
            risks       : [(rsk1Updated.key): rsk1Updated],
            tests       : [(tst1.key): tst1],
            techSpecs   : [:],
            docs        : [:],
            discontinuations: [tst2.key]
        ]
        project = createProject([
            //"loadSavedJiraData": { return storedData },
            "loadJiraData": { return newVersionData }
        ]).init()

        when:
        project.load(this.git, this.jiraUseCase)

        then:
        1 * project.loadSavedJiraData(_) >> storedData
        1 * project.loadJiraData(_) >> newVersionData

        then:
        1 * project.mergeJiraData(storedData, newVersionData)
        1 * project.convertJiraDataToJiraDataItems(_)
        1 * project.resolveJiraDataItemReferences(_)

        then:
        issueListIsEquals(project.components, mergedData.components.values() as List)
        issueListIsEquals(project.mitigations, mergedData.mitigations.values() as List)
        issueListIsEquals(project.getTechnicalSpecifications(), mergedData.techSpecs.values() as List)
        issueListIsEquals(project.getSystemRequirements(), mergedData.requirements.values() as List)
        issueListIsEquals(project.risks, mergedData.risks.values() as List)
        issueListIsEquals(project.tests, mergedData.tests.values() as List)

        def reqResult = project.getSystemRequirements().first()
        reqResult.risks == req1Updated.risks
        reqResult.tests == req1Updated.tests
        issueListIsEquals(reqResult.getResolvedRisks(), [rsk1Updated])
        issueListIsEquals(reqResult.getResolvedTests(), [tst1])

        def rskResult = project.getRisks().first()
        rskResult.requirements == rsk1Updated.requirements
        rskResult.tests == rsk1Updated.tests
        issueListIsEquals(rskResult.getResolvedSystemRequirements(), [rsk1Updated])
        issueListIsEquals(rskResult.getResolvedTests(), [tst1])
    }

    def "load jira data with pre-existing version information stored"() {
        given:
        def firstVersion = '1'
        def secondVersion = '2'

        def cmp ={  name ->  [key: "CMP-${name}" as String, name: "Component 1"]}
        def req = {  name, String version = null ->  [key: "REQ-${name}" as String, description:name, version:version] }
        def ts = {  name, String version = null ->  [key: "TS-${name}" as String, description:name, version:version] }
        def rsk = {  name, String version = null ->  [key: "RSK-${name}" as String, description:name, version:version] }
        def tst = {  name, String version = null ->  [key: "TST-${name}" as String, description:name, version:version] }
        def mit = {  name, String version = null ->  [key: "MIT-${name}" as String, description:name, version:version] }



        def cmp1 = cmp("1")
        def req1 = req(1, firstVersion)
        def req2 = req('toDelete', firstVersion)
        def req3 = req('toChange', firstVersion)
        def req4 = req('changed', secondVersion)
        def rsk1 = rsk('1', firstVersion)
        def tst1 = tst('1', firstVersion)
        def tst2 = tst('2', firstVersion)
        def tst3 = tst('3', secondVersion)
        def mit1 = mit('1', firstVersion)
        def ts1 = ts('1', firstVersion)

        //cmp1 << []

    }

    Boolean issueIsEquals(Map issueA, Map issueB) {
        issueA.forEach{mapKey, value ->
            if (!issueB[mapKey]) return false
            if (issueB[mapKey] != value ) return false
        }
        true
    }

    Boolean issueListIsEquals(List issuesA, List issuesB) {
        if (issuesA.size() != issuesB.size()) return false
        def issuesBKeys = issuesB.collect{it.key}
        issuesA.forEach{ issueA ->
            if (! issuesBKeys.contains(issueA.key)) return false
            def correspondentIssueB = issuesB.find{it.key = issueA.key}
            if (! issueIsEquals(issueA, correspondentIssueB)) return false
        }
        true
    }
}
