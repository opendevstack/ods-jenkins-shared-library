package util

import groovy.json.JsonSlurperClassic
import groovy.transform.InheritConstructors

import org.ods.services.GitService
import org.apache.http.client.utils.URIBuilder
import org.junit.contrib.java.lang.system.EnvironmentVariables
import org.ods.orchestration.parser.*
import org.ods.orchestration.usecase.*
import org.ods.orchestration.util.*
import org.ods.util.IPipelineSteps
import org.ods.util.Logger
import org.yaml.snakeyaml.Yaml

@InheritConstructors
class FakeGitUtil extends GitService {
    @Override
    String getCommitSha() {
        return "my-commit"
    }

    @Override
    String getOriginUrl() {
        return "https://github.com/my-org/my-repo-A.git"
    }

    @Override
    boolean remoteTagExists(String name) {
        return true
    }
}

@InheritConstructors
class FakeProject extends Project {

    protected static String METADATA_FILE_NAME = new FixtureHelper().getResource("/project-metadata.yml").getAbsolutePath()

    @Override
    Project init() {
        this.data.buildParams = this.loadBuildParams(steps)
        this.data.metadata = this.loadMetadata(METADATA_FILE_NAME)

        this.data.metadata.repositories.each { repo ->
            repo.metadata = [
                name       : "Sock Shop: ${repo.id}",
                description: "Some description for ${repo.id}",
                supplier   : "https://github.com/microservices-demo/",
                version    : "1.0"
            ]
        }

        return this
    }

    @Override
    Project load(GitService git, JiraUseCase jira) {
        this.git = git
        this.jiraUseCase = jiraUseCase

        this.data.git = [ commit: git.getCommitSha(), url: git.getOriginUrl() ]
        this.data.jira = this.loadJiraData(this.jiraProjectKey)
        this.data.jira.project.version = this.loadCurrentVersionDataFromJira()
        this.data.jira.bugs = this.loadJiraDataBugs(this.data.jira.tests)
        this.data.jira = this.convertJiraDataToJiraDataItems(this.data.jira)
        this.data.jiraResolved = this.resolveJiraDataItemReferences(this.data.jira)

        this.data.jira.trackingDocs = this.loadJiraDataTrackingDocs()
        this.data.jira.issueTypes = this.loadJiraDataIssueTypes()

        this.data.jira.undone = this.computeWipJiraIssues(this.data.jira)
        this.data.jira.undone.docChapters = [:]
        this.data.jira[Project.JiraDataItem.TYPE_DOCS] = this.loadDocs()

        this.data.documents = [:]
        this.data.openshift = [:]

        return this
    }

    static List<String> getBuildEnvironment(IPipelineSteps steps, boolean debug) {
        def env = new EnvironmentVariables()
        return FixtureHelper.createProjectBuildEnvironment(env)
    }

    protected URI getGitURLFromPath(String path, String remote) {
        def url = "https://github.com/my-org/my-repo-A.git"
        return new URIBuilder(url).build()
    }

    static Map loadBuildParams(IPipelineSteps steps) {
        return FixtureHelper.createProjectBuildParams()
    }

    protected Map loadJiraData(String projectKey) {
        return FixtureHelper.createProjectJiraData()
    }

    protected Map loadCurrentVersionDataFromJira() {
        return [
            "id"  : "11100",
            "name": "0.3"
        ]
    }

    protected Map loadJiraDataBugs(Map tests) {
        def bugs = FixtureHelper.createProjectJiraDataBugs()

        // Add relations from tests to bug
        bugs.each { key, bug ->
            bug.tests.each { testKey ->
                if (!tests[testKey].bugs) {
                    tests[testKey].bugs = []
                }

                tests[testKey].bugs << bug.key
            }
        }

        return bugs
    }

    protected Map loadJiraDataTrackingDocs() {
        return FixtureHelper.createProjectJiraDataDocs()
    }

