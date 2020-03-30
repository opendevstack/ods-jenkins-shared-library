package org.ods.util

import java.nio.file.Files

import org.apache.http.client.utils.URIBuilder
import org.ods.service.*
import org.ods.usecase.*
import org.yaml.snakeyaml.Yaml

import static util.FixtureHelper.*

import util.*

class ProjectSpec extends SpecHelper {

    GitUtil git
    IPipelineSteps steps
    JiraUseCase jiraUseCase
    Project project
    File metadataFile

    def createProject(Map<String, Closure> mixins = [:]) {
        Project project = Spy(constructorArgs: [steps])

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
        git = Mock(GitUtil)
        jiraUseCase = Mock(JiraUseCase)
        steps = Spy(util.PipelineSteps)
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
        def result = new Project(steps).getVersionedDevEnvsEnabled()

        then:
        result == false

        when:
        result = new Project(steps, [versionedDevEnvs: false]).getVersionedDevEnvsEnabled()

        then:
        result == false

        when:
        result = new Project(steps, [versionedDevEnvs: true]).getVersionedDevEnvsEnabled()

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
        def project = new Project(steps)

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
        def project = new Project(steps)

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
        def project = new Project(steps)

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
        def project = new Project(steps)

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

    def "load"() {
        given:
        def component1 = [key: "CMP-1", name: "Component 1"]
        def epic1 = [key: "EPC-1", name: "Epic 1"]
        def mitigation1 = [key: "MTG-1", name: "Mitigation 1"]
        def requirement1 = [key: "REQ-1", name: "Requirement 1"]
        def risk1 = [key: "RSK-1", name: "Risk 1"]
        def techSpec1 = [key: "TS-1", name: "Technical Specification 1"]
        def test1 = [key: "TST-1", name: "Test 1"]
        def test2 = [key: "TST-2", name: "Test 2"]
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
                println "D: " + docGenData
                return docGenData
            }
        }

        def projectObj = new Project(steps)
        projectObj.git = git
        projectObj.jiraUseCase = new JiraUseCase(projectObj, steps, Mock(MROPipelineUtil), jira)

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
        project.loadMetadata(metadataFile.getAbsolutePath())

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: LeVADocs capability has been defined but contains no GAMPCategory."

        cleanup:
        metadataFile.delete()
    }
}
