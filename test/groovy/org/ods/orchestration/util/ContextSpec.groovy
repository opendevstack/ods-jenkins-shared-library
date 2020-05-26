package org.ods.orchestration.util

import java.nio.file.Files

import org.apache.http.client.utils.URIBuilder
import org.ods.services.GitService
import org.ods.orchestration.service.*
import org.ods.orchestration.usecase.*
import org.ods.util.IPipelineSteps
import org.yaml.snakeyaml.Yaml

import static util.FixtureHelper.*

import util.*

class ContextSpec extends SpecHelper {

    GitService git
    IPipelineSteps steps
    JiraUseCase jiraUseCase
    Context context
    File metadataFile

    def createContext(Map<String, Closure> mixins = [:]) {
        Context context = Spy(constructorArgs: [steps])

        if (mixins.containsKey("getGitURLFromPath")) {
            context.getGitURLFromPath(*_) >> { mixins["getGitURLFromPath"]() }
        } else {
            context.getGitURLFromPath(*_) >> { return new URIBuilder("https://github.com/my-org/my-pipeline-repo.git").build() }
        }

        if (mixins.containsKey("loadJiraData")) {
            context.loadJiraData(*_) >> { mixins["loadJiraData"]() }
        } else {
            context.loadJiraData(*_) >> { return createContextJiraData() }
        }

        if (mixins.containsKey("loadJiraDataBugs")) {
            context.loadJiraDataBugs(*_) >> { mixins["loadJiraDataBugs"]() }
        } else {
            context.loadJiraDataBugs(*_) >> { return createContextJiraDataBugs() }
        }

        if (mixins.containsKey("loadJiraDataDocs")) {
            context.loadJiraDataDocs(*_) >> { mixins["loadJiraDataDocs"]() }
        } else {
            context.loadJiraDataDocs(*_) >> { return createContextJiraDataDocs() }
        }

        if (mixins.containsKey("loadJiraDataIssueTypes")) {
            context.loadJiraDataIssueTypes(*_) >> { mixins["loadJiraDataIssueTypes"]() }
        } else {
            context.loadJiraDataIssueTypes(*_) >> { return createContextJiraDataIssueTypes() }
        }

        return context
    }

    def setup() {
        git = Mock(GitService)
        jiraUseCase = Mock(JiraUseCase)
        steps = Spy(util.PipelineSteps)
        steps.env.WORKSPACE = ""

        metadataFile = new FixtureHelper().getResource("/project-metadata.yml")
        Context.METADATA_FILE_NAME = metadataFile.getAbsolutePath()

        context = createContext().init().load(git, jiraUseCase)
    }

    def "get build environment for DEBUG"() {
        when:
        def result = Context.getBuildEnvironment(steps)

        then:
        result.find { it == "DEBUG=false" }

        when:
        result = Context.getBuildEnvironment(steps, true)

        then:
        result.find { it == "DEBUG=true" }

        when:
        result = Context.getBuildEnvironment(steps, false)

        then:
        result.find { it == "DEBUG=false" }
    }

    def "get build environment for MULTI_REPO_BUILD"() {
        when:
        def result = Context.getBuildEnvironment(steps)

        then:
        result.find { it == "MULTI_REPO_BUILD=true" }
    }

    def "get build environment for MULTI_REPO_ENV"() {
        when:
        steps.env.environment = null
        def result = Context.getBuildEnvironment(steps)

        then:
        result.find { it == "MULTI_REPO_ENV=dev" }

        when:
        steps.env.environment = ""
        result = Context.getBuildEnvironment(steps)

        then:
        result.find { it == "MULTI_REPO_ENV=dev" }

        when:
        steps.env.environment = "qa"
        result = Context.getBuildEnvironment(steps)

        then:
        result.find { it == "MULTI_REPO_ENV=test" }
    }

    def "get build environment for MULTI_REPO_ENV_TOKEN"() {
        when:
        steps.env.environment = "dev"
        def result = Context.getBuildEnvironment(steps)

        then:
        result.find { it == "MULTI_REPO_ENV_TOKEN=D" }

        when:
        steps.env.environment = "qa"
        result = Context.getBuildEnvironment(steps)

        then:
        result.find { it == "MULTI_REPO_ENV_TOKEN=Q" }

        when:
        steps.env.environment = "prod"
        result = Context.getBuildEnvironment(steps)

        then:
        result.find { it == "MULTI_REPO_ENV_TOKEN=P" }
    }