    protected Map loadJiraDataIssueTypes() {
        return FixtureHelper.createProjectJiraDataIssueTypes()
    }

    void setRepositories(List repos) {
        this.data.metadata.repositories = repos
    }

    Map loadDocs() {
        return ["doc1": [
                    "key": "DOC-1",
                    "version": "1.0",
                    "name": "name",
                    "number": "1",
                    "documents":["SSDS"],
                    "section": "sec1",
                    "content": "myContent"
                ]]
    }

}

class FixtureHelper {
    static Project createProject(Map buildParams = null) {
        def steps = new PipelineSteps()
        steps.env.WORKSPACE = ""

        Project project = new FakeProject(steps, new Logger(steps, true))
            .init()
        if (buildParams) {
            project.data.buildParams = buildParams
        }
        return project.load(new FakeGitUtil(steps, null), null)
    }

    static Map createProjectBuildEnvironment(def env) {
        def params = createProjectBuildParams()
        params.each { key, value ->
            env.set(key, value)
        }

        return params
    }

    static Map createProjectBuildParams() {
        return [
            changeDescription            : "The change I've wanted.",
            changeId                     : "0815",
            configItem                   : "myItem",
            targetEnvironment            : "dev",
            targetEnvironmentToken       : "D",
            version                      : "0.1"
        ]
    }

    static Map createProjectJiraData() {
        def file = new FixtureHelper().getResource("project-jira-data.json")
        return new JsonSlurperClassic().parse(file)
    }

    static Map createProjectJiraDataBugs() {
        return [:]
        /*
        return [
            "NET-301": [
                "key": "NET-301",
                "name": "org.spockframework.runtime. ConditionFailedWithExceptionError",
                "assignee": "Unassigned",
                "dueDate": "",
                "status": "TO DO",
                "tests": []
            ]
        ]
        */
    }

