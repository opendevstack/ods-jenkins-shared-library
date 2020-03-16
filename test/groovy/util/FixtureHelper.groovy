package util

import groovy.json.JsonSlurper
import groovy.json.JsonSlurperClassic
import groovy.transform.InheritConstructors
import org.apache.http.client.utils.URIBuilder
import org.junit.contrib.java.lang.system.EnvironmentVariables
import org.ods.parser.JUnitParser
import org.ods.service.JiraService
import org.ods.util.GitUtil
import org.ods.util.IPipelineSteps
import org.ods.util.Project

@InheritConstructors
class FakeGitUtil extends GitUtil {
    String getCommit() {
        return "my-commit"
    }

    String getURL() {
        return "https://github.com/my-org/my-repo-A.git"
    }
}

@InheritConstructors
class FakeProject extends Project {

    @Override
    Project init() {
        this.data.buildParams = this.loadBuildParams(steps)
        this.data.metadata = this.loadMetadata(METADATA_FILE_NAME)
        return this
    }

    @Override
    Project load(GitUtil git, JiraService jira) {
        this.data.git = [commit: git.getCommit(), url: git.getURL()]
        this.data.jira = this.cleanJiraDataItems(this.convertJiraDataToJiraDataItems(this.loadJiraData(this.data.metadata.id)))
        this.data.jira.project.version = loadJiraDataProjectVersion()
        this.data.jiraResolved = this.resolveJiraDataItemReferences(this.data.jira)
        this.data.jira.docs = this.loadJiraDataDocs()
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

    private File getResource(String path) {
        path = path.startsWith('/') ? path : '/' + path
        new File(getClass().getResource(path).toURI())
    }

    static Map loadBuildParams(IPipelineSteps steps) {
        return FixtureHelper.createProjectBuildParams()
    }

    protected Map loadJiraData(String projectKey) {
        def file = this.getResource("project-jira-data.json")
        return new JsonSlurper().parse(file)
    }

    protected Map loadJiraDataProjectVersion() {
        return [
            "id"  : "11100",
            "name": "0.3"
        ]
    }

    protected Map loadJiraDataDocs() {
        return FixtureHelper.createProjectJiraDataDocs()
    }

    protected Map loadMetadata(String filename) {
        return FixtureHelper.createProjectMetadata()
    }

    void setRepositories(List repos) {
        this.data.metadata.repositories = repos
    }
}

class FixtureHelper {
    static Project createProject() {
        def steps = new PipelineSteps()
        return new FakeProject(steps).init()
            .load(new FakeGitUtil(steps), null)
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
            sourceEnvironmentToClone     : "dev",
            sourceEnvironmentToCloneToken: "D",
            targetEnvironment            : "dev",
            targetEnvironmentToken       : "D",
            version                      : "0.1"
        ]
    }