    def "get build environment for RELEASE_PARAM_CHANGE_ID"() {
        when:
        steps.env.environment = "dev"
        steps.env.version = "0.1"
        def result = Context.getBuildEnvironment(steps)

        then:
        result.find { it == "RELEASE_PARAM_CHANGE_ID=UNDEFINED" }

        when:
        steps.env.changeId = ""
        steps.env.environment = "dev"
        steps.env.version = "0.1"
        result = Context.getBuildEnvironment(steps)

        then:
        result.find { it == "RELEASE_PARAM_CHANGE_ID=UNDEFINED" }

        when:
        steps.env.changeId = "myId"
        steps.env.environment = "dev"
        steps.env.version = "0.1"
        result = Context.getBuildEnvironment(steps)

        then:
        result.find { it == "RELEASE_PARAM_CHANGE_ID=myId" }
    }

    def "get build environment for RELEASE_PARAM_CHANGE_DESC"() {
        when:
        steps.env.changeDescription = null
        def result = Context.getBuildEnvironment(steps)

        then:
        result.find { it == "RELEASE_PARAM_CHANGE_DESC=UNDEFINED" }

        when:
        steps.env.changeDescription = ""
        result = Context.getBuildEnvironment(steps)

        then:
        result.find { it == "RELEASE_PARAM_CHANGE_DESC=UNDEFINED" }

        when:
        steps.env.changeDescription = "myDescription"
        result = Context.getBuildEnvironment(steps)

        then:
        result.find { it == "RELEASE_PARAM_CHANGE_DESC=myDescription" }
    }

    def "get build environment for RELEASE_PARAM_CONFIG_ITEM"() {
        when:
        steps.env.configItem = null
        def result = Context.getBuildEnvironment(steps)

        then:
        result.find { it == "RELEASE_PARAM_CONFIG_ITEM=UNDEFINED" }

        when:
        steps.env.configItem = ""
        result = Context.getBuildEnvironment(steps)

        then:
        result.find { it == "RELEASE_PARAM_CONFIG_ITEM=UNDEFINED" }

        when:
        steps.env.configItem = "myItem"
        result = Context.getBuildEnvironment(steps)

        then:
        result.find { it == "RELEASE_PARAM_CONFIG_ITEM=myItem" }
    }

    def "get build environment for RELEASE_PARAM_VERSION"() {
        when:
        steps.env.version = null
        def result = Context.getBuildEnvironment(steps)

        then:
        result.find { it == "RELEASE_PARAM_VERSION=WIP" }

        when:
        steps.env.version = ""
        result = Context.getBuildEnvironment(steps)

        then:
        result.find { it == "RELEASE_PARAM_VERSION=WIP" }

        when:
        steps.env.version = "0.1"
        result = Context.getBuildEnvironment(steps)

        then:
        result.find { it == "RELEASE_PARAM_VERSION=0.1" }
    }

    def "get versioned dev ens"() {
        when:
        def result = new Context(steps).getVersionedDevEnvsEnabled()

        then:
        result == false

        when:
        result = new Context(steps, [versionedDevEnvs: false]).getVersionedDevEnvsEnabled()

        then:
        result == false

        when:
        result = new Context(steps, [versionedDevEnvs: true]).getVersionedDevEnvsEnabled()

        then:
        result == true
    }

    def "get concrete environments"() {
        expect:
        Context.getConcreteEnvironment(environment, version, versionedDevEnvsEnabled) == result

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
        context.getEnvironmentParams('/path/to/dev.env') == result

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
        context.getEnvironmentParamsFile() == result

        where:
        exists  || result
        true    || '/path/to/workspace/dev.env'
        false   || ''
    }

    def "target cluster is not external when no API URL is configured"() {
        context.setOpenShiftData('https://api.example.openshift.com:443')

        expect:
        context.getTargetClusterIsExternal() == false
    }

    def "target cluster can be external when an API URL is configured"() {
        given:
        steps.env.environment = environment
        def metadataFile = Files.createTempFile("metadata", ".yml").toFile()
        Context.METADATA_FILE_NAME = metadataFile.getAbsolutePath()

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
        context.init()
        context.setOpenShiftData('https://api.example.openshift.com:443')

        then:
        context.getTargetClusterIsExternal() == result

        where:
        environment | configuredProdApiUrl || result
        'dev'       | 'https://api.other.openshift.com'   || false
        'prod'      | 'https://api.other.openshift.com'   || true
        'prod'      | 'https://api.example.openshift.com' || false
    }