    static Map createProjectJiraDataDocs() {
        return [
            "NET-1072": [
                "key"        : "NET-1072",
                "name"       : "Test Case Report",
                "description": "TCR",
                "status"     : "DONE",
                "labels"     : [
                    "Doc:TCR"
                ]
            ],
            "NET-1071": [
                "key"        : "NET-1071",
                "name"       : "Test Case Plan",
                "description": "TCP",
                "status"     : "DONE",
                "labels"     : [
                    "Doc:TCP"
                ]
            ],
            "NET-1066": [
                "key"        : "NET-1066",
                "name"       : "Discrepancy Log for P",
                "description": "C-DIL for P",
                "status"     : "DONE",
                "labels"     : [
                    "Doc:DIL_P"
                ]
            ],
            "NET-1064": [
                "key"        : "NET-1064",
                "name"       : "Discrepancy Log for Q",
                "description": "C-DIL for Q",
                "status"     : "DONE",
                "labels"     : [
                    "Doc:DIL_Q"
                ]
            ],
            "NET-1142": [
                "key": "NET-1142",
                "name": "Discrepancy Log for D",
                "description": "C-DIL for D",
                "status": "DONE",
                "labels": [
                    "Doc:DIL"
                ]
            ],
            "NET-1013": [
                "key"        : "NET-1013",
                "name"       : "Combined Specification Document URS FS CS",
                "description": "C-CSD",
                "status"     : "DONE",
                "labels"     : [
                    "Doc:CSD"
                ]
            ],
            "NET-938" : [
                "key"        : "NET-938",
                "name"       : "Traceability Matrix",
                "description": "TC-CTR",
                "status"     : "DONE",
                "labels"     : [
                    "Doc:TRC"
                ]
            ],
            "NET-937" : [
                "key"        : "NET-937",
                "name"       : "System and Software Design Specification including Source Code Review Report",
                "description": "C-SSDS",
                "status"     : "DONE",
                "labels"     : [
                    "Doc:SSDS"
                ]
            ],
            "NET-494" : [
                "key"        : "NET-494",
                "name"       : "Configuration and Installation Testing Report for Dev",
                "description": "C-IVR for DEV",
                "status"     : "DONE",
                "labels"     : [
                    "Doc:IVR"
                ]
            ],
            "NET-433" : [
                "key"        : "NET-433",
                "name"       : "Test Case Report_Manual",
                "description": "TC-CTR_M",
                "status"     : "DONE",
                "labels"     : [
                    "Doc:TC_CTR_M"
                ]
            ],
            "NET-428" : [
                "key"        : "NET-428",
                "name"       : "Functional / Requirements Testing Report_Manual",
                "description": "C-FTR_M",
                "status"     : "DONE",
                "labels"     : [
                    "Doc:FTR_M"
                ]
            ],
            "NET-427" : [
                "key"        : "NET-427",
                "name"       : "Test Case Report",
                "description": "TC-CTR",
                "status"     : "DONE",
                "labels"     : [
                    "Doc:TC_CTR"
                ]
            ],
            "NET-1133": [
                "key": "NET-1133",
                "name": "Test Case Plan for P",
                "description": "TCP_P",
                "status": "DONE",
                "labels": [
                    "Doc:TCP_P"
                ]
            ],
            "NET-1132": [
                "key": "NET-1132",
                "name": "Test Case Plan for Q",
                "description": "TCP_Q",
                "status": "DONE",
                "labels": [
                    "Doc:TCP_Q"
                ]
            ],
            "NET-416" : [
                "key"        : "NET-416",
                "name"       : "Configuration and Installation Testing Plan for DEV",
                "description": "C-IVP for Dev",
                "status"     : "DONE",
                "labels"     : [
                    "Doc:IVP"
                ]
            ],
            "NET-318" : [
                "key"        : "NET-318",
                "name"       : "Technical Installation Plan",
                "description": "C-TIP",
                "status"     : "DONE",
                "labels"     : [
                    "Doc:TIP"
                ]
            ],
            "NET-317" : [
                "key"        : "NET-317",
                "name"       : "Technical Installation Report",
                "description": "C-TIR",
                "status"     : "DONE",
                "labels"     : [
                    "Doc:TIR"
                ]
            ],
            "NET-24"  : [
                "key"        : "NET-24",
                "name"       : "Validation Summary Report",
                "description": "C-VSR",
                "status"     : "DONE",
                "labels"     : [
                    "Doc:VSR"
                ]
            ],
            "NET-23"  : [
                "key"        : "NET-23",
                "name"       : "Configuration and Installation Testing Report for P",
                "description": "C-IVR for P",
                "status"     : "DONE",
                "labels"     : [
                    "Doc:IVR_P"
                ]
            ],
            "NET-22"  : [
                "key"        : "NET-22",
                "name"       : "Technical Installation Report for P",
                "description": "C-TIR for P",
                "status"     : "DONE",
                "labels"     : [
                    "Doc:TIR_P"
                ]
            ],
            "NET-21"  : [
                "key"        : "NET-21",
                "name"       : "Configuration and Installation Testing Plan for P",
                "description": "C-IVP for P",
                "status"     : "DONE",
                "labels"     : [
                    "Doc:IVP_P"
                ]
            ],
            "NET-20"  : [
                "key"        : "NET-20",
                "name"       : "Technical Installation Plan for P",
                "description": "C-TIP for P",
                "status"     : "DONE",
                "labels"     : [
                    "Doc:TIP_P"
                ]
            ],
            "NET-19"  : [
                "key"        : "NET-19",
                "name"       : "Combined Integration / Acceptance Testing Report",
                "description": "C-CFTR",
                "status"     : "DONE",
                "labels"     : [
                    "Doc:CFTR"
                ]
            ],
            "NET-18"  : [
                "key"        : "NET-18",
                "name"       : "Test Cases",
                "description": "C-TC",
                "status"     : "DONE",
                "labels"     : [
                    "Doc:TC"
                ]
            ],
            "NET-17"  : [
                "key"        : "NET-17",
                "name"       : "Combined Integration / Acceptance Testing Plan",
                "description": "C-CFTP",
                "status"     : "DONE",
                "labels"     : [
                    "Doc:CFTP"
                ]
            ],
            "NET-1129": [
                "key": "NET-1129",
                "name": "Combined Integration / Acceptance Testing Plan for P",
                "description": "C-CFTP_P",
                "status": "DONE",
                "labels": [
                    "Doc:CFTP_P"
                ]
            ],
            "NET-1128": [
                "key": "NET-1128",
                "name": "Combined Integration / Acceptance Testing Plan for Q",
                "description": "C-CFTP_Q",
                "status": "DONE",
                "labels": [
                    "Doc:CFTP_Q"
                ]
            ],
            "NET-15"  : [
                "key"        : "NET-15",
                "name"       : "Development Test Report",
                "description": "C-DTR",
                "status"     : "DONE",
                "labels"     : [
                    "Doc:DTR"
                ]
            ],
            "NET-14"  : [
                "key"        : "NET-14",
                "name"       : "Configuration and Installation Testing Report for Q",
                "description": "C-IVR for Q",
                "status"     : "DONE",
                "labels"     : [
                    "Doc:IVR_Q"
                ]
            ],
            "NET-13"  : [
                "key"        : "NET-13",
                "name"       : "Technical Installation Report for Q",
                "description": "C-TIR for Q",
                "status"     : "DONE",
                "labels"     : [
                    "Doc:TIR_Q"
                ]
            ],
            "NET-12"  : [
                "key"        : "NET-12",
                "name"       : "Development Test Plan",
                "description": "C-DTP",
                "status"     : "DONE",
                "labels"     : [
                    "Doc:DTP"
                ]
            ],
            "NET-8"   : [
                "key"        : "NET-8",
                "name"       : "Configuration and Installation Testing Plan for Q",
                "description": "C-IVP for Q",
                "status"     : "DONE",
                "labels"     : [
                    "Doc:IVP_Q"
                ]
            ],
            "NET-7"   : [
                "key"        : "NET-7",
                "name"       : "Technical Installation Plan for Q",
                "description": "C-TIP for Q",
                "status"     : "DONE",
                "labels"     : [
                    "Doc:TIP_Q"
                ]
            ],
            "NET-6"   : [
                "key"        : "NET-6",
                "name"       : "Risk Assessment",
                "description": "C-RA",
                "status"     : "DONE",
                "labels"     : [
                    "Doc:RA"
                ]
            ]
        ]
    }