    static Map createProjectJiraDataDocs() {
        return [
            "PLTFMDEV-1072": [
                "key"        : "PLTFMDEV-1072",
                "name"       : "Test Case Report",
                "description": "TCR",
                "status"     : "DONE",
                "labels"     : [
                    "LeVA_Doc:TCR"
                ]
            ],
            "PLTFMDEV-1071": [
                "key"        : "PLTFMDEV-1071",
                "name"       : "Test Case Plan",
                "description": "TCP",
                "status"     : "DONE",
                "labels"     : [
                    "LeVA_Doc:TCP"
                ]
            ],
            "PLTFMDEV-1066": [
                "key"        : "PLTFMDEV-1066",
                "name"       : "Discrepancy Log for P",
                "description": "C-DIL for P",
                "status"     : "DONE",
                "labels"     : [
                    "LeVA_Doc:DIL_P"
                ]
            ],
            "PLTFMDEV-1064": [
                "key"        : "PLTFMDEV-1064",
                "name"       : "Discrepancy Log for Q",
                "description": "C-DIL for Q",
                "status"     : "DONE",
                "labels"     : [
                    "LeVA_Doc:DIL_Q"
                ]
            ],
            "PLTFMDEV-1013": [
                "key"        : "PLTFMDEV-1013",
                "name"       : "Combined Specification Document URS FS CS",
                "description": "C-CSD",
                "status"     : "DONE",
                "labels"     : [
                    "LeVA_Doc:CSD"
                ]
            ],
            "PLTFMDEV-938" : [
                "key"        : "PLTFMDEV-938",
                "name"       : "Traceability Matrix",
                "description": "TC-CTR",
                "status"     : "DONE",
                "labels"     : [
                    "LeVA_Doc:TRC"
                ]
            ],
            "PLTFMDEV-937" : [
                "key"        : "PLTFMDEV-937",
                "name"       : "System and Software Design Specification including Source Code Review Report",
                "description": "C-SSDS",
                "status"     : "DONE",
                "labels"     : [
                    "LeVA_Doc:SSDS"
                ]
            ],
            "PLTFMDEV-494" : [
                "key"        : "PLTFMDEV-494",
                "name"       : "Configuration and Installation Testing Report for Dev",
                "description": "C-IVR for DEV",
                "status"     : "DONE",
                "labels"     : [
                    "LeVA_Doc:IVR"
                ]
            ],
            "PLTFMDEV-433" : [
                "key"        : "PLTFMDEV-433",
                "name"       : "Test Case Report_Manual",
                "description": "TC-CTR_M",
                "status"     : "DONE",
                "labels"     : [
                    "LeVA_Doc:TC_CTR_M"
                ]
            ],
            "PLTFMDEV-428" : [
                "key"        : "PLTFMDEV-428",
                "name"       : "Functional / Requirements Testing Report_Manual",
                "description": "C-FTR_M",
                "status"     : "DONE",
                "labels"     : [
                    "LeVA_Doc:FTR_M"
                ]
            ],
            "PLTFMDEV-427" : [
                "key"        : "PLTFMDEV-427",
                "name"       : "Test Case Report",
                "description": "TC-CTR",
                "status"     : "DONE",
                "labels"     : [
                    "LeVA_Doc:TC_CTR"
                ]
            ],
            "PLTFMDEV-426" : [
                "key"        : "PLTFMDEV-426",
                "name"       : "Test Case Plan",
                "description": "TC-CTP",
                "status"     : "DONE",
                "labels"     : [
                    "LeVA_Doc:TC_CTP"
                ]
            ],
            "PLTFMDEV-416" : [
                "key"        : "PLTFMDEV-416",
                "name"       : "Configuration and Installation Testing Plan for DEV",
                "description": "C-IVP for Dev",
                "status"     : "DONE",
                "labels"     : [
                    "LeVA_Doc:IVP"
                ]
            ],
            "PLTFMDEV-318" : [
                "key"        : "PLTFMDEV-318",
                "name"       : "Technical Installation Plan",
                "description": "C-TIP",
                "status"     : "DONE",
                "labels"     : [
                    "LeVA_Doc:TIP"
                ]
            ],
            "PLTFMDEV-317" : [
                "key"        : "PLTFMDEV-317",
                "name"       : "Technical Installation Report",
                "description": "C-TIR",
                "status"     : "DONE",
                "labels"     : [
                    "LeVA_Doc:TIR"
                ]
            ],
            "PLTFMDEV-24"  : [
                "key"        : "PLTFMDEV-24",
                "name"       : "Validation Summary Report",
                "description": "C-VSR",
                "status"     : "DONE",
                "labels"     : [
                    "LeVA_Doc:VSR"
                ]
            ],
            "PLTFMDEV-23"  : [
                "key"        : "PLTFMDEV-23",
                "name"       : "Configuration and Installation Testing Report for P",
                "description": "C-IVR for P",
                "status"     : "DONE",
                "labels"     : [
                    "LeVA_Doc:IVR_P"
                ]
            ],
            "PLTFMDEV-22"  : [
                "key"        : "PLTFMDEV-22",
                "name"       : "Technical Installation Report for P",
                "description": "C-TIR for P",
                "status"     : "DONE",
                "labels"     : [
                    "LeVA_Doc:TIR_P"
                ]
            ],
            "PLTFMDEV-21"  : [
                "key"        : "PLTFMDEV-21",
                "name"       : "Configuration and Installation Testing Plan for P",
                "description": "C-IVP for P",
                "status"     : "DONE",
                "labels"     : [
                    "LeVA_Doc:IVP_P"
                ]
            ],
            "PLTFMDEV-20"  : [
                "key"        : "PLTFMDEV-20",
                "name"       : "Technical Installation Plan for P",
                "description": "C-TIP for P",
                "status"     : "DONE",
                "labels"     : [
                    "LeVA_Doc:TIP_P"
                ]
            ],
            "PLTFMDEV-19"  : [
                "key"        : "PLTFMDEV-19",
                "name"       : "Combined Integration / Acceptance Testing Report",
                "description": "C-CFTR",
                "status"     : "DONE",
                "labels"     : [
                    "LeVA_Doc:CFTR",
                    "LeVA_Doc:FTR"
                ]
            ],
            "PLTFMDEV-18"  : [
                "key"        : "PLTFMDEV-18",
                "name"       : "Test Cases",
                "description": "C-TC",
                "status"     : "DONE",
                "labels"     : [
                    "LeVA_Doc:TC"
                ]
            ],
            "PLTFMDEV-17"  : [
                "key"        : "PLTFMDEV-17",
                "name"       : "Combined Integration / Acceptance Testing Plan",
                "description": "C-CFTP",
                "status"     : "DONE",
                "labels"     : [
                    "LeVA_Doc:CFTP",
                    "LeVA_Doc:FTP"
                ]
            ],
            "PLTFMDEV-15"  : [
                "key"        : "PLTFMDEV-15",
                "name"       : "Development Test Report",
                "description": "C-DTR",
                "status"     : "DONE",
                "labels"     : [
                    "LeVA_Doc:DTR"
                ]
            ],
            "PLTFMDEV-14"  : [
                "key"        : "PLTFMDEV-14",
                "name"       : "Configuration and Installation Testing Report for Q",
                "description": "C-IVR for Q",
                "status"     : "DONE",
                "labels"     : [
                    "LeVA_Doc:IVR_Q"
                ]
            ],
            "PLTFMDEV-13"  : [
                "key"        : "PLTFMDEV-13",
                "name"       : "Technical Installation Report for Q",
                "description": "C-TIR for Q",
                "status"     : "DONE",
                "labels"     : [
                    "LeVA_Doc:TIR_Q"
                ]
            ],
            "PLTFMDEV-12"  : [
                "key"        : "PLTFMDEV-12",
                "name"       : "Development Test Plan",
                "description": "C-DTP",
                "status"     : "DONE",
                "labels"     : [
                    "LeVA_Doc:DTP"
                ]
            ],
            "PLTFMDEV-8"   : [
                "key"        : "PLTFMDEV-8",
                "name"       : "Configuration and Installation Testing Plan for Q",
                "description": "C-IVP for Q",
                "status"     : "DONE",
                "labels"     : [
                    "LeVA_Doc:IVP_Q"
                ]
            ],
            "PLTFMDEV-7"   : [
                "key"        : "PLTFMDEV-7",
                "name"       : "Technical Installation Plan for Q",
                "description": "C-TIP for Q",
                "status"     : "DONE",
                "labels"     : [
                    "LeVA_Doc:TIP_Q"
                ]
            ],
            "PLTFMDEV-6"   : [
                "key"        : "PLTFMDEV-6",
                "name"       : "Risk Assessment",
                "description": "C-RA",
                "status"     : "DONE",
                "labels"     : [
                    "LeVA_Doc:RA"
                ]
            ],
            "DEMO-69"      : [
                "key"        : "DEMO-69",
                "name"       : "Document Demo",
                "description": "Demo",
                "status"     : "PENDING",
                "labels"     : [
                    "LeVA_Doc:myTypeNotDone"
                ]
            ],
            "DEMO-70"      : [
                "key"        : "DEMO-70",
                "name"       : "Document Demo",
                "description": "Demo",
                "status"     : "PENDING",
                "labels"     : [
                    "LeVA_Doc:myTypeNotDone"
                ]
            ],
            "DEMO-71"      : [
                "key"        : "DEMO-71",
                "name"       : "Document Demo",
                "description": "Demo",
                "status"     : "DONE",
                "labels"     : [
                    "LeVA_Doc:myType"
                ]
            ]
        ]
    }