    def "get capabilities"() {
        given:
        def metadataFile = Files.createTempFile("metadata", ".yml").toFile()
        Context.METADATA_FILE_NAME = metadataFile.getAbsolutePath()

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
        context.init()

        then:
        context.getCapabilities() == [
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
        Context.METADATA_FILE_NAME = metadataFile.getAbsolutePath()

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
        context.init()

        then:
        context.getCapability("Zephyr")

        then:
        context.getCapability("LeVADocs") == [
            GAMPCategory: 5,
            templatesVersion: "1.0"
        ]

        when:
        context.getCapability("LeVADocs").GAMPCategory = 3

        then:
        context.getCapability("LeVADocs").GAMPCategory == 3

        cleanup:
        metadataFile.delete()
    }

    def "has capability"() {
        given:
        def metadataFile = Files.createTempFile("metadata", ".yml").toFile()
        Context.METADATA_FILE_NAME = metadataFile.getAbsolutePath()

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
        context.init()

        then:
        context.hasCapability("Zephyr")

        then:
        context.hasCapability("LeVADocs")

        then:
        !context.hasCapability("other")

        cleanup:
        metadataFile.delete()
    }

    def "get document tracking issue"() {
        when:
        def result = context.getDocumentTrackingIssues(["Doc:TIP"])

        then:
        result == [
            [key: "NET-318", status: "DONE"]
        ]

        when:
        result = context.getDocumentTrackingIssues(["Doc:TIP", "Doc:TIP_Q", "Doc:TIP_P"])

        then:
        result == [
            [key: "NET-318", status: "DONE"],
            [key: "NET-7", status: "DONE"],
            [key: "NET-20", status: "DONE"]
        ]
    }

    def "get Git URL from path"() {
        given:
        def project = new Context(steps)

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
        def project = new Context(steps)

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
        def project = new Context(steps)

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
        def project = new Context(steps)

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
        def result = Context.isTriggeredByChangeManagementProcess(steps)

        then:
        result

        when:
        steps.env.changeId = "0815"
        steps.env.configItem = null
        result = Context.isTriggeredByChangeManagementProcess(steps)

        then:
        !result

        when:
        steps.env.changeId = null
        steps.env.configItem = "myItem"
        result = Context.isTriggeredByChangeManagementProcess(steps)

        then:
        !result

        when:
        steps.env.changeId = null
        steps.env.configItem = null
        result = Context.isTriggeredByChangeManagementProcess(steps)

        then:
        !result
    }

    def "compute wip jira issues"() {
        given:
        def data = [:]
        Context.JiraDataItem.TYPES_WITH_STATUS.each { type ->
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
        Context.JiraDataItem.TYPES_WITH_STATUS.each { type ->
            expected[type] = [ "${type}-1", "${type}-2" ]
        }

        when:
        def result = context.computeWipJiraIssues(data)

        then:
        result == expected
    }

    def "get wip Jira issues for an empty collection"() {
        setup:
        def data = [project: [:], components: [:]]
        Context.JiraDataItem.TYPES_WITH_STATUS.each { type ->
            data[type] = [:]
        }

        def expected = [docChapters: [:]]
        Context.JiraDataItem.TYPES_WITH_STATUS.each { type ->
            expected[type] = []
        }

        context = createContext([
            "loadJiraData": {
                return data
            },
            "loadJiraDataBugs": {
                return [:]
            }
        ]).init()

        when:
        context.load(git, jiraUseCase)

        then:
        !context.hasWipJiraIssues()
        0 * context.reportPipelineStatus(*_)

        then:
        context.getWipJiraIssues() == expected
    }

    def "get wip Jira issues for a collection of DONE issues"() {
        setup:
        def data = [project: [:], components: [:]]
        Context.JiraDataItem.TYPES_WITH_STATUS.each { type ->
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
        Context.JiraDataItem.TYPES_WITH_STATUS.each { type ->
            expected[type] = []
        }

        context = createContext([
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
        context.load(git, jiraUseCase)

        then:
        !context.hasWipJiraIssues()
        0 * context.reportPipelineStatus(*_)

        then:
        context.getWipJiraIssues() == expected
    }

    def "get wip Jira issues for a mixed collection of DONE and other issues"() {
        setup:
        def data = [project: [:], components: [:]]
        Context.JiraDataItem.TYPES_WITH_STATUS.each { type ->
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
        Context.JiraDataItem.TYPES_WITH_STATUS.each { type ->
            expected[type] = [ "${type}-1", "${type}-2" ]
        }

        def expectedMessage = "Pipeline-generated documents are watermarked '${LeVADocumentUseCase.WORK_IN_PROGRESS_WATERMARK}' since the following issues are work in progress:"
        Context.JiraDataItem.TYPES_WITH_STATUS.each { type ->
            expectedMessage += "\n\n${type.capitalize()}: ${type}-1, ${type}-2"
        }

        context = createContext([
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
        context.load(git, jiraUseCase)

        then:
        context.hasWipJiraIssues()
        1 * context.addCommentInReleaseStatus(expectedMessage)

        then:
        context.getWipJiraIssues() == expected
    }

    def "get wip Jira issues for a collection of document chapters"() {
        setup:
        def data = [project: [:], components: [:]]
        Context.JiraDataItem.TYPES_WITH_STATUS.each { type ->
            data[type] = [:]
        }

        def expected = [docChapters: ["myDocumentType": ["docChapters-1", "docChapters-2"]]]
        Context.JiraDataItem.TYPES_WITH_STATUS.each { type ->
            expected[type] = []
        }

        context = createContext([
            "loadJiraData": {
                return data
            },
            "loadJiraDataBugs": {
                return [:]
            }
        ]).init()

        when:
        context.load(git, jiraUseCase)

        context.data.jira.undone.docChapters["myDocumentType"] = ["docChapters-1", "docChapters-2"]

        then:
        context.hasWipJiraIssues()

        then:
        context.getWipJiraIssues() == expected
    }

    def "load"() {
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
        context.load(this.git, this.jiraUseCase)

        then:
        1 * context.loadJiraData(_) >> [
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

        1 * context.convertJiraDataToJiraDataItems(_)
        1 * context.resolveJiraDataItemReferences(_)
        1 * context.loadJiraDataBugs(_) >> createContextJiraDataBugs()
        1 * context.loadJiraDataDocs() >> createContextJiraDataDocs()
        1 * context.loadJiraDataIssueTypes() >> createContextJiraDataIssueTypes()
        1 * jiraUseCase.updateJiraReleaseStatusBuildNumber()

        then:
        def components = context.components
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
        def result = Context.loadBuildParams(steps)

        then:
        result.changeDescription == "UNDEFINED"

        when:
        steps.env.changeDescription = ""
        result = Context.loadBuildParams(steps)

        then:
        result.changeDescription == "UNDEFINED"

        when:
        steps.env.changeDescription = "myDescription"
        result = Context.loadBuildParams(steps)

        then:
        result.changeDescription == "myDescription"
    }

    def "load build param changeId"() {
        when:
        steps.env.changeId = null
        steps.env.environment = "dev"
        steps.env.version = "0.1"
        def result = Context.loadBuildParams(steps)

        then:
        result.changeId == "UNDEFINED"

        when:
        steps.env.changeId = ""
        steps.env.environment = "dev"
        steps.env.version = "0.1"
        result = Context.loadBuildParams(steps)

        then:
        result.changeId == "UNDEFINED"

        when:
        steps.env.changeId = "myId"
        steps.env.environment = "dev"
        steps.env.version = "0.1"
        result = Context.loadBuildParams(steps)

        then:
        result.changeId == "myId"
    }

    def "load build param configItem"() {
        when:
        steps.env.configItem = null
        def result = Context.loadBuildParams(steps)

        then:
        result.configItem == "UNDEFINED"

        when:
        steps.env.configItem = ""
        result = Context.loadBuildParams(steps)

        then:
        result.configItem == "UNDEFINED"

        when:
        steps.env.configItem = "myItem"
        result = Context.loadBuildParams(steps)

        then:
        result.configItem == "myItem"
    }

    def "load build param releaseStatusJiraIssueKey"() {
        when:
        steps.env.releaseStatusJiraIssueKey = "JIRA-1"
        def result = Context.loadBuildParams(steps)

        then:
        result.releaseStatusJiraIssueKey == "JIRA-1"

        when:
        steps.env.releaseStatusJiraIssueKey = " JIRA-1 "
        result = Context.loadBuildParams(steps)

        then:
        result.releaseStatusJiraIssueKey == "JIRA-1"

        when:
        steps.env.changeId = "1"
        steps.env.configItem = "my-config-item"
        steps.env.releaseStatusJiraIssueKey = null
        result = Context.loadBuildParams(steps)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to load build param 'releaseStatusJiraIssueKey': undefined"

        when:
        steps.env.changeId = null
        steps.env.configItem = null
        steps.env.releaseStatusJiraIssueKey = null
        result = Context.loadBuildParams(steps)

        then:
        result.releaseStatusJiraIssueKey == null
    }

    def "load build param targetEnvironment"() {
        when:
        steps.env.environment = null
        def result = Context.loadBuildParams(steps)

        then:
        result.targetEnvironment == "dev"

        when:
        steps.env.environment = ""
        result = Context.loadBuildParams(steps)

        then:
        result.targetEnvironment == "dev"

        when:
        steps.env.environment = "qa"
        result = Context.loadBuildParams(steps)

        then:
        result.targetEnvironment == "qa"
    }

    def "load build param targetEnvironmentToken"() {
        when:
        steps.env.environment = "dev"
        def result = Context.loadBuildParams(steps)

        then:
        result.targetEnvironmentToken == "D"

        when:
        steps.env.environment = "qa"
        result = Context.loadBuildParams(steps)

        then:
        result.targetEnvironmentToken == "Q"

        when:
        steps.env.environment = "prod"
        result = Context.loadBuildParams(steps)

        then:
        result.targetEnvironmentToken == "P"
    }

    def "load build param version"() {
        when:
        steps.env.version = null
        def result = Context.loadBuildParams(steps)

        then:
        result.version == "WIP"

        when:
        steps.env.version = ""
        result = Context.loadBuildParams(steps)

        then:
        result.version == "WIP"

        when:
        steps.env.version = "0.1"
        result = Context.loadBuildParams(steps)

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

        def projectObj = new Context(steps)
        projectObj.git = git
        projectObj.jiraUseCase = new JiraUseCase(projectObj, steps, Mock(MROPipelineUtil), jira)

        def projectKey = "DEMO"

        context = createContext([
            "loadJiraData": {
                return projectObj.loadJiraData(projectKey)
            }
        ])

        when:
        docGenData = null
        context.loadJiraData(projectKey)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to load documentation generation data from Jira. 'project.id' is undefined."

        when:
        docGenData = [:]
        context.loadJiraData(projectKey)

        then:
        e = thrown(IllegalArgumentException)
        e.message == "Error: unable to load documentation generation data from Jira. 'project.id' is undefined."

        when:
        docGenData = [project: [:]]
        context.loadJiraData(projectKey)

        then:
        e = thrown(IllegalArgumentException)
        e.message == "Error: unable to load documentation generation data from Jira. 'project.id' is undefined."

        when:
        docGenData = [project: [id: null]]
        context.loadJiraData(projectKey)

        then:
        e = thrown(IllegalArgumentException)
        e.message == "Error: unable to load documentation generation data from Jira. 'project.id' is undefined."

        when:
        docGenData = [project: [id: "4711"]]
        def result = context.loadJiraData(projectKey)

        then:
        result.project.id == "4711"

        when:
        docGenData = [project: [id: 4711]]
        result = context.loadJiraData(projectKey)

        then:
        result.project.id == "4711"
    }

    def "load metadata"() {
        when:
        def result = context.loadMetadata()

        // Verify annotations to the metadata.yml file are made
        def expected = new Yaml().load(new File(Context.METADATA_FILE_NAME).text)
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
        context.loadMetadata(null)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to parse project meta data. 'filename' is undefined."
    }

    def "load project metadata with non-existent file"() {
        when:
        def filename = "non-existent"
        context.loadMetadata(filename)

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

        context.loadMetadata(metadataFile.getAbsolutePath())

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

        context.loadMetadata(metadataFile.getAbsolutePath())

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

        def result = context.loadMetadata(metadataFile.getAbsolutePath())

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

        def result = context.loadMetadata(metadataFile.getAbsolutePath())

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

        context.loadMetadata(metadataFile.getAbsolutePath())

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

        context.loadMetadata(metadataFile.getAbsolutePath())

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

        context.loadMetadata(metadataFile.getAbsolutePath())

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

        context.loadMetadata(metadataFile.getAbsolutePath())

        then:
        e = thrown(IllegalArgumentException)
        e.message == "Error: unable to parse project meta data. Required attribute 'repositories[1].id' is undefined."

        cleanup:
        metadataFile.delete()
    }

    def "load project metadata with LeVADocs"() {
        given:
        def metadataFile = Files.createTempFile("metadata", ".yml").toFile()
        Context.METADATA_FILE_NAME = metadataFile.getAbsolutePath()

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
        def result = context.init()

        then:
        result.getCapability("LeVADocs").GAMPCategory == 5
        result.getCapability("LeVADocs").templatesVersion == "2.0"

        cleanup:
        metadataFile.delete()
    }

    def "load project metadata with LeVADocs capabilities but without templatesVersion"() {
        given:
        def metadataFile = Files.createTempFile("metadata", ".yml").toFile()
        Context.METADATA_FILE_NAME = metadataFile.getAbsolutePath()

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
        def result = context.init()

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
        context.loadMetadata(metadataFile.getAbsolutePath())

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to parse project metadata. More than one LeVADoc capability has been defined."

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
        context.loadMetadata(metadataFile.getAbsolutePath())

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: LeVADocs capability has been defined but contains no GAMPCategory."

        cleanup:
        metadataFile.delete()
    }
}