    static Map createProjectJiraDataIssueTypes() {
        return [
            "Epic": [
                id: "1",
                name: "Epic",
                fields: [
                    "Issue Status": [
                        id: "customfield_1",
                        name: "Issue Status"
                    ],
                    "Linked Issues": [
                       id: "issuelinks",
                       name: "Linked Issues"
                    ]
                ]
            ],
            "Story": [
                id: "2",
                name: "Story",
                fields: [
                    "Issue Status": [
                        id: "customfield_1",
                        name: "Issue Status"
                    ],
                    "Epic Link": [
                        id: "customfield_2",
                        name: "Epic Link"
                    ],
                    "Linked Issues": [
                       id: "issuelinks",
                       name: "Linked Issues"
                    ]
                ]
            ],
            "Test": [
                id: "3",
                name: "Test",
                fields: [
                    "Issue Status": [
                        id: "customfield_1",
                        name: "Issue Status"
                    ],
                    "Epic Link": [
                        id: "customfield_2",
                        name: "Epic Link"
                    ],
                    "Linked Issues": [
                       id: "issuelinks",
                       name: "Linked Issues"
                    ]
                ]
            ],
            "Documentation": [
                id: "4",
                name: "Documentation",
                fields: [
                    "Document Version": [
                        id: "customfield_3",
                        name: "Document Version"
                    ]
                ]
            ],
            "Release Status": [
                id: "5",
                name: "Release Status",
                fields: [
                    "Build Number": [
                        id: "customfield_4",
                        name: "Build Number"
                    ],
                    "Release Manager Status": [
                        id: "customfield_5",
                        name: "Release Manager Status"
                    ]
                ]
            ]
        ]
    }