    static Map createProjectMetadata() {
        def result = [
            id          : "pltfmdev",
            name        : "Sock Shop",
            description : "A socks-selling e-commerce demo application.",
            services    : [
                bitbucket: [
                    credentials: [
                        id: "pltfmdev-cd-cd-user-with-password"
                    ]
                ],
                jira     : [
                    credentials: [
                        id: "pltfmdev-cd-cd-user-with-password"
                    ]
                ],
                nexus    : [
                    repository: [
                        name: "leva-documentation"
                    ]
                ]
            ],
            repositories: [
                [
                    id  : "demo-app-carts",
                    type: "ods-service",
                    data: [
                        documents: [:]
                    ]
                ],
                [
                    id  : "demo-app-catalogue",
                    type: "ods",
                    data: [
                        documents: [:]
                    ]
                ],
                [
                    id  : "demo-app-front-end",
                    type: "ods",
                    data: [
                        documents: [:]
                    ]
                ],
                [
                    id  : "demo-app-test",
                    type: "ods-test",
                    data: [
                        documents: [:]
                    ]
                ]
            ],
            capabilities: []
        ]

        result.repositories.each { repo ->
            repo.data?.git = [
                branch                 : "origin/master",
                commit                 : "3a2ea1a79a134ebcb871fa8b8d6d91cf",
                previousCommit         : "2ffb2df6aeb349ba81f5597bc0d3a087",
                previousSucessfulCommit: "a7ff3fdbd79f456d985ff0c0fa9f4754",
                url                    : "https://cd_user@somescm.com/scm/someproject/${repo.id}.git".toString()
            ]
            repo.metadata = [
                name       : "Sock Shop: ${repo.id}".toString(),
                description: "Some description for ${repo.id}".toString(),
                supplier   : "https://github.com/microservices-demo/",
                version    : "1.0"
            ]
        }

        return result
    }

