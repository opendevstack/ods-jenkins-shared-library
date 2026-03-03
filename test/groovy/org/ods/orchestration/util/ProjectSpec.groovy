package org.ods.orchestration.util


import org.apache.http.client.utils.URIBuilder
import org.ods.orchestration.service.JiraService
import org.ods.orchestration.usecase.JiraUseCase
import org.ods.orchestration.usecase.LeVADocumentUseCase
import org.ods.orchestration.usecase.OpenIssuesException
import org.ods.services.GitService
import org.ods.util.IPipelineSteps
import org.ods.util.Logger
import org.yaml.snakeyaml.Yaml
import util.FixtureHelper
import util.SpecHelper

import java.nio.file.Files

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
            project.loadJiraData(*_) >> { return FixtureHelper.createProjectJiraData() }
        }

        if (mixins.containsKey("loadJiraDataBugs")) {
            project.loadJiraDataBugs(*_) >> { mixins["loadJiraDataBugs"]() }
        } else {
            project.loadJiraDataBugs(*_) >> { return FixtureHelper.createProjectJiraDataBugs() }
        }

        if (mixins.containsKey("loadJiraDataDocs")) {
            project.loadJiraDataTrackingDocs(*_) >> { mixins["loadJiraDataDocs"]() }
        } else {
            project.loadJiraDataTrackingDocs(*_) >> { return FixtureHelper.createProjectJiraDataDocs() }
        }

        if (mixins.containsKey("loadJiraDataSecurityVulnerabilities")) {
            project.loadJiraDataSecurityVulnerabilities(*_) >> { mixins["loadJiraDataSecurityVulnerabilities"]() }
        } else {
            project.loadJiraDataSecurityVulnerabilities(*_) >> {
                return FixtureHelper.createProjectJiraSecurityVulnerabilities()
            }
        }

        if (mixins.containsKey("loadJiraDataIssueTypes")) {
            project.loadJiraDataIssueTypes(*_) >> { mixins["loadJiraDataIssueTypes"]() }
        } else {
            project.loadJiraDataIssueTypes(*_) >> { return FixtureHelper.createProjectJiraDataIssueTypes() }
        }

        if (mixins.containsKey("loadJiraData")) {
            project.loadJiraData(*_) >> { mixins["loadJiraData"]() }
        }

        if (mixins.containsKey("getDocumentChapterData")) {
            project.getDocumentChapterData(*_) >> { mixins["getDocumentChapterData"]() }
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
        project.isTargetClusterExternal() == false
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
            environments:
              prod:
                apiUrl: ${configuredProdApiUrl}
        """

        when:
        project.init()
        project.setOpenShiftData('https://api.example.openshift.com:443')

        then:
        project.isTargetClusterExternal() == result

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
                    templatesVersion: "${Project.DEFAULT_TEMPLATE_VERSION}"
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
            templatesVersion: "${Project.DEFAULT_TEMPLATE_VERSION}"
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
                    status: "TODO",
                    key: "${type}-1",
                ],
                "${type}-2": [
                    status: "DOING",
                    key: "${type}-2",
                ],
                "${type}-3": [
                    status: "DONE",
                    key: "${type}-3",
                ],
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

    def "compute wip jira issues for non Gxp project"() {
        given:
        def data = [:]
        Project.JiraDataItem.REGULAR_ISSUE_TYPES.each { type ->
            data[type] = [
                "${type}-1": [
                    status: "TODO",
                    key: "${type}-1",
                ],
                "${type}-2": [
                    status: "DOING",
                    key: "${type}-2",
                ],
                "${type}-3": [
                    status: "DONE",
                    key: "${type}-3",
                ],
                "${type}-4": [
                    status: "CANCELLED",
                    key: "${type}-4",
                ],
            ]
        }

        data[Project.JiraDataItem.TYPE_DOCS] = [
                "${Project.JiraDataItem.TYPE_DOCS}-5": [
                    status: Project.JiraDataItem.ISSUE_STATUS_TODO,
                    key: "${Project.JiraDataItem.TYPE_DOCS}-5",
                    number: '1',
                    heading: 'Introduction',
                    documents:['SSDS']
                ],
                "${Project.JiraDataItem.TYPE_DOCS}-6": [
                    status: Project.JiraDataItem.ISSUE_STATUS_DONE,
                    key: "${Project.JiraDataItem.TYPE_DOCS}-6",
                    number: '2.1',
                    heading: 'System Design Overview',
                    documents:['SSDS']
                ],
                "${Project.JiraDataItem.TYPE_DOCS}-7": [
                    status: Project.JiraDataItem.ISSUE_STATUS_CANCELLED,
                    key: "${Project.JiraDataItem.TYPE_DOCS}-7",
                    number: '3.1',
                    heading: 'System Design Profile',
                    documents:['SSDS']
                ],
                "${Project.JiraDataItem.TYPE_DOCS}-8": [
                    status: Project.JiraDataItem.ISSUE_STATUS_TODO,
                    key: "${Project.JiraDataItem.TYPE_DOCS}-8",
                    number: '5.4',
                    heading: 'Utilisation of Existing Infrastructure Services',
                    documents:['SSDS']
                ],
                "${Project.JiraDataItem.TYPE_DOCS}-9": [
                    status: Project.JiraDataItem.ISSUE_STATUS_TODO,
                    key: "${Project.JiraDataItem.TYPE_DOCS}-9",
                    number: '1',
                    heading: 'Introduction and Purpose',
                    documents:['SSDS']
                ],
                "${Project.JiraDataItem.TYPE_DOCS}-10": [
                    status: Project.JiraDataItem.ISSUE_STATUS_TODO,
                    key: "${Project.JiraDataItem.TYPE_DOCS}-10",
                    number: '3.1',
                    heading: 'Related Business / GxP Process',
                    documents:['CSD']
                ],
                "${Project.JiraDataItem.TYPE_DOCS}-11": [
                    status: Project.JiraDataItem.ISSUE_STATUS_TODO,
                    key: "${Project.JiraDataItem.TYPE_DOCS}-11",
                    number: '5.1',
                    heading: 'Definitions',
                    documents:['CSD']
                ],
                "${Project.JiraDataItem.TYPE_DOCS}-12": [
                    status: Project.JiraDataItem.ISSUE_STATUS_TODO,
                    key: "${Project.JiraDataItem.TYPE_DOCS}-12",
                    number: '5.2',
                    heading: 'Abbreviations',
                    documents:['CSD']
                ]
            ]

        project.projectProperties.put(Project.IS_GXP_PROJECT_PROPERTY, 'false')
        def expected = [:]
        Project.JiraDataItem.REGULAR_ISSUE_TYPES.each { type ->
            expected[type] = [ "${type}-1", "${type}-2", ]
        }
        expected[Project.JiraDataItem.TYPE_DOCS] = [ "${Project.JiraDataItem.TYPE_DOCS}-5",
                                                     "${Project.JiraDataItem.TYPE_DOCS}-7",
                                                     "${Project.JiraDataItem.TYPE_DOCS}-8",
                                                     "${Project.JiraDataItem.TYPE_DOCS}-9",
                                                     "${Project.JiraDataItem.TYPE_DOCS}-10"]

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

        def expected = [:]
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

        def expected = [:]
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
            },
            "loadJiraDataSecurityVulnerabilities": {
                return [
                    "securityVulnerabilities-1": [
                        status: "DONE"
                    ],
                    "securityVulnerabilities-2": [
                        status: "DONE"
                    ],
                    "securityVulnerabilities-3": [
                        status: "DONE"
                    ],
                    "securityVulnerabilities-4": [
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

        def expected = [:]
        Project.JiraDataItem.TYPES_WITH_STATUS.each { type ->
            expected[type] = [ "${type}-1", "${type}-2" ]
        }

        def expectedMessage = "Pipeline-generated documents are watermarked '${LeVADocumentUseCase.WORK_IN_PROGRESS_WATERMARK}' since the following issues are work in progress: "
        Project.JiraDataItem.TYPES_WITH_STATUS.each { type ->
            expectedMessage += "\n\n" +
                "${ProjectMessagesUtil.insertSpaceBeforeCapitals(type.capitalize())}: ${type}-1, ${type}-2"
        }
        expectedMessage += "\n\nPlease note that for a successful Deploy to D, the above-mentioned issues need to be in status Done."

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
            },
            "loadJiraDataSecurityVulnerabilities": {
                return [
                    "securityVulnerabilities-1": [
                        status: "TODO"
                    ],
                    "securityVulnerabilities-2": [
                        status: "IN PROGRESS"
                    ],
                    "securityVulnerabilities-3": [
                        status: "DONE"
                    ],
                    "securityVulnerabilities-4": [
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
        def document = 'myDocumentType'
        def data = [project: [:], components: [:]]
        Project.JiraDataItem.TYPES_WITH_STATUS.each { type ->
            if (type != Project.JiraDataItem.TYPE_DOCS) {
                data[type] = [:]
            } else {
                data[type] = [
                    "${type}-1": [
                        status: "TODO",
                        key: "${type}-1",
                        documents: [document],
                    ],
                    "${type}-2": [
                        status: "DOING",
                        key: "${type}-2",
                        documents: [document],
                    ],
                    "${type}-3": [
                        status: "DONE",
                        key: "${type}-3",
                        documents: [document],
                    ]
                ]
            }
        }

        def expected = [:]
        Project.JiraDataItem.TYPES_WITH_STATUS.each { type ->
            if (type != Project.JiraDataItem.TYPE_DOCS) {
                expected[type] = []
            } else {
                expected[type] = ["${type}-1", "${type}-2"]
            }
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
        project.hasWipJiraIssues()

        then:
        project.getWipJiraIssues() == expected
        project.getWIPDocChapters() == [(document): [Project.JiraDataItem.TYPE_DOCS+"-1",  Project.JiraDataItem.TYPE_DOCS+"-2"]]
        project.getWIPDocChaptersForDocument(document) == [Project.JiraDataItem.TYPE_DOCS+"-1",  Project.JiraDataItem.TYPE_DOCS+"-2"]
    }

    def "fail build with open issues"() {
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

        def expected = [:]
        Project.JiraDataItem.TYPES_WITH_STATUS.each { type ->
            expected[type] = ["${type}-1", "${type}-2"]
        }

        def expectedMessage = "The pipeline failed since the following issues are work in progress " +
            "(no documents were generated): "
        Project.JiraDataItem.TYPES_WITH_STATUS.each { type ->
            expectedMessage += "\n\n${ProjectMessagesUtil.insertSpaceBeforeCapitals(type.capitalize())}: " +
                "${type}-1, ${type}-2"
        }
        expectedMessage += "\n\nPlease note that for a successful Deploy to D, the above-mentioned issues " +
            "need to be in status Done."

        project = createProject([
            "loadJiraData"    : {
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
            },
            "loadJiraDataSecurityVulnerabilities": {
                return [
                    "securityVulnerabilities-1": [
                        status: "TODO"
                    ],
                    "securityVulnerabilities-2": [
                        status: "IN PROGRESS"
                    ],
                    "securityVulnerabilities-3": [
                        status: "DONE"
                    ],
                    "securityVulnerabilities-4": [
                        status: "DONE"
                    ]
                ]
            }
        ]).init()
        project.data.buildParams.version = "1.0"
        when:
        project.load(git, jiraUseCase)

        then:
        project.hasWipJiraIssues()

        then:
        def e = thrown(OpenIssuesException)
        e.message == expectedMessage

        and:
        Project.JiraDataItem.TYPES_WITH_STATUS.each { type ->
            !expectedMessage.find("${type}-3")
        }
    }

    def "fail build with mandatory doc open issues for non-GxP project"() {
        setup:
        def data = [project: [:], components: [:]]
        Project.JiraDataItem.TYPES_WITH_STATUS.each { type ->
            data[type] = [
                "${type}-3": [
                    status: "DONE"
                ]
            ]
        }

        data.project.projectProperties = [:]
        data.project.projectProperties["PROJECT.IS_GXP"] = "false"
        data.docs = [:]
        data.docs["docs-1"] = [ documents: [a], number: b, status: "DOING"]
        def expected = [:]
        Project.JiraDataItem.REGULAR_ISSUE_TYPES.each { type ->
            expected[type] = ["${type}-1", "${type}-2"]
        }

        def expectedMessage = "The pipeline failed since the following issues are work in progress (no documents were generated): "

        expectedMessage += "\n\nDocs: docs-1"

        expectedMessage += "\n\nPlease note that for a successful Deploy to D, the above-mentioned issues need to be in status Done."
        project = createProject([
            "loadJiraData"    : {
                return data
            },
            "loadJiraDataBugs": {
                return [
                    "bugs-3": [
                        status: "DONE"
                    ]
                ]
            },
            "loadJiraDataSecurityVulnerabilities": {
                return [
                    "securityVulnerabilities-4": [
                        status: "DONE"
                    ]
                ]
            }
        ]).init()
        project.data.buildParams.version = "1.0"
        when:
        project.load(git, jiraUseCase)

        then:
        project.hasWipJiraIssues() == c

        then:
        def e = thrown(OpenIssuesException)
        e.message == expectedMessage

        and:
        Project.JiraDataItem.TYPES_WITH_STATUS.each { type ->
            !expectedMessage.find("${type}-3")
        }

        where:
        a | b || c
        "CSD" | '1' || true
        "CSD" | '3.1' || true
        "SSDS" | '1' || true
        "SSDS" | '2.1' || true
        "SSDS" | '3.1' || true
        "SSDS" | '5.4' || true
    }

    def "NOT fail build with non-mandatory doc open issues for non-GxP project"() {
        setup:
        def data = [project: [:], components: [:]]
        Project.JiraDataItem.TYPES_WITH_STATUS.each { type ->
            data[type] = [
                "${type}-3": [
                    status: "DONE"
                ]
            ]
        }

        data.project.projectProperties = [:]
        data.project.projectProperties["PROJECT.IS_GXP"] = "false"
        data.docs = [:]
        data.docs["docs-1"] = [ documents: [a], number: b, status: "DOING"]
        def expected = [:]
        Project.JiraDataItem.REGULAR_ISSUE_TYPES.each { type ->
            expected[type] = ["${type}-1", "${type}-2"]
        }

        def expectedMessage = "The pipeline failed since the following issues are work in progress (no documents were generated): "

        expectedMessage += "\n\nDocs: docs-1"
        project = createProject([
            "loadJiraData"    : {
                return data
            },
            "loadJiraDataBugs": {
                return [
                    "bugs-3": [
                        status: "DONE"
                    ]
                ]
            },
            "loadJiraDataSecurityVulnerabilities": {
                return [
                    "securityVulnerabilities-4": [
                        status: "DONE"
                    ]
                ]
            }
        ]).init()
        project.data.buildParams.version = "1.0"
        when:
        project.load(git, jiraUseCase)

        then:
        project.hasWipJiraIssues() == c

        where:
        a | b || c
        "CSD" | '2' || false
        "CSD" | '3.2' || false
        "SSDS" | '2' || false
        "SSDS" | '2.2' || false
        "SSDS" | '3.2' || false
        "SSDS" | '5.5' || false
        "CFTP" | '1' || false
        "CFTR" | '1' || false
        "DTP" | '1' || false
        "DTR" | '1' || false
        "DIL" | '1' || false
        "IVP" | '1' || false
        "IVR" | '1' || false
        "RA" | '1' || false
        "TCP" | '1' || false
        "TCR" | '1' || false
        "TIP" | '1' || false
        "TIR" | '1' || false
        "TRC" | '1' || false
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
            project     : [name: "my-project", id:'0'],
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
        1 * project.loadJiraDataBugs(*_) >> FixtureHelper.createProjectJiraDataBugs()
        2 * project.loadJiraDataTrackingDocs(*_) >> FixtureHelper.createProjectJiraDataDocs()
        1 * project.loadJiraDataIssueTypes() >> FixtureHelper.createProjectJiraDataIssueTypes()
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

        // Stubbed Method Responses limitation of not being able to spy/mock JiraUseCase for projectObj
        def jiraIssue1 = FixtureHelper.createJiraIssue("1", null, null, null, "DONE")
        jiraIssue1.fields["0"] = "1.0"
        jiraIssue1.fields.labels = [JiraUseCase.LabelPrefix.DOCUMENT+ "CSD"]
        jiraIssue1.renderedFields = [:]
        jiraIssue1.renderedFields["1"] = "<html>myContent1</html>"
        jiraIssue1.renderedFields.description = "<html>1-description</html>"

        def jira = Mock(JiraService) {
            getDocGenData(_) >> {
                return docGenData
            }
            isVersionEnabledForDelta(*_) >> { return false }
            searchByJQLQuery(*_) >> { return [ issues: [jiraIssue1]]}
            getTextFieldsOfIssue(*_) >> { return [field_0: [name: "1"]]}
        }

        def projectObj = new Project(steps, logger)
        projectObj.git = git
        projectObj.jiraUseCase = new JiraUseCase(projectObj, steps, Mock(MROPipelineUtil), jira, logger)
        projectObj.data.buildParams = FixtureHelper.createProjectBuildParams()
        projectObj.data.jira = [issueTypes: [
            (JiraUseCase.IssueTypes.DOCUMENTATION_CHAPTER): [ fields: [
                (JiraUseCase.CustomIssueFields.HEADING_NUMBER): [id:"0"],
                (JiraUseCase.CustomIssueFields.CONTENT): [id: "1"],
            ]],
            (JiraUseCase.IssueTypes.RELEASE_STATUS): [ fields: [
                (JiraUseCase.CustomIssueFields.RELEASE_VERSION): [id: "field_0"],
            ]]
        ]]
        projectObj.data.metadata = [capabilities:[[LeVADocs:[GAMPCategory: 5,templatesVersion: "1.1"]]]]

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
    }

    def "load metadata"() {
        when:
        def result = project.loadMetadata()

        // Verify annotations to the metadata.yml file are made
        def expected = new Yaml().load(new File(Project.METADATA_FILE_NAME).text)
        expected.repositories.each { repo ->
            repo.include = true
            repo.data = [ documents: [:], openshift: [:] ]
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
            capabilities:
              - LeVADocs:
                  GAMPCategory: 5
        """

        when:
        def result = project.init()

        then:
        result.getCapability("LeVADocs").templatesVersion == Project.DEFAULT_TEMPLATE_VERSION

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

    def "use old docGen report when version is not enabled for the feature"() {
        setup:
        def versionEnabled
        def jiraServiceStubs = { JiraService it ->
            it.isVersionEnabledForDelta(*_) >> {
                return versionEnabled
            }
            it.getDocGenData(*_) >> { return [project:[id:"1"]] }
            it.getDeltaDocGenData(*_) >> { return [project:[id:"1"]] }
            it.getTextFieldsOfIssue(*_) >> { return [field_0: [name: "1"]]}
        }
        project = setupWithJiraService(jiraServiceStubs)
        project.data.jira = [issueTypes: [
            (JiraUseCase.IssueTypes.RELEASE_STATUS): [ fields: [
                (JiraUseCase.CustomIssueFields.RELEASE_VERSION): [id: "field_0"],
            ]]
        ]]

        when:
        versionEnabled = false
        project.loadJiraData("projectKey")

        then:
        1 * project.getDocumentChapterData(_) >> [:]
        0 * project.loadJiraDataForCurrentVersion(*_)
        1 * project.loadFullJiraData(_)

        when:
        versionEnabled = true
        project.loadJiraData("DEMO")

        then:
        1 * project.loadJiraDataForCurrentVersion(*_)
        1 * project.getDocumentChapterData(*_) >> [:]
        0 * project.loadFullJiraData(_)

    }

    def "load saved data from the previousVersion"() {
        setup:
        def firstVersion = '1'
        def secondVersion = '2'
        def newVersionData = [
            project     : [name: "my-project", id:'0'],
            version: secondVersion,
            precedingVersions: [firstVersion],
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
        project = setupWithJiraService()

        when:
        project.loadJiraDataForCurrentVersion("KEY", secondVersion)

        then:
        1 * project.loadVersionJiraData(*_) >> newVersionData
        1 * project.loadSavedJiraData(firstVersion) >> [:]

    }

    def "load only new data for initial release"() {
        given:
        def firstVersion = '1'
        def newVersionData = [
            project     : [name: "my-project", id:'0'],
            version: firstVersion,
            precedingVersions: [],
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
        project = setupWithJiraService()

        when:
        project.data.jira = project.loadJiraDataForCurrentVersion("KEY", firstVersion)

        then:
        1 * project.loadVersionJiraData(*_) >>  newVersionData

        then:
        0 * project.loadJiraDataForCurrentVersion(_)
    }

    def "do initial load if no previousVersion information is listed"() {
        given:
        def firstVersion = '1'
        def noPreviousReleases1 = [
            project     : [name: "my-project", id:'0'],
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
        project = setupWithJiraService()

        when:
        project.loadJiraDataForCurrentVersion("KEY", firstVersion)

        then:
        1 * project.loadVersionJiraData(*_) >> noPreviousReleases1

        then:
        0 * project.loadSavedJiraData(_)

    }
    def "do initial load if no previousVersions information is empty"() {
        given:
        def firstVersion = '1'

        def noPreviousReleases1 = [
            project     : [name: "my-project", id:'0'],
            version: firstVersion,
            precedingVersions: [],
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
        project = setupWithJiraService()

        when:
        project.loadJiraDataForCurrentVersion("KEY", firstVersion)

        then:
        1 * project.loadVersionJiraData(*_) >> noPreviousReleases1

        then:
        0 * project.loadSavedJiraData(_)

    }

    def "merge new test added"() {
        given:
        def firstVersion = '1'
        def secondVersion = '2'

        def cmp = {  name ->  [key: "CMP-${name}" as String, name: "Component 1"] }
        def req = {  name, String version = null ->  [key: "REQ-${name}" as String, description:name, versions:[version]] }
        def ts = {  name, String version = null ->  [key: "TS-${name}" as String, description:name, versions:[version]] }
        def rsk = {  name, String version = null ->  [key: "RSK-${name}" as String, description:name, versions:[version]] }
        def tst = {  name, String version = null ->  [key: "TST-${name}" as String, description:name, versions:[version]] }
        def mit = {  name, String version = null ->  [key: "MIT-${name}" as String, description:name, versions:[version]] }

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
            project     : [name: "my-project", id:'0'],
            version: secondVersion,
            precedingVersions: [firstVersion],
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
        project = setupWithJiraService()

        when:
        project.data.jira = project.loadJiraDataForCurrentVersion("KEY", secondVersion)
        project.data.jira = project.convertJiraDataToJiraDataItems(project.data.jira)
        project.data.jiraResolved = project.resolveJiraDataItemReferences(project.data.jira)

        then:
        1 * project.loadSavedJiraData(_) >> storedData
        1 * project.loadVersionJiraData(*_) >> newVersionData

        then:
        1 * project.mergeJiraData(storedData, newVersionData)

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
        issueListIsEquals(rskResult.getResolvedSystemRequirements(), [req1Updated])
        issueListIsEquals(rskResult.getResolvedTests(), [tst1, tst2])
    }

    def "merge new risk and test added"() {
        given:
        def firstVersion = '1'
        def secondVersion = '2'

        def cmp = {  name ->  [key: "CMP-${name}" as String, name: "Component 1"] }
        def req = {  name, String version = null ->  [key: "REQ-${name}" as String, description:name, versions:[version]] }
        def ts = {  name, String version = null ->  [key: "TS-${name}" as String, description:name, versions:[version]] }
        def rsk = {  name, String version = null ->  [key: "RSK-${name}" as String, description:name, versions:[version]] }
        def tst = {  name, String version = null ->  [key: "TST-${name}" as String, description:name, versions:[version]] }
        def mit = {  name, String version = null ->  [key: "MIT-${name}" as String, description:name, versions:[version]] }

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
            project     : [name: "my-project", id:'0'],
            version: secondVersion,
            precedingVersions: [firstVersion],
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
        project = setupWithJiraService()

        when:
        project.data.jira = project.loadJiraDataForCurrentVersion("KEY", secondVersion)
        project.data.jira = project.convertJiraDataToJiraDataItems(project.data.jira)
        project.data.jiraResolved = project.resolveJiraDataItemReferences(project.data.jira)

        then:
        1 * project.loadSavedJiraData(_) >> storedData
        1 * project.loadVersionJiraData(*_) >> newVersionData

        then:
        1 * project.mergeJiraData(storedData, newVersionData)

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
        def req = {  name, String version = null ->  [key: "REQ-${name}" as String, description:name, versions:[version]] }
        def ts = {  name, String version = null ->  [key: "TS-${name}" as String, description:name, versions:[version]] }
        def rsk = {  name, String version = null ->  [key: "RSK-${name}" as String, description:name, versions:[version]] }
        def tst = {  name, String version = null ->  [key: "TST-${name}" as String, description:name, versions:[version]] }
        def mit = {  name, String version = null ->  [key: "MIT-${name}" as String, description:name, versions:[version]] }

        def req0 = req('betaReq', betaVersion)
        def req1 = req('1', firstVersion)
        def req2 = req('midReq', firstVersion)
        def req3 = req('newerReq', secondVersion)
        def rsk1 = rsk('toModify', firstVersion)
        def tst1 = tst('1', secondVersion)
        def rsk2 = rsk('modification', secondVersion)


        req1 << [risks: [rsk1.key], tests: [tst1.key]]
        req2 << [predecessors: [req0.key], expandedPredecessors: [[key: req0.key, versions: req0.versions]]]
        rsk1 << [requirements: [req1.key], tests: [tst1.key]]
        tst1 << [requirements: [req1.key], risks: [rsk1.key]]

        rsk2 << [requirements: [req1.key], tests: [tst1.key], predecessors: [rsk1.key],
                 expandedPredecessors: [[key: rsk1.key, versions: rsk1.versions]]]
        req3 << [predecessors: [req2.key]]
        def req1Updated = req1.clone() + [risks: [rsk2.key]]
        def tst1Updated = tst1.clone() + [risks: [rsk2.key]]
        def rsk2WithDetails = rsk2.clone()
        rsk2WithDetails << [expandedPredecessors: [[key: rsk1.key, versions: rsk1.versions]]]
        def req3withDetails = req3.clone()
        req3withDetails << [expandedPredecessors: [[key: req2.key, versions: req2.versions],
                                                   [key: req0.key, versions: req0.versions]]]

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
            project     : [name: "my-project", id:'0'],
            version: secondVersion,
            precedingVersions: [firstVersion],
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
        project = setupWithJiraService()

        when:
        project.data.jira = project.loadJiraDataForCurrentVersion("KEY", secondVersion)
        project.data.jira = project.convertJiraDataToJiraDataItems(project.data.jira)
        project.data.jiraResolved = project.resolveJiraDataItemReferences(project.data.jira)

        then:
        1 * project.loadSavedJiraData(_) >> storedData
        1 * project.loadVersionJiraData(*_) >> newVersionData

        then:
        1 * project.mergeJiraData(storedData, newVersionData)

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

    def "merge modification of a requirement and related issues"() {
        given:
        def firstVersion = '1.0'
        def secondVersion = '2.0'

        def req = {  name, String version = null ->  [key: "REQ-${name}" as String, description:name, versions:[version]] }
        def ts = {  name, String version = null ->  [key: "TS-${name}" as String, description:name, versions:[version]] }
        def rsk = {  name, String version = null ->  [key: "RSK-${name}" as String, description:name, versions:[version]] }
        def tst = {  name, String version = null ->  [key: "TST-${name}" as String, description:name, versions:[version]] }
        def mit = {  name, String version = null ->  [key: "MIT-${name}" as String, description:name, versions:[version]] }

        def req1 = req('1', firstVersion)
        def tst1 = tst('1', firstVersion)
        def tst2 = tst('2', firstVersion)
        def ts1 = ts('1', firstVersion)
        def rsk1 = rsk('1', firstVersion)
        def mit1 = mit('1', firstVersion)
        def req2 = req('2', secondVersion)
        def tst3 = tst('3', secondVersion)
        def tst4 = tst('4', secondVersion)
        def ts2 = ts('2', secondVersion)
        def rsk2 = rsk('2', secondVersion)
        def mit2 = mit('2', secondVersion)

        req1 << [tests: [tst1.key, tst2.key], techSpecs: [ts1.key], risks: [rsk1.key], mitigations: [mit1.key]]
        tst1 << [requirements: [req1.key], techSpecs: [ts1.key]]
        tst2 << [requirements: [req1.key], techSpecs: [ts1.key]]
        ts1 << [requirements: [req1.key], tests: [tst1.key, tst2.key]]
        rsk1 << [requirements: [req1.key], mitigations: [mit1.key]]
        mit1 << [requirements: [req1.key], risks: [rsk1.key]]
        req2 << [predecessors: [req1.key], tests: [tst2.key,tst3.key,tst4.key], techSpecs: [ts2.key], risks: [rsk2.key], mitigations: [mit2.key]]
        tst3 << [predecessors: [tst1.key], requirements: [req2.key], techSpecs: [ts2.key]]
        tst4 << [requirements: [req2.key]]
        rsk2 << [predecessors: [rsk1.key], requirements: [req2.key], mitigations: [mit2.key]]
        mit2 << [predecessors: [mit1.key], requirements: [req2.key], risks: [rsk2.key]]
        ts2 << [predecessors: [ts1.key], requirements: [req2.key], tests: [tst2.key, tst3.key]]

        def req2Updated = req2.clone()
        req2Updated  << [expandedPredecessors: [[key: req1.key, versions: req1.versions]]]
        def tst2Updated = tst2.clone()
        def tst3Updated = tst3.clone()
        tst3Updated << [expandedPredecessors: [[key: tst1.key, versions: tst1.versions]]]
        def rsk2Updated = rsk2.clone()
        rsk2Updated << [expandedPredecessors: [[key: rsk1.key, versions: rsk1.versions]]]
        def mit2Updated = mit2.clone()
        mit2Updated << [expandedPredecessors: [[key: mit1.key, versions: mit1.versions]]]
        def ts2Updated = ts2.clone()
        ts2Updated  << [expandedPredecessors: [[key: ts1.key, versions: ts1.versions]]]

        def storedData = [
            components  : [:],
            epics       : [:],
            mitigations : [(mit1.key): mit1],
            requirements: [(req1.key): req1],
            risks       : [(rsk1.key): rsk1],
            tests       : [(tst1.key): tst1, (tst2.key): tst2],
            techSpecs   : [(ts1.key): ts1],
            docs        : [:]
        ]
        def newVersionData = [
            project     : [name: "my-project", id:'0'],
            version: secondVersion,
            precedingVersions: [firstVersion],
            bugs        : [:],
            components  : [:],
            epics       : [:],
            mitigations : [(mit2.key):mit2],
            requirements: [(req2.key):req2],
            risks       : [(rsk2.key):rsk2],
            tests       : [(tst3.key):tst3, (tst4.key):tst4],
            techSpecs   : [(ts2.key):ts2],
            docs        : [:]
        ]

        def mergedData = [
            components  : [:],
            epics       : [:],
            mitigations : [(mit2Updated.key): mit2Updated],
            requirements: [(req2Updated.key): req2Updated],
            risks       : [(rsk2Updated.key): rsk2Updated],
            tests       : [(tst3Updated.key): tst3Updated, (tst4.key): tst4, (tst2Updated.key): tst2Updated],
            techSpecs   : [(ts2Updated.key): ts2Updated],
            docs        : [:]
        ]
        project = setupWithJiraService()

        when:
        project.data.jira = project.loadJiraDataForCurrentVersion("KEY", secondVersion)
        project.data.jira = project.convertJiraDataToJiraDataItems(project.data.jira)
        project.data.jiraResolved = project.resolveJiraDataItemReferences(project.data.jira)

        then:
        1 * project.loadSavedJiraData(_) >> storedData
        1 * project.loadVersionJiraData(*_) >> newVersionData

        then:
        1 * project.mergeJiraData(storedData, newVersionData)

        then:
        issueListIsEquals(project.components, mergedData.components.values() as List)
        issueListIsEquals(project.mitigations, mergedData.mitigations.values() as List)
        issueListIsEquals(project.getTechnicalSpecifications(), mergedData.techSpecs.values() as List)
        issueListIsEquals(project.getSystemRequirements(), mergedData.requirements.values() as List)
        issueListIsEquals(project.risks, mergedData.risks.values() as List)
        issueListIsEquals(project.tests, mergedData.tests.values() as List)

        def reqResult = project.getSystemRequirements().first()
        reqResult.tests == req2Updated.tests
        reqResult.techSpecs == req2Updated.techSpecs
        reqResult.risks == req2Updated.risks
        reqResult.mitigations == req2Updated.mitigations
        issueListIsEquals(reqResult.getResolvedTests(), [tst4, tst3Updated, tst2Updated])
        issueListIsEquals(reqResult.getResolvedTechnicalSpecifications(), [ts2Updated])
        issueListIsEquals(reqResult.getResolvedRisks(), [rsk2Updated])
        issueListIsEquals(reqResult.getResolvedMitigations(), [mit2Updated])

        def tstResult = project.tests.first()
        tstResult.requirements == tst3Updated.requirements
        tstResult.techSpecs == tst3Updated.techSpecs
        issueListIsEquals(tstResult.getResolvedSystemRequirements(), [req2Updated])
        issueListIsEquals(tstResult.getResolvedTechnicalSpecifications(), [ts2Updated])

        def tsResult = project.getTechnicalSpecifications().last()
        tsResult.requirements == ts2Updated.requirements
        tsResult.tests == ts2Updated.tests
        issueListIsEquals(tsResult.getResolvedSystemRequirements(), [req2Updated])
        issueListIsEquals(tsResult.getResolvedTests(), [tst3Updated, tst2Updated])

        def rskResult = project.risks.first()
        rskResult.requirements == rsk2Updated.requirements
        rskResult.mitigations == rsk2Updated.mitigations
        issueListIsEquals(rskResult.getResolvedSystemRequirements(), [req2Updated])
        issueListIsEquals(rskResult.getResolvedMitigations(), [mit2Updated])

        def mitResult = project.mitigations.first()
        mitResult.requirements == mit2Updated.requirements
        mitResult.risks == mit2Updated.risks
        issueListIsEquals(mitResult.getResolvedSystemRequirements(), [req2Updated])
        issueListIsEquals(mitResult.getResolvedRisks(), [rsk2Updated])
    }

    def "merge requirement with risk succeeded and test discontinued"() {
        given:
        def firstVersion = '1.0'
        def secondVersion = '2.0'

        def req = {  name, String version = null ->  [key: "REQ-${name}" as String, description:name, versions:[version]] }
        def rsk = {  name, String version = null ->  [key: "RSK-${name}" as String, description:name, versions:[version]] }
        def tst = {  name, String version = null ->  [key: "TST-${name}" as String, description:name, versions:[version]] }

        def req1 = req('1', firstVersion)
        def rsk1 = rsk('1', firstVersion)
        def tst1 = tst('1', firstVersion)
        def req2 = req('2', secondVersion)
        def rsk2 = rsk('2', secondVersion)

        req1 << [risks: [rsk1.key], tests: [tst1.key]]
        rsk1 << [requirements: [req1.key], tests: [tst1.key]]
        tst1 << [requirements: [req1.key], risks: [rsk1.key]]
        req2 << [predecessors: [req1.key], risks: [rsk2.key]]
        rsk2 << [predecessors: [rsk1.key], requirements: [req2.key]]

        def req2Updated = req2.clone()
        req2Updated  << [expandedPredecessors: [[key: req1.key, versions: req1.versions]]]
        def rsk2Updated = rsk2.clone()
        rsk2Updated  << [expandedPredecessors: [[key: rsk1.key, versions: rsk1.versions]]]

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
            project     : [name: "my-project", id:'0'],
            version: secondVersion,
            precedingVersions: [firstVersion],
            bugs        : [:],
            components  : [:],
            epics       : [:],
            mitigations : [:],
            requirements: [(req2.key): req2],
            risks       : [(rsk2.key): rsk2],
            tests       : [:],
            techSpecs   : [:],
            docs        : [:],
            discontinuedKeys: [tst1.key]
        ]

        def mergedData = [
            components  : [:],
            epics       : [:],
            mitigations : [:],
            requirements: [(req2Updated.key): req2Updated],
            risks       : [(rsk2Updated.key): rsk2Updated],
            tests       : [:],
            techSpecs   : [:],
            docs        : [:],
            discontinuations: [tst1.key]
        ]
        project = setupWithJiraService()

        when:
        project.data.jira = project.loadJiraDataForCurrentVersion("KEY", secondVersion)
        project.data.jira = project.convertJiraDataToJiraDataItems(project.data.jira)
        project.data.jiraResolved = project.resolveJiraDataItemReferences(project.data.jira)

        then:
        1 * project.loadSavedJiraData(_) >> storedData
        1 * project.loadVersionJiraData(*_) >> newVersionData

        then:
        1 * project.mergeJiraData(storedData, newVersionData)

        then:
        issueListIsEquals(project.components, mergedData.components.values() as List)
        issueListIsEquals(project.getSystemRequirements(), mergedData.requirements.values() as List)
        issueListIsEquals(project.risks, mergedData.risks.values() as List)
        issueListIsEquals(project.tests, mergedData.tests.values() as List)

        def reqResult = project.getSystemRequirements().first()
        reqResult.tests == req2Updated.tests
        reqResult.risks == req2Updated.risks
        //issueListIsEquals(reqResult.getResolvedTests(), [])
        issueListIsEquals(reqResult.getResolvedRisks(), [rsk2Updated])

        def rskResult = project.risks.first()
        rskResult.requirements == rsk2Updated.requirements
        rskResult.tests == rsk2Updated.tests
        issueListIsEquals(rskResult.getResolvedSystemRequirements(), [req2Updated])
        //issueListIsEquals(rskResult.getResolvedTests(), [])
    }

    def "merge deletion of a test"() {
        given:
        def firstVersion = '1'
        def secondVersion = '2'

        def cmp = {  name ->  [key: "CMP-${name}" as String, name: "Component 1"] }
        def req = {  name, String version = null ->  [key: "REQ-${name}" as String, description:name, versions:[version]] }
        def ts = {  name, String version = null ->  [key: "TS-${name}" as String, description:name, versions:[version]] }
        def rsk = {  name, String version = null ->  [key: "RSK-${name}" as String, description:name, versions:[version]] }
        def tst = {  name, String version = null ->  [key: "TST-${name}" as String, description:name, versions:[version]] }
        def mit = {  name, String version = null ->  [key: "MIT-${name}" as String, description:name, versions:[version]] }

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
            project     : [name: "my-project", id:'0'],
            version: secondVersion,
            precedingVersions: [firstVersion],
            bugs        : [:],
            components  : [:],
            epics       : [:],
            mitigations : [:],
            requirements: [:],
            risks       : [:],
            tests       : [:],
            techSpecs   : [:],
            docs        : [:],
            discontinuedKeys: [tst2.key]
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
        project = setupWithJiraService()

        when:
        project.data.jira = project.loadJiraData("my-project")
        project.data.jira = project.convertJiraDataToJiraDataItems(project.data.jira)
        project.data.jiraResolved = project.resolveJiraDataItemReferences(project.data.jira)

        then:
        1 * project.loadVersionJiraData(*_) >> newVersionData
        1 * project.loadSavedJiraData(_) >> storedData

        then:
        1 * project.mergeJiraData(storedData, newVersionData)

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
        issueListIsEquals(rskResult.getResolvedSystemRequirements(), [req1Updated])
        issueListIsEquals(rskResult.getResolvedTests(), [tst1])
    }

    def "assign versions to components"() {
        given:
        def firstVersion = '1'

        def cmp = { name, List<String> versions = []-> [key: "CMP-${name}" as String, name: name, versions: versions]}
        def cmp1 = cmp('front')

        def newVersionData = [
            project     : [name: "my-project", id:'0'],
            version: firstVersion,
            precedingVersions: [],
            bugs        : [:],
            components  : [(cmp1.key):cmp1],
            epics       : [:],
            mitigations : [:],
            requirements: [:],
            risks       : [:],
            tests       : [:],
            techSpecs   : [:],
            docs        : [:],
            discontinuations: []
        ]

        def mergedData = [
            components  : [(cmp1.key):cmp1 + [versions: [firstVersion]]],
            epics       : [:],
            mitigations : [:],
            requirements: [:],
            risks       : [:],
            tests       : [:],
            techSpecs   : [:],
            docs        : [:],
            discontinuations: []
        ]
        project = setupWithJiraService()

        when:
        project.data.jira = project.loadJiraData("my-project")

        then:
        1 * project.loadVersionJiraData(*_) >> newVersionData

        then:
        def component = project.getComponents().first()
        component.version == mergedData.components[cmp1.key].version
    }

    def "merge a new component"() {
        given:
        def firstVersion = '1'
        def secondVersion = '2'

        def cmp = { name, List<String> versions = []-> [key: "CMP-${name}" as String, name: name, versions: versions]}
        def cmp1 = cmp('front')
        def cmp2 = cmp('new')

        def cmp1wv = cmp1.clone() + [versions: [firstVersion]]
        def cmp2wv = cmp2.clone() + [versions: [secondVersion]]

        def storedData = [
            components  : [(cmp1wv.key):cmp1wv],
            epics       : [:],
            mitigations : [:],
            requirements: [:],
            risks       : [:],
            tests       : [:],
            techSpecs   : [:],
            docs        : [:]
        ]
        def newVersionData = [
            project     : [name: "my-project", id:'0'],
            version: secondVersion,
            precedingVersions: [firstVersion],
            bugs        : [:],
            components  : [(cmp1.key):cmp1, (cmp2.key):cmp2],
            epics       : [:],
            mitigations : [:],
            requirements: [:],
            risks       : [:],
            tests       : [:],
            techSpecs   : [:],
            docs        : [:],
            discontinuations: []
        ]

        def mergedData = [
            components  : [(cmp1wv.key):cmp1wv, (cmp2wv.key):cmp2wv],
            epics       : [:],
            mitigations : [:],
            requirements: [:],
            risks       : [:],
            tests       : [:],
            techSpecs   : [:],
            docs        : [:],
            discontinuations: []
        ]
        project = setupWithJiraService()

        when:
        project.data.jira = project.loadJiraData("my-project")

        then:
        1 * project.loadSavedJiraData(_) >> storedData
        1 * project.loadVersionJiraData(*_) >> newVersionData

        then:
        issueListIsEquals(project.components, mergedData.components.values() as List)
    }

    def "merge the links of a component"() {
        given:
        def firstVersion = '1'
        def secondVersion = '2'

        def cmp = { name, List<String> versions = []-> [key: "CMP-${name}" as String, name: name, versions: versions]}
        def req = {  name, String version = null ->  [key: "REQ-${name}" as String, description:name, versions:[version]] }
        def req1 = req('1', firstVersion)
        def req2 = req('2', firstVersion)
        def req3 = req('3', secondVersion)

        def cmp1 = cmp('front')
        def cmp2 = cmp('new')

        req1 << [components: [cmp1.key]]
        req2 << [components: [cmp2.key]]
        req3 << [components: [cmp1.key]]

        def cmp1wv = cmp1.clone() + [versions: [firstVersion]]
        def cmp2wv = cmp2.clone() + [versions: [secondVersion]]

        def storedData = [
            components  : [(cmp1wv.key):cmp1wv + [requirements: [req1.key]], (cmp2wv.key):cmp2wv + [requirements: [req2.key]]],
            epics       : [:],
            mitigations : [:],
            requirements: [(req1.key): req1, (req2.key): req2],
            risks       : [:],
            tests       : [:],
            techSpecs   : [:],
            docs        : [:]
        ]
        def newVersionData = [
            project     : [name: "my-project", id:'0'],
            version: secondVersion,
            precedingVersions: [firstVersion],
            bugs        : [:],
            components  : [(cmp1.key):cmp1 + [requirements: [req3.key]], (cmp2.key):cmp2],
            epics       : [:],
            mitigations : [:],
            requirements: [(req3.key): req3],
            risks       : [:],
            tests       : [:],
            techSpecs   : [:],
            docs        : [:],
            discontinuations: []
        ]

        def mergedData = [
            components  : [(cmp1wv.key):cmp1wv + [requirements: [req1.key, req3.key]], (cmp2wv.key):cmp2wv+ [requirements: [req2.key]]],
            epics       : [:],
            mitigations : [:],
            requirements: [(req1.key): req1, (req2.key): req2, (req3.key): req3],
            risks       : [:],
            tests       : [:],
            techSpecs   : [:],
            docs        : [:],
            discontinuations: []
        ]
        project = setupWithJiraService()

        when:
        project.data.jira = project.loadJiraData("my-project")

        then:
        1 * project.loadSavedJiraData(_) >> storedData
        1 * project.loadVersionJiraData(*_) >> newVersionData

        then:
        issueListIsEquals(project.components, mergedData.components.values() as List)
    }

    def "merge a discontinued component"() {
        given:
        def firstVersion = '1'
        def secondVersion = '2'
        def req = {  name, String version = null ->  [key: "REQ-${name}" as String, description:name, versions:[version]] }
        def req1 = req('1', firstVersion) + [components: []]
        def cmp = { name, List<String> versions = []-> [key: "CMP-${name}" as String, name: name, versions: versions]}
        def cmp1wv = cmp('front') + [versions:[firstVersion], requirements: [req1.key]]
        def cmp2 = cmp('new')
        def cmp2Updated = cmp2.clone() + [versions: [secondVersion]]

        def storedData = [
            components  : [(cmp1wv.key):cmp1wv],
            epics       : [:],
            mitigations : [:],
            requirements: [(req1.key): req1 + [components: [cmp1wv.key]]],
            risks       : [:],
            tests       : [:],
            techSpecs   : [:],
            docs        : [:]
        ]
        def newVersionData = [
            project     : [name: "my-project", id: "0"],
            version: secondVersion,
            precedingVersions: [firstVersion],
            bugs        : [:],
            components  : [(cmp2.key):cmp2],
            epics       : [:],
            mitigations : [:],
            requirements: [:],
            risks       : [:],
            tests       : [:],
            techSpecs   : [:],
            docs        : [:],
            discontinuations: []
        ]

        def mergedData = [
            components  : [(cmp2Updated.key):cmp2Updated],
            epics       : [:],
            mitigations : [:],
            requirements: [(req1.key): req1],
            risks       : [:],
            tests       : [:],
            techSpecs   : [:],
            docs        : [:],
            discontinuations: [cmp1wv.key]
        ]
        project = setupWithJiraService()

        when:
        project.data.jira = project.loadJiraData("my-project")

        then:
        1 * project.loadVersionJiraData(*_) >> newVersionData
        1 * project.loadSavedJiraData(_) >> storedData

        then:
        issueListIsEquals(project.components, mergedData.components.values() as List)
        issueListIsEquals(project.getSystemRequirements(), mergedData.requirements.values() as List)
    }

    Boolean issueIsEquals(Map issueA, Map issueB) {
        issueA == issueB
    }

    Boolean issueListIsEquals(List issuesA, List issuesB) {
        if (issuesA.size() != issuesB.size()) return false
        def issuesBKeys = issuesB.collect { it.key }
        def areEquals = issuesA.collect { issueA ->
            if (! issuesBKeys.contains(issueA.key)) return false
            def correspondentIssueB = issuesB.find { it.key == issueA.key }
            issueIsEquals(issueA, correspondentIssueB)
        }
        return areEquals.isEmpty() || areEquals.contains(true)
    }

    Project setupWithJiraService(Closure jiraMockedMethods = null) {
        def jiraMockedM = jiraMockedMethods ?: { JiraService it ->
            it.isVersionEnabledForDelta(*_) >> { return true }
        }
        def projectObj = new Project(steps, logger)
        projectObj.git = git
        def jira = Mock(JiraService) { jiraMockedM(it) }
        JiraUseCase jiraUseCase = Spy(JiraUseCase, constructorArgs: [projectObj, steps, Mock(MROPipelineUtil), jira, logger])
        jiraUseCase.updateJiraReleaseStatusBuildNumber(*_) >> null
        projectObj.jiraUseCase = jiraUseCase
        projectObj.data.buildParams = FixtureHelper.createProjectBuildParams()
        def projectKey = "DEMO"

        Project spied =  Spy(projectObj)
        spied.getJiraProjectKey() >> projectKey
        spied.loadVersionDataFromJira(_) >> {String versionName -> [id: 1, name: versionName] }
        spied.getCapability('LeVADocs') >> [templatesVersion: '1.1']
        return spied
    }

    def "returns document chapters for a document"() {
        given:
        def document = 'myDocumentType'
        def nodoc = [
            nodoc1: [key:'nodoc1', documents:[]],
            nodoc2: [key:'nodoc2'],
        ]
        def onedoc = [onedoc: [key:'onedoc', documents:[document]]]
        def twodoc = [twodoc: [key:'twodoc', documents:[document,'anotherdoc']]]
        def otherdoc = [otherdoc: [key:'otherdoc', documents:['anotherdoc']]]

        when:
        project.data.jira[Project.JiraDataItem.TYPE_DOCS] = onedoc
        def result = project.getDocumentChaptersForDocument(document)

        then:
        result.size() == 1

        when:
        project.data.jira[Project.JiraDataItem.TYPE_DOCS] = twodoc
        result = project.getDocumentChaptersForDocument(document)

        then:
        result.size() == 1

        when:
        project.data.jira[Project.JiraDataItem.TYPE_DOCS] = onedoc + twodoc
        result = project.getDocumentChaptersForDocument(document)

        then:
        result.size() == 2

        when:
        project.data.jira[Project.JiraDataItem.TYPE_DOCS] = nodoc
        result = project.getDocumentChaptersForDocument(document)

        then:
        result.size() == 0

        when:
        project.data.jira[Project.JiraDataItem.TYPE_DOCS] = otherdoc
        result = project.getDocumentChaptersForDocument(document)

        then:
        result.size() == 0

        when:
        project.data.jira[Project.JiraDataItem.TYPE_DOCS] = onedoc + twodoc + nodoc + otherdoc
        result = project.getDocumentChaptersForDocument(document)

        then:
        result.size() == 2
    }

    def "compute WIP document chapters per document"() {
        def issue = { String key, String status, List<String> docs ->
            [(key): [ documents: docs, status: status, key: key ]]
        }

        def data = [
            (Project.JiraDataItem.TYPE_DOCS): issue(
                      'done1', Project.JiraDataItem.ISSUE_STATUS_DONE, ['CSD', 'SSDS']) +
                issue('done2', Project.JiraDataItem.ISSUE_STATUS_DONE, ['DTP']) +
                issue('canceled', Project.JiraDataItem.ISSUE_STATUS_DONE, ['DTP']) +
                issue('undone1', 'WORK IN PROGress', ['CSD', 'SSDS']) +
                issue('undone2', 'Some custom status', ['SSDS']) +
                issue('undone3', 'TO DO', ['DTP'])
        ]
        def expected = [ CSD: ['undone1'], SSDS: ['undone1', 'undone2'], DTP: ['undone3']]

        when:
        def result = project.computeWipDocChapterPerDocument(data)

        then:
        result == expected
    }

    def "compute WIP document chapters per document for non Gxp docs - all mandatory issues done"() {
        given:
        def issue = { String key, String number, String heading, String status,
                      List<String> docs -> [(key): [ documents: docs, status: status, key: key, number: number, heading: heading ]]
        }

        def data = [
            (Project.JiraDataItem.TYPE_DOCS): issue('77', '1', 'Introduction',
                Project.JiraDataItem.ISSUE_STATUS_DONE, ['SSDS']) +
                issue('76', '2.1', 'System Design Overview',
                    Project.JiraDataItem.ISSUE_STATUS_DONE, ['SSDS']) +
                issue('73', '3.1', 'System Design Profile',
                    Project.JiraDataItem.ISSUE_STATUS_DONE, ['SSDS']) +
                issue('67', '5.4', 'Utilisation of Existing Infrastructure Services',
                    Project.JiraDataItem.ISSUE_STATUS_DONE, ['SSDS']) +
                issue('66', '6.1', 'Development Environment',
                    Project.JiraDataItem.ISSUE_STATUS_TODO, ['SSDS']) +
                issue('42', '1', 'Introduction and Purpose',
                    Project.JiraDataItem.ISSUE_STATUS_DONE, ['CSD']) +
                issue('40', '3.1', 'Related Business / GxP Process',
                    Project.JiraDataItem.ISSUE_STATUS_DONE, ['CSD']) +
                issue('39', '5.1', 'Definitions',
                    Project.JiraDataItem.ISSUE_STATUS_TODO, ['CSD']) +
                issue('38', '5.2', 'Abbreviations',
                    Project.JiraDataItem.ISSUE_STATUS_TODO, ['CSD'])
        ]
        project.projectProperties.put(Project.IS_GXP_PROJECT_PROPERTY, 'false')
        def expected = [:]

        when:
        def result = project.computeWipDocChapterPerDocument(data)

        then:
        result == expected
    }

    def "compute WIP document chapters per document for non Gxp docs - one mandatory issue to do and one cancelled"() {
        given:
        def issue = { String key, String number, String heading, String status,
                      List<String> docs -> [(key): [ documents: docs, status: status, key: key, number: number, heading: heading ]]
        }

        def data = [
            (Project.JiraDataItem.TYPE_DOCS): issue('77', '1', 'Introduction',
                Project.JiraDataItem.ISSUE_STATUS_TODO, ['SSDS']) +
                issue('76', '2.1', 'System Design Overview',
                    Project.JiraDataItem.ISSUE_STATUS_CANCELLED, ['SSDS']) +
                issue('73', '3.1', 'System Design Profile',
                    Project.JiraDataItem.ISSUE_STATUS_DONE, ['SSDS']) +
                issue('67', '5.4', 'Utilisation of Existing Infrastructure Services',
                    Project.JiraDataItem.ISSUE_STATUS_DONE, ['SSDS']) +
                issue('42', '1', 'Introduction and Purpose',
                    Project.JiraDataItem.ISSUE_STATUS_DONE, ['CSD']) +
                issue('40', '3.1', 'Related Business / GxP Process',
                    Project.JiraDataItem.ISSUE_STATUS_DONE, ['CSD']) +
                issue('39', '5.1', 'Definitions',
                    Project.JiraDataItem.ISSUE_STATUS_TODO, ['CSD']) +
                issue('38', '5.2', 'Abbreviations',
                    Project.JiraDataItem.ISSUE_STATUS_TODO, ['CSD'])
        ]
        project.projectProperties.put(Project.IS_GXP_PROJECT_PROPERTY, 'false')
        def expected = [SSDS:['77', '76']]

        when:
        def result = project.computeWipDocChapterPerDocument(data)

        then:
        result == expected
    }

    def "compute WIP document chapters per document for non Gxp docs - all issues to do"() {
        given:
        def issue = { String key, String number, String heading, String status,
                      List<String> docs -> [(key): [ documents: docs, status: status, key: key, number: number, heading: heading ]]
        }

        def data = [
            (Project.JiraDataItem.TYPE_DOCS): issue('77', '1', 'Introduction',
                Project.JiraDataItem.ISSUE_STATUS_TODO, ['SSDS']) +
                issue('76', '2.1', 'System Design Overview',
                    Project.JiraDataItem.ISSUE_STATUS_TODO, ['SSDS']) +
                issue('73', '3.1', 'System Design Profile',
                    Project.JiraDataItem.ISSUE_STATUS_TODO, ['SSDS']) +
                issue('67', '5.4', 'Utilisation of Existing Infrastructure Services',
                    Project.JiraDataItem.ISSUE_STATUS_TODO, ['SSDS']) +
                issue('42', '1', 'Introduction and Purpose',
                    Project.JiraDataItem.ISSUE_STATUS_TODO, ['CSD']) +
                issue('40', '3.1', 'Related Business / GxP Process',
                    Project.JiraDataItem.ISSUE_STATUS_TODO, ['CSD']) +
                issue('39', '5.1', 'Definitions',
                    Project.JiraDataItem.ISSUE_STATUS_DONE, ['CSD']) +
                issue('38', '5.2', 'Abbreviations',
                    Project.JiraDataItem.ISSUE_STATUS_DONE, ['CSD'])
        ]
        project.projectProperties.put(Project.IS_GXP_PROJECT_PROPERTY, 'false')
        def expected = [SSDS:['77', '76', '73', '67'], CSD:['42', '40']]

        when:
        def result = project.computeWipDocChapterPerDocument(data)

        then:
        result == expected
    }

    def "assert if versioning is enabled for the project"() {
        given:
        def versionEnabled
        def jiraServiceStubs = { JiraService it ->
            it.isVersionEnabledForDelta(*_) >> {
                return versionEnabled
            }
        }
        def project = setupWithJiraService( jiraServiceStubs )

        when:
        versionEnabled = true
        def result = project.checkIfVersioningIsEnabled('project', 'version')

        then:
        result

        when:
        versionEnabled = false
        result = project.checkIfVersioningIsEnabled('project', 'version')

        then:
        !result

        when:
        versionEnabled = false
        result = project.checkIfVersioningIsEnabled('project', 'version')

        then:
        project.getCapability('LeVADocs') >> [templatesVersion: '1.0']

        then:
        !result

        when:
        versionEnabled = true
        result = project.checkIfVersioningIsEnabled('project', 'version')

        then:
        project.getCapability('LeVADocs') >> [templatesVersion: '1.0']

        then:
        !result



    }

    def "get version for document type"() {
        given:
        def project = new Project(null, null)
        def versions = [
            CSD: 3L,
            SSDS: 1L,
            RA: 8L,
            TRC: 5L,
            DTP: 2L,
            DTR: 4L,
            CFTP: 9L,
            CFTR: 6L,
            TIR: 10L,
            TIP: 7L,
        ]
        def histories = [
            CSD: Stub(DocumentHistory),
            SSDS: Stub(DocumentHistory),
            RA: Stub(DocumentHistory),
            TRC: Stub(DocumentHistory),
            DTP: Stub(DocumentHistory),
            'DTR-repo1': Stub(DocumentHistory),
            'DTR-repo2': Stub(DocumentHistory),
            CFTP: Stub(DocumentHistory),
            CFTR: Stub(DocumentHistory),
            'TIR-repo1': Stub(DocumentHistory),
            'TIR-repo2': Stub(DocumentHistory),
            TIP: Stub(DocumentHistory),
        ]
        histories.CSD.getVersion() >> versions.CSD
        histories.SSDS.getVersion() >> versions.SSDS
        histories.RA.getVersion() >> versions.RA
        histories.TRC.getVersion() >> versions.TRC
        histories.DTP.getVersion() >> versions.DTP
        histories.'DTR-repo1'.getVersion() >> versions.DTR
        histories.'DTR-repo2'.getVersion() >> versions.DTR
        histories.CFTP.getVersion() >> versions.CFTP
        histories.CFTR.getVersion() >> versions.CFTR
        histories.'TIR-repo1'.getVersion() >> versions.TIR
        histories.'TIR-repo2'.getVersion() >> versions.TIR
        histories.TIP.getVersion() >> versions.TIP

        when:
        histories.each { docName, history ->
            project.setHistoryForDocument(history, docName)
        }

        then:
        versions.each { docType, version ->
            assert project.getDocumentVersionFromHistories(docType) == version
        }
        assert project.getDocumentVersionFromHistories('other') == null

    }

    def "get automated unit tests"(){
        given:
        def component = "Technology-demo-app-catalogue"
        def expected = project.data.jira.tests.findAll{ key, testIssue -> key == "NET-137"}.values() as List

        when:
        def testIssues = project.getAutomatedTestsTypeUnit(component)

        then:
        testIssues.containsAll(expected)
    }

    def "get automated unit tests throw an error"(){
        given:
        def component = "Technology-demo-app-catalogue"
        def testReferences = project.data.jiraResolved[Project.JiraDataItem.TYPE_TESTS]["NET-137"]
        def componentThatThrowError = [a:1] as Map
        testReferences[Project.JiraDataItem.TYPE_COMPONENTS] = [componentThatThrowError]

        when:
        def testIssues = project.getAutomatedTestsTypeUnit(component)

        then:
        RuntimeException ex = thrown()
        ex.message == 'Error with testIssue key: NET-137, no component assigned or it is wrong.'
    }

    def "load build param - rePromote field"() {
        when:
        steps.env.rePromote = rePromoteInput
        def result = Project.loadBuildParams(steps)

        then:
        result.rePromote == rePromoteOutput

        where:
        rePromoteInput  || rePromoteOutput
        null            || true
        ""              || true
        "true"          || true
        "True"          || true
        "TRUE"          || true
        "false"         || false
        "False"         || false
        "FALSE"         || false
    }

    def "verify isGxpProject default value"() {
        given:
        project.projectProperties.remove(Project.IS_GXP_PROJECT_PROPERTY)

        when:
        def result = project.isGxp()

        then:
        result == Project.IS_GXP_PROJECT_DEFAULT
    }

    def "verify isGxpProject true value"() {
        given:
        project.projectProperties.put(Project.IS_GXP_PROJECT_PROPERTY, 'true')

        when:
        def result = project.isGxp()

        then:
        result == true
    }

    def "verify isGxpProject false value"() {
        given:
        project.projectProperties.put(Project.IS_GXP_PROJECT_PROPERTY, 'false')

        when:
        def result = project.isGxp()

        then:
        result == false
    }

    def "check component mismatch with jira enabled"() {
        given:
        jiraUseCase.getComponents('net', 'UNDEFINED', true) >> { return [deployableState: 'DEPLOYABLE'] }

        when:
        def result = project.getComponentsFromJira()

        then:
        result.deployableState == 'DEPLOYABLE'
    }

    def "check component mismatch fail with jira enabled"() {
        given:
        jiraUseCase.getComponents('net', 'UNDEFINED', true) >> { return [deployableState: 'MISCONFIGURED', message: 'Error'] }

        when:
        def result = project.getComponentsFromJira()

        then:
        result.deployableState != 'DEPLOYABLE'
    }

    def "check component mismatch with jira disabled"() {
        given:
        def projectObj = new Project(steps, logger)

        when:
        def result = projectObj.getComponentsFromJira()

        then:
        !result
    }

    def "check hasGivenTypes"() {
        given:
        def projectObj = new Project(steps, logger)

        when:
        def resulFromExecution = projectObj.hasGivenTypes(testTypes, testIssue)

        then:
        result == resulFromExecution

        where:
        testTypes                                               |   testIssue                   |   result
        ['Unit', 'Integration', 'Installation', 'Acceptance']   |   [testType: 'Unit']          |   true
        ['Integration', 'Installation', 'Acceptance']           |   [testType: 'Unit']          |   false
        ['Unit', 'Integration', 'Installation', 'Acceptance']   |   [testType: null]            |   false
        ['Unit', 'Integration', 'Installation', 'Acceptance']   |   [:]                         |   false
    }

    def "Store aggregated test results with failures and skipped"() {
        given:
        def projectObj = new Project(steps, logger)
        def testData = [ testsuites: [
                [
                    failures: '1',
                    tests: '1',
                    errors: '0',
                    skipped: '0',
                ],
                [
                    failures: '1',
                    tests: '3',
                    errors: '1',
                    skipped: '1',
                ],
                [
                    failures: '0',
                    tests: '5',
                    errors: '0',
                    skipped: '0',
                ],
                [
                    failures: '2',
                    tests: '4',
                    errors: '1',
                    skipped: '0',
                ]
            ]
        ]
        def expected = new TestResults(1, 6, 4, 2, 0)


        when:
        projectObj.storeAggregatedTestResults(testData)

        then:
        projectObj.getAggregatedTestResults() == expected
    }


    def "Store aggregated test results only succeded"() {
        given:
        def projectObj = new Project(steps, logger)
        def testData = [ testsuites: [
                [
                    failures: '0',
                    tests: '1',
                    errors: '0',
                    skipped: '0',
                ]
            ]
        ]
        def expected = new TestResults(0, 4, 0, 0, 0)


        when:
        for (def i : 1..4) {
            projectObj.storeAggregatedTestResults(testData)
        }

        then:
        projectObj.getAggregatedTestResults() == expected
    }

}