    static Map createProjectMetadata() {
        def file = new FixtureHelper().getResource("project-metadata.yml")
        return new Yaml().load(file.text)
    }

    static Map createJiraIssue(String id, String issuetype = "Story", String summary = null, String description = null, String status = null) {
        def result = [
            id    : id,
            key   : "JIRA-${id}",
            fields: [:],
            self  : "http://${id}"
        ]

        result.fields.summary = summary ?: "${id}-summary"
        result.fields.description = description ?: "${id}-description"

        result.fields.components = []
        result.fields.issuelinks = []
        result.fields.issuetype = [
            name: issuetype
        ]
        result.fields.status = [
            name: status
        ]

        return result
    }

    static Map createJiraIssueLink(String id, Map inwardIssue = null, Map outwardIssue = null) {
        def result = [
            id  : id,
            type: [
                name   : "Relate",
                inward : "relates to",
                outward: "is related to"
            ],
            self: "http://${id}"
        ]

        if (inwardIssue) {
            result.inwardIssue = inwardIssue
        }

        if (outwardIssue) {
            result.outwardIssue = outwardIssue
        }

        if (!inwardIssue && !outwardIssue) {
            result.outwardIssue = result.inwardIssue = createIssue(id)
        }

        return result
    }

    static List createJiraIssues(def issuetype = "Story") {
        def result = []

        // Create an issue belonging to 3 components and 2 inward links
        def issue1 = createJiraIssue("1", issuetype)
        issue1.fields.components = [
            [name: "myComponentA"],
            [name: "myComponentB"],
            [name: "myComponentC"]
        ]
        issue1.fields.issuelinks = [
            createJiraIssueLink("1", createJiraIssue("100")),
            createJiraIssueLink("2", createJiraIssue("101"))
        ]
        result << issue1

        // Create an issue belonging to 2 components and 1 outward links
        def issue2 = createJiraIssue("2", issuetype)
        issue2.fields.components = [
            [name: "myComponentA"],
            [name: "myComponentB"]
        ]
        issue2.fields.issuelinks = [
            createJiraIssueLink("1", createJiraIssue("200"))
        ]
        result << issue2

        // Create an issue belonging to 1 component and 0 outward links
        def issue3 = createJiraIssue("3", issuetype)
        issue3.fields.components = [
            [name: "myComponentA"]
        ]
        result << issue3

        // Create an issue belonging to 0 components and 0 outward links
        result << createJiraIssue("4")
    }

    static List createJiraDocumentIssues() {
        def result = []

        result << createJiraIssue("1", "my-doc-A", "Document A", null, "DONE")
        result << createJiraIssue("2", "my-doc-B", "Document B", null, "DONE")
        result << createJiraIssue("3", "my-doc-C", "Document C", null, "DONE")

        return result
    }

    static List createJiraTestIssues() {
        def result = createJiraIssues("Test")

        def issue1 = result[0]
        issue1.fields.issuelinks = [
            createJiraIssueLink("1", null, createJiraIssue("100"))
        ]
        issue1.test = [
            description: issue1.description
        ]

        def issue2 = result[1]
        issue2.fields.issuelinks = [
            createJiraIssueLink("1", null, createJiraIssue("200")),
        ]
        issue2.test = [
            description: issue2.description
        ]

        def issue3 = result[2]
        issue3.fields.issuelinks = [
            createJiraIssueLink("1", null, createJiraIssue("300"))
        ]
        issue3.test = [
            description: issue3.description
        ]

        def issue4 = result[3]
        issue4.fields.issuelinks = [
            createJiraIssueLink("1", null, createJiraIssue("400")),
        ]
        issue4.test = [
            description: issue4.description
        ]

        def issue5 = createJiraIssue("5", "Test")
        issue5.fields.issuelinks = [
            createJiraIssueLink("1", null, createJiraIssue("500")),
        ]
        issue5.test = [
            description: issue5.description
        ]
        result << issue5

        return result
    }