    static Map createJiraIssue(String id, String issuetype = "Story", String summary = null, String description = null, String status = null) {
        def result = [
            id    : id,
            key   : "JIRA-${id}",
            fields: [:],
            self  : "http://${id}",
            status: status
        ]

        result.fields.summary = summary ?: "${id}-summary"
        result.fields.description = description ?: "${id}-description"

        result.fields.components = []
        result.fields.issuelinks = []
        result.fields.issuetype = [
            name: issuetype
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

    static String createSockShopJUnitXmlTestResults() {
        """
        <testsuites name="sockshop-suites" tests="4" failures="1" errors="1">
            <testsuite name="sockshop-suite-1" tests="2" failures="0" errors="1" skipped="0" timestamp="2020-03-08T20:49:53Z">
                <properties>
                    <property name="my-property-a" value="my-property-a-value"/>
                </properties>
                <testcase name="PLTFMDEV401_verify-database-setup" classname="org.sockshop.DatabaseSetupTest" status="Succeeded" time="1"/>
                <testcase name="PLTFMDEV1060_verfify-database-installation" classname="org.sockshop.DatabaseInstallationTest" status="Error" time="2">
                    <error type="my-error-type" message="my-error-message">This is an error.</error>
                </testcase>
            </testsuite>
            <testsuite name="sockshop-suite-2" tests="2" failures="1" errors="0" skipped="1" timestamp="2020-03-08T20:46:54Z">
                <testcase name="PLTFMDEV1061_verify-database-is-operational" classname="org.sockshop.DatabaseOperationalTest" status="Failed" time="3">
                    <failure type="my-failure-type" message="my-failure-message">This is a failure.</failure>
                </testcase>
                <testcase name="PLTFMDEV1062_verify-databse-authentication" classname="org.sockshop.DatabaseAuthenticationTest" status="Missing" time="4">
                    <skipped/>
                </testcase>
            </testsuite>
            <testsuite name="sockshop-suite-3" tests="1" failures="0" errors="0" skipped="0" timestamp="2020-03-08T20:46:55Z">
                <testcase name="PLTFMDEV1046_verify-frontend-is-setup-correctly" classname="org.sockshop.FrontendSetupTest" status="Succeeded" time="5"/>
            </testsuite>
            <testsuite name="sockshop-suite-4" tests="4" failures="0" errors="0" skipped="1" timestamp="2020-03-08T20:46:56Z">
                <testcase name="PLTFMDEV554_user-exists-in-system" classname="org.sockshop.integration.UserTest" status="Succeeded" time="3" />
                <testcase name="PLTFMDEV1073_carts-gets-processed-correctly" classname="org.sockshop.integration.CartTest" status="Succeeded" time="3" />
                <testcase name="PLTFMDEV1074_frontend-retrieves-cart-correctly" classname="org.sockshop.integration.FrontendTest" status="Succeeded" time="3" />
                <testcase name="PLTFMDEV1075_frontend-retrieves-payment-data-correctly" classname="org.sockshop.integration.PaymentTest" status="Succeeded" time="3" />
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

    static Map createSockShopTestResults() {
        return JUnitParser.parseJUnitXML(
            createSockShopJUnitXmlTestResults()
        )
    }

    static Set createTestResultErrors() {
        return JUnitParser.Helper.getErrors(createTestResults())
    }

    static Set createSockShopTestResultErrors() {
        return JUnitParser.Helper.getErrors(createSockShopTestResults())
    }

    static Set createTestResultFailures() {
        return JUnitParser.Helper.getFailures(createTestResults())
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
}