    static String createJUnitXMLTestResults() {
        return """
        <testsuites name="my-suites" tests="4" failures="1" errors="1">
            <testsuite name="my-suite-1" tests="2" failures="0" errors="1" skipped="0" timestamp="2020-03-08T20:49:53Z">
                <properties>
                    <property name="my-property-a" value="my-property-a-value"/>
                </properties>
                <testcase name="JIRA1_my-testcase-1" classname="app.MyTestCase1" status="Succeeded" time="1"/>
                <testcase name="JIRA2_my-testcase-2" classname="app.MyTestCase2" status="Error" time="2">
                    <error type="my-error-type" message="my-error-message">This is an error.</error>
                </testcase>
            </testsuite>
            <testsuite name="my-suite-2" tests="2" failures="1" errors="0" skipped="1" timestamp="2020-03-08T20:50:53Z">
                <testcase name="JIRA3_my-testcase-3" classname="app.MyTestCase3" status="Failed" time="3">
                    <failure type="my-failure-type" message="my-failure-message">This is a failure.</failure>
                </testcase>
                <testcase name="JIRA4_my-testcase-4" classname="app.MyTestCase4" status="Missing" time="4">
                    <skipped/>
                </testcase>
            </testsuite>
            <testsuite name="my-suite-3" tests="1" failures="0" errors="0" skipped="0" timestamp="2020-03-08T20:51:53Z">
                <testcase name="my-testcase-5" classname="app.MyTestCase5" status="Succeeded" time="5"/>
            </testsuite>
        </testsuites>
        """
    }

    static String createJUnitXMLTestResultsWithDuplicates() {
        return """
        <testsuites name="my-suites" tests="4" failures="1" errors="1">
            <testsuite name="my-suite-1" tests="2" failures="0" errors="1" skipped="0" timestamp="2020-03-08T20:49:53Z">
                <properties>
                    <property name="my-property-a" value="my-property-a-value"/>
                </properties>
                <testcase name="JIRA1_my-testcase-1" classname="app.MyTestCase1" status="Succeeded" time="1"/>
                <testcase name="JIRA2_my-testcase-2" classname="app.MyTestCase2" status="Error" time="2">
                    <error type="my-error-type" message="my-error-message">This is an error.</error>
                </testcase>
            </testsuite>
            <testsuite name="my-suite-2" tests="2" failures="1" errors="0" skipped="1" timestamp="2020-03-08T20:50:53Z">
                <testcase name="JIRA1_my-testcase-3" classname="app.MyTestCase3" status="Failed" time="3">
                    <failure type="my-failure-type" message="my-failure-message">This is a failure.</failure>
                </testcase>
                <testcase name="JIRA2_my-testcase-4" classname="app.MyTestCase4" status="Missing" time="4">
                    <skipped/>
                </testcase>
            </testsuite>
            <testsuite name="my-suite-3" tests="1" failures="0" errors="0" skipped="0" timestamp="2020-03-08T20:51:53Z">
                <testcase name="my-testcase-5" classname="app.MyTestCase5" status="Succeeded" time="5"/>
            </testsuite>
        </testsuites>
        """
    }

    static String createSockShopJUnitXmlTestResults() {
        """
        <testsuites name="sockshop-suites" tests="4" failures="1" errors="1">
            <testsuite name="sockshop-suite-1" tests="2" failures="0" errors="1" skipped="0" timestamp="2020-03-08T20:49:53Z">
                <properties>
                    <property name="my-property-a" value="my-property-a-value"/>
                </properties>
                <testcase name="NET130_verify-database-setup" classname="org.sockshop.DatabaseSetupTest" status="Succeeded" time="1"/>
                <testcase name="NET139_verify-payment-service-installation" classname="org.sockshop.PaymentServiceInstallationTest" status="Error" time="2">
                    <error type="my-error-type" message="my-error-message">This is an error.</error>
                </testcase>
            </testsuite>
            <testsuite name="sockshop-suite-2" tests="2" failures="1" errors="0" skipped="1" timestamp="2020-03-08T20:46:54Z">
                <testcase name="NET140_verify-order-service-installation" classname="org.sockshop.OrderServiceInstallationTest" status="Failed" time="3">
                    <failure type="my-failure-type" message="my-failure-message">This is a failure.</failure>
                </testcase>
                <testcase name="NET141_verify-databse-authentication" classname="org.sockshop.ShippingServiceInstallationTest" status="Missing" time="4">
                    <skipped/>
                </testcase>
            </testsuite>
            <testsuite name="sockshop-suite-3" tests="1" failures="0" errors="0" skipped="0" timestamp="2020-03-08T20:46:55Z">
                <testcase name="NET138_verify-frontend-is-setup-correctly" classname="org.sockshop.FrontendSetupTest" status="Succeeded" time="5"/>
            </testsuite>
            <testsuite name="sockshop-suite-4" tests="4" failures="0" errors="0" skipped="1" timestamp="2020-03-08T20:46:56Z">
                <testcase name="NET136_user-exists-in-system" classname="org.sockshop.integration.UserTest" status="Succeeded" time="3" />
                <testcase name="NET142_carts-gets-processed-correctly" classname="org.sockshop.integration.CartTest" status="Succeeded" time="3" />
                <testcase name="NET143_frontend-retrieves-cart-correctly" classname="org.sockshop.integration.FrontendTest" status="Succeeded" time="3" />
                <testcase name="NET144_frontend-retrieves-payment-data-correctly" classname="org.sockshop.integration.PaymentTest" status="Succeeded" time="3" />
            </testsuite>
        </testsuites>
        """
    }

    static Map createOpenShiftPodDataForComponent() {
        return [
            items: [
                [
                    metadata: [
                        name             : "myPodName",
                        namespace        : "myPodNamespace",
                        creationTimestamp: "myPodCreationTimestamp",
                        labels           : [
                            env: "myPodEnvironment"
                        ]
                    ],
                    spec    : [
                        nodeName: "myPodNode"
                    ],
                    status  : [
                        podIP: "1.2.3.4",
                        phase: "myPodStatus"
                    ]
                ]
            ]
        ]
    }

    static Map createTestResults() {
        return JUnitParser.parseJUnitXML(
            createJUnitXMLTestResults()
        )
    }

    static Map createTestResultsWithDuplicates() {
        return JUnitParser.parseJUnitXML(
            createJUnitXMLTestResultsWithDuplicates()
        )
    }

    static Map createSockShopTestResults() {
        return JUnitParser.parseJUnitXML(
            createSockShopJUnitXmlTestResults()
        )
    }

    static Set createTestResultErrors() {
        return JUnitParser.Helper.getErrors(createTestResults())
    }

    static Set createTestResultFailures() {
        return JUnitParser.Helper.getFailures(createTestResults())
    }

    static Set createSockShopTestResultErrors() {
        return JUnitParser.Helper.getErrors(createSockShopTestResults())
    }

    static Set createSockShopTestResultFailures() {
        return JUnitParser.Helper.getFailures(createSockShopTestResults())
    }

    static List createSockShopJiraTestIssues() {
        def file = FixtureHelper.class.getResource("/project-jira-data.json")
        return new JsonSlurperClassic().parse(file).tests.collect { mapEntry ->
            mapEntry.value
        }
    }

    File getResource(String path) {
        path = path.startsWith('/') ? path : '/' + path
        new File(this.getClass().getResource(path).toURI())
    }
}
