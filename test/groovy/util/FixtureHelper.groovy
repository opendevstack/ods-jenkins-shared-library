package util

import groovy.json.JsonSlurperClassic
import groovy.transform.InheritConstructors
import org.apache.http.client.utils.URIBuilder
import org.junit.contrib.java.lang.system.EnvironmentVariables
import org.ods.orchestration.parser.JUnitParser
import org.ods.orchestration.usecase.JiraUseCase
import org.ods.orchestration.util.Project
import org.ods.services.GitService
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
                version    : "1.0",
            ]

            repo.include = true
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
        this.data.jira.securityVulnerabilities = this.loadJiraDataSecurityVulnerabilities()
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

    protected Map loadJiraDataSecurityVulnerabilities() {
        return FixtureHelper.createProjectJiraSecurityVulnerabilities()
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

    static Map createProjectJiraSecurityVulnerabilities() {
        return [:]
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

    static Map createTIRDataHelm() {
        [
            'git'                    : [
                'previousSucessfulCommit': 'b00012345bcdef',
                'baseTag'                : '',
                'commit'                 : 'a00012345bcdef',
                'previousCommit'         : 'b00012345bcdef',
                'targetTag'              : '',
                'branch'                 : 'master',
                'url'                    : 'https://bitbucket-myodsproject-cd.ocp.mycompany.com/scm/myodsproject/myodsproject-backend-helm-monorepo.git',
            ],
            'previousSucessfulCommit': 'c00012345bcdef',
            'documents'              : [
            ],
            'openshift'              : [
                'testResults'              : 1,
                'deployments'              : [
                    'backend-helm-monorepo-chart-component-deploymentMean': [
                        'chartDir'               : 'chart',
                        'helmAdditionalFlags'    : ['--additional-flag-1', '--additional-flag-2'],
                        'helmEnvBasedValuesFiles': ['values1.env.yaml', 'values2.env.yaml'],
                        'helmValues'             : [
                            'registry'   : 'image-registry.openshift.svc:1000',
                            'componentId': 'backend-helm-monorepo',
                        ],
                        'helmDefaultFlags'       : ['--install', '--atomic'],
                        'namespace'              : 'myodsproject-dev',
                        'helmReleaseName'        : 'backend-helm-monorepo',
                        'selector'               : 'app.kubernetes.io/instance=backend-helm-monorepo',
                        'helmValuesFiles'        : ['values.yaml'],
                        'type'                   : 'helm',
                        'helmStatus'             : [
                            'name'           : 'backend-helm-monorepo',
                            'namespace'      : 'myodsproject-dev',
                            'description'    : 'Upgrade complete',
                            'resourcesByKind': [
                                'Deployment': ['backend-helm-monorepo-chart-component-a', 'backend-helm-monorepo-chart-component-b'],
                                'Service'   : ['backend-helm-monorepo-chart'],
                            ],
                            'version'        : '14',
                            'status'         : 'deployed',
                            'lastDeployed'   : '2024-10-31T11:10:27.478860933Z',
                        ],
                    ],
                    'backend-helm-monorepo-chart-component-b'             : [
                        'podNamespace'                : 'myodsproject-dev',
                        'podStatus'                   : 'Running',
                        'deploymentId'                : 'backend-helm-monorepo-chart-component-b-567ff4f8f6',
                        'podName'                     : 'backend-helm-monorepo-chart-component-b-567ff4f8f6-kvmqm',
                        'podMetaDataCreationTimestamp': '2024-10-31T11:10:28Z',
                        'containers'                  : [
                            'chart-component-b': 'image-registry.openshift.svc:1000/myodsproject-dev/backend-helm-monorepo-component-b@sha256:10002345abcde',
                        ],
                    ],
                    'backend-helm-monorepo-chart-component-a'             : [
                        'podNamespace'                : 'myodsproject-dev',
                        'podStatus'                   : 'Running',
                        'deploymentId'                : 'backend-helm-monorepo-chart-component-a-5ffd9c7cbd',
                        'podName'                     : 'backend-helm-monorepo-chart-component-a-5ffd9c7cbd-h4wsb',
                        'podMetaDataCreationTimestamp': '2024-10-31T11:10:28Z',
                        'containers'                  : [
                            'chart-component-a': 'image-registry.openshift.svc:1000/myodsproject-dev/backend-helm-monorepo-component-a@sha256:10002345abcde',
                        ],
                    ],
                ],
                'testResultsFolder'        : 'build/test-results/test',
                'xunitTestResultsStashPath': 'test-reports-junit-xml-backend-helm-monorepo-19',
                'SCRR'                     : 'SCRR-myodsproject-backend-helm-monorepo.docx',
                'SCRR-MD'                  : 'SCRR-myodsproject-backend-helm-monorepo.md',
                'builds'                   : [
                    'backend-helm-monorepo-component-b': [
                        'image'  : 'image-registry.openshift.svc:1000/myodsproject-cd/backend-helm-monorepo-component-b@sha256:10002345abcde',
                        'buildId': 'backend-helm-monorepo-component-b-26',
                    ],
                    'backend-helm-monorepo-component-a': [
                        'image'  : 'image-registry.openshift.svc:1000/myodsproject-cd/backend-helm-monorepo-component-a@sha256:10002345abcde',
                        'buildId': 'backend-helm-monorepo-component-a-26',
                    ],
                ],
                'CREATED_BY_BUILD'         : 'WIP/19',
                'sonarqubeScanStashPath'   : 'scrr-report-backend-helm-monorepo-19',
            ],
        ]
    }

    static Map createTIRRepoHelm() {
        [
            'include'       : true,
            'metadata'      : [
                'supplier'   : 'IT INF IAS',
                'name'       : 'PostgreSQL',
                'description': 'A fully functional PostgreSQL Cluster with Patroni',
                'type'       : 'ods',
                'version'    : '4.x',
            ],
            'data'          : [
                'git'                    : [
                    'previousSucessfulCommit': 'b00012345bcdef',
                    'baseTag'                : '',
                    'commit'                 : 'a00012345bcdef',
                    'previousCommit'         : 'b00012345bcdef',
                    'targetTag'              : '',
                    'branch'                 : 'master',
                    'url'                    : 'https://bitbucket-myodsproject-cd.ocp.mycompany.com/scm/myodsproject/myodsproject-backend-helm-monorepo.git',
                ],
                'previousSucessfulCommit': 'c00012345bcdef',
                'documents'              : [
                ],
                'openshift'              : [
                    'testResults'              : 1,
                    'deployments'              : [
                        'backend-helm-monorepo-chart-component-b-deploymentMean': [
                            'chartDir'               : 'chart',
                            'helmAdditionalFlags'    : ['--additional-flag-1', '--additional-flag-2'],
                            'helmEnvBasedValuesFiles': ['values1.env.yaml', 'values2.env.yaml'],
                            'helmValues'             : [
                                'registry'   : 'image-registry.openshift.svc:1000',
                                'componentId': 'backend-helm-monorepo',
                            ],
                            'helmDefaultFlags'       : ['--install', '--atomic'],
                            'namespace'              : 'myodsproject-dev',
                            'helmReleaseName'        : 'backend-helm-monorepo',
                            'selector'               : 'app.kubernetes.io/instance=backend-helm-monorepo',
                            'helmValuesFiles'        : ['values.yaml'],
                            'type'                   : 'helm',
                            'helmStatus'             : [
                                'name'           : 'backend-helm-monorepo',
                                'namespace'      : 'myodsproject-dev',
                                'description'    : 'Upgrade complete',
                                'resourcesByKind': [
                                    'Deployment': ['backend-helm-monorepo-chart-component-a', 'backend-helm-monorepo-chart-component-b'],
                                    'Service'   : ['backend-helm-monorepo-chart'],
                                ],
                                'version'        : '14',
                                'status'         : 'deployed',
                                'lastDeployed'   : '2024-10-31T11:10:27.478860933Z',
                            ],
                        ],
                        'backend-helm-monorepo-chart-component-b'               : [
                            'podNamespace'                : 'myodsproject-dev',
                            'podStatus'                   : 'Running',
                            'deploymentId'                : 'backend-helm-monorepo-chart-component-b-567ff4f8f6',
                            'podName'                     : 'backend-helm-monorepo-chart-component-b-567ff4f8f6-kvmqm',
                            'podMetaDataCreationTimestamp': '2024-10-31T11:10:28Z',
                            'containers'                  : [
                                'chart-component-b': 'image-registry.openshift.svc:1000/myodsproject-dev/backend-helm-monorepo-component-b@sha256:10002345abcde',
                            ],
                        ],
                        'backend-helm-monorepo-chart-component-a'               : [
                            'podNamespace'                : 'myodsproject-dev',
                            'podStatus'                   : 'Running',
                            'deploymentId'                : 'backend-helm-monorepo-chart-component-a-5ffd9c7cbd',
                            'podName'                     : 'backend-helm-monorepo-chart-component-a-5ffd9c7cbd-h4wsb',
                            'podMetaDataCreationTimestamp': '2024-10-31T11:10:28Z',
                            'containers'                  : [
                                'chart-component-a': 'image-registry.openshift.svc:1000/myodsproject-dev/backend-helm-monorepo-component-a@sha256:10002345abcde',
                            ],
                        ],
                    ],
                    'testResultsFolder'        : 'build/test-results/test',
                    'xunitTestResultsStashPath': 'test-reports-junit-xml-backend-helm-monorepo-19',
                    'SCRR'                     : 'SCRR-myodsproject-backend-helm-monorepo.docx',
                    'SCRR-MD'                  : 'SCRR-myodsproject-backend-helm-monorepo.md',
                    'builds'                   : [
                        'backend-helm-monorepo-component-b': [
                            'image'  : 'image-registry.openshift.svc:1000/myodsproject-cd/backend-helm-monorepo-component-b@sha256:10002345abcde',
                            'buildId': 'backend-helm-monorepo-component-b-26',
                        ],
                        'backend-helm-monorepo-component-a': [
                            'image'  : 'image-registry.openshift.svc:1000/myodsproject-cd/backend-helm-monorepo-component-a@sha256:10002345abcde',
                            'buildId': 'backend-helm-monorepo-component-a-26',
                        ],
                    ],
                    'CREATED_BY_BUILD'         : 'WIP/19',
                    'sonarqubeScanStashPath'   : 'scrr-report-backend-helm-monorepo-19',
                ],
            ],
            'doInstall'     : true,
            'pipelineConfig': [
                'dependencies': [],
            ],
            'defaultBranch' : 'master',
            'id'            : 'backend-helm-monorepo',
            'type'          : 'ods',
            'url'           : 'https://bitbucket-myodsproject-cd.ocp.mycompany.com/scm/myodsproject/myodsproject-backend-helm-monorepo.git',
        ]
    }

    static Map createTIRDataTailor() {
        [
            'git'                    : [
                'previousSucessfulCommit': null,
                'baseTag'                : '',
                'commit'                 : 'a00012345bcdef',
                'previousCommit'         : null,
                'targetTag'              : '',
                'branch'                 : 'master',
                'url'                    : 'https://bitbucket-myodsproject-cd.ocp.mycompany.com/scm/myodsproject/myodsproject-flask-backend.git',
            ],
            'previousSucessfulCommit': 'c00012345bcdef',
            'documents'              : [
            ],
            'openshift'              : [
                'testResults'              : 1,
                'deployments'              : [
                    'flask-backend'               : [
                        'podNamespace'                : 'myodsproject-dev',
                        'podStatus'                   : 'Running',
                        'deploymentId'                : 'flask-backend-2',
                        'podName'                     : 'flask-backend-2-plcgr',
                        'podMetaDataCreationTimestamp': '2024-10-31T11:09:56Z',
                        'containers'                  : [
                            'flask-backend': 'image-registry.openshift.svc:1000/myodsproject-dev/flask-backend@sha256:10002345abcde',
                        ],
                    ],
                    'flask-backend-deploymentMean': [
                        'tailorParamFile': '',
                        'tailorParams'   : [],
                        'selector'       : 'app=myodsproject-flask-backend',
                        'type'           : 'tailor',
                        'tailorSelectors': [
                            'selector': 'app=myodsproject-flask-backend',
                            'exclude' : 'bc,is',
                        ],
                        'tailorPreserve' : [],
                        'tailorVerify'   : true,
                    ],
                ],
                'testResultsFolder'        : 'build/test-results/test',
                'xunitTestResultsStashPath': 'test-reports-junit-xml-flask-backend-19',
                'SCRR'                     : 'SCRR-myodsproject-flask-backend.docx',
                'SCRR-MD'                  : 'SCRR-myodsproject-flask-backend.md',
                'builds'                   : [
                    'flask-backend': [
                        'image'  : 'image-registry.openshift.svc:1000/myodsproject-cd/flask-backend@sha256:10002345abcde',
                        'buildId': 'flask-backend-2',
                    ],
                ],
                'CREATED_BY_BUILD'         : 'WIP/19',
                'sonarqubeScanStashPath'   : 'scrr-report-flask-backend-19',
            ],
        ]
    }

    static Map createTIRRepoTailor() {
        [
            'include'       : true,
            'metadata'      : [
                'supplier'   : 'https://www.palletsprojects.com/p/flask/',
                'name'       : 'Flask',
                'description': 'Flask is a micro web framework written in Python. Technologies: Flask 3.0.0, Python 3.11',
                'type'       : 'ods',
                'version'    : '4.x',
            ],
            'data'          : [
                'git'                    : [
                    'previousSucessfulCommit': null,
                    'baseTag'                : '',
                    'commit'                 : 'a00012345bcdef',
                    'previousCommit'         : null,
                    'targetTag'              : '',
                    'branch'                 : 'master',
                    'url'                    : 'https://bitbucket-myodsproject-cd.ocp.mycompany.com/scm/myodsproject/myodsproject-flask-backend.git',
                ],
                'previousSucessfulCommit': 'c00012345bcdef',
                'documents'              : [
                ],
                'openshift'              : [
                    'testResults'              : 1,
                    'deployments'              : [
                        'flask-backend'               : [
                            'podNamespace'                : 'myodsproject-dev',
                            'podStatus'                   : 'Running',
                            'deploymentId'                : 'flask-backend-2',
                            'podName'                     : 'flask-backend-2-plcgr',
                            'podMetaDataCreationTimestamp': '2024-10-31T11:09:56Z',
                            'containers'                  : [
                                'flask-backend': 'image-registry.openshift.svc:1000/myodsproject-dev/flask-backend@sha256:10002345abcde',
                            ],
                        ],
                        'flask-backend-deploymentMean': [
                            'tailorParamFile': 'a-param-file.yaml',
                            'tailorParams'   : ['fake-param1', 'fake-param2'],
                            'selector'       : 'app=myodsproject-flask-backend',
                            'type'           : 'tailor',
                            'tailorSelectors': [
                                'selector': 'app=myodsproject-flask-backend',
                                'exclude' : 'bc,is',
                            ],
                            'tailorPreserve' : ['fake-preserve1', 'fake-preserve2'],
                            'tailorVerify'   : true,
                        ],
                    ],
                    'testResultsFolder'        : 'build/test-results/test',
                    'xunitTestResultsStashPath': 'test-reports-junit-xml-flask-backend-19',
                    'SCRR'                     : 'SCRR-myodsproject-flask-backend.docx',
                    'SCRR-MD'                  : 'SCRR-myodsproject-flask-backend.md',
                    'builds'                   : [
                        'flask-backend': [
                            'image'  : 'image-registry.openshift.svc:1000/myodsproject-cd/flask-backend@sha256:10002345abcde',
                            'buildId': 'flask-backend-2',
                        ],
                    ],
                    'CREATED_BY_BUILD'         : 'WIP/19',
                    'sonarqubeScanStashPath'   : 'scrr-report-flask-backend-19',
                ],
            ],
            'doInstall'     : true,
            'pipelineConfig': [
                'dependencies': [],
            ],
            'defaultBranch' : 'master',
            'id'            : 'flask-backend',
            'type'          : 'ods',
            'url'           : 'https://bitbucket-myodsproject-cd.ocp.mycompany.com/scm/myodsproject/myodsproject-flask-backend.git',
        ]
    }

    static Map createHelmCmdStatusMap() {
        [
            'info'     : [
                'deleted'       : '',
                'description'   : 'Upgrade complete',
                'first_deployed': '2022-12-19T09:44:32.164490076Z',
                'last_deployed' : '2024-03-04T15:21:09.34520527Z',
                'resources'     : [
                    'v1/Cluster'     : [[
                                            'apiVersion': 'postgresql.k8s.k8db.io/v1',
                                            'kind'      : 'Cluster',
                                            'metadata'  : [
                                                'annotations'      : [
                                                    'meta.helm.sh/release-name'     : 'standalone-app',
                                                    'meta.helm.sh/release-namespace': 'myproject-test',
                                                ],
                                                'creationTimestamp': '2023-07-04T13:18:28Z',
                                                'generation'       : 3,
                                                'labels'           : [
                                                    'app.kubernetes.io/instance'  : 'standalone-app',
                                                    'app.kubernetes.io/managed-by': 'Helm',
                                                    'app.kubernetes.io/name'      : 'some-cluster',
                                                    'app.kubernetes.io/version'   : 'aaaabbbbcccc',
                                                    'helm.sh/chart'               : 'some-cluster-0.1.0_aaaabbbbcccc',
                                                ],
                                                'name'             : 'some-cluster',
                                                'namespace'        : 'myproject-test',
                                                'resourceVersion'  : '2880969905',
                                                'uid'              : '12345678-1234-1234-1234-200000000abcde',
                                            ],
                                            'spec'      : [
                                                'affinity'             : [
                                                    'podAntiAffinityType': 'preferred',
                                                ],
                                                'bootstrap'            : [
                                                    'initdb': [
                                                        'database'     : 'app',
                                                        'encoding'     : 'UTF8',
                                                        'localeCType'  : 'C',
                                                        'localeCollate': 'C',
                                                        'owner'        : 'app',
                                                    ],
                                                ],
                                                'enableSuperuserAccess': true,
                                                'failoverDelay'        : 0,
                                                'imageName'            : 'quay.io/k8db/postgresql:x.x',
                                                'instances'            : 1,
                                                'logLevel'             : 'info',
                                                'maxSyncReplicas'      : 0,
                                                'minSyncReplicas'      : 0,
                                                'monitoring'           : [
                                                    'customQueriesConfigMap': [[
                                                                                   'key' : 'queries',
                                                                                   'name': 'postgresql-operator-default-monitoring',
                                                                               ]],
                                                    'disableDefaultQueries' : false,
                                                    'enablePodMonitor'      : false,
                                                ],
                                                'postgresGID'          : 26,
                                                'postgresUID'          : 26,
                                                'postgresql'           : [
                                                    'parameters'                   : [
                                                        'archive_mode'              : 'on',
                                                        'archive_timeout'           : '5min',
                                                        'dynamic_shared_memory_type': 'posix',
                                                        'log_destination'           : 'csvlog',
                                                        'log_directory'             : '/controller/log',
                                                        'log_filename'              : 'postgres',
                                                        'log_rotation_age'          : '0',
                                                        'log_rotation_size'         : '0',
                                                        'log_truncate_on_rotation'  : 'false',
                                                        'logging_collector'         : 'on',
                                                        'max_parallel_workers'      : '32',
                                                        'max_replication_slots'     : '32',
                                                        'max_worker_processes'      : '32',
                                                        'shared_memory_type'        : 'mmap',
                                                        'shared_preload_libraries'  : '',
                                                        'ssl_max_protocol_version'  : 'TLSvx.x',
                                                        'ssl_min_protocol_version'  : 'TLSvx.x',
                                                        'wal_keep_size'             : '512MB',
                                                        'wal_receiver_timeout'      : '5s',
                                                        'wal_sender_timeout'        : '5s',
                                                    ],
                                                    'syncReplicaElectionConstraint': [
                                                        'enabled': false,
                                                    ],
                                                ],
                                                'primaryUpdateMethod'  : 'restart',
                                                'primaryUpdateStrategy': 'unsupervised',
                                                'replicationSlots'     : [
                                                    'highAvailability': [
                                                        'enabled'   : true,
                                                        'slotPrefix': '_cnp_',
                                                    ],
                                                    'updateInterval'  : 30,
                                                ],
                                                'resources'            : [],
                                                'smartShutdownTimeout' : 180,
                                                'startDelay'           : 30,
                                                'stopDelay'            : 30,
                                                'storage'              : [
                                                    'resizeInUseVolumes': true,
                                                    'size'              : '20Gi',
                                                ],
                                                'switchoverDelay'      : 40000000,
                                            ],
                                            'status'    : [
                                                'certificates'                     : [
                                                    'clientCASecret'      : 'some-cluster-ca',
                                                    'expirations'         : [
                                                        'some-cluster-ca'         : '2024-08-29 14:02:22 +0000 UTC',
                                                        'some-cluster-replication': '2024-08-29 14:02:22 +0000 UTC',
                                                        'some-cluster-server'     : '2024-08-29 14:02:22 +0000 UTC',
                                                    ],
                                                    'replicationTLSSecret': 'some-cluster-replication',
                                                    'serverAltDNSNames'   : ['some-cluster-rw', 'some-cluster-rw.myproject-test', 'some-cluster-rw.myproject-test.svc', 'some-cluster-r', 'some-cluster-r.myproject-test', 'some-cluster-r.myproject-test.svc', 'some-cluster-ro', 'some-cluster-ro.myproject-test', 'some-cluster-ro.myproject-test.svc'],
                                                    'serverCASecret'      : 'some-cluster-ca',
                                                    'serverTLSSecret'     : 'some-cluster-server',
                                                ],
                                                'cloudNativePostgresqlCommitHash'  : '900010000',
                                                'cloudNativePostgresqlOperatorHash': '12345abcdef',
                                                'conditions'                       : [[
                                                                                          'lastTransitionTime': '2024-05-25T14:42:08Z',
                                                                                          'message'           : 'Cluster is Ready',
                                                                                          'reason'            : 'ClusterIsReady',
                                                                                          'status'            : 'True',
                                                                                          'type'              : 'Ready',
                                                                                      ], [
                                                                                          'lastTransitionTime': '2023-07-04T13:19:38Z',
                                                                                          'message'           : 'vlr addon is disabled',
                                                                                          'reason'            : 'Disabled',
                                                                                          'status'            : 'False',
                                                                                          'type'              : 'k8s.k8db.io/vlr',
                                                                                      ], [
                                                                                          'lastTransitionTime': '2023-07-04T13:19:38Z',
                                                                                          'message'           : 'external-backup-adapter addon is disabled',
                                                                                          'reason'            : 'Disabled',
                                                                                          'status'            : 'False',
                                                                                          'type'              : 'k8s.k8db.io/extBackpAdapt',
                                                                                      ], [
                                                                                          'lastTransitionTime': '2023-07-04T13:19:38Z',
                                                                                          'message'           : 'external-backup-adapter-cluster addon is disabled',
                                                                                          'reason'            : 'Disabled',
                                                                                          'status'            : 'False',
                                                                                          'type'              : 'k8s.k8db.io/extBackpAdaptCluster',
                                                                                      ], [
                                                                                          'lastTransitionTime': '2023-07-04T13:19:40Z',
                                                                                          'message'           : 'kstn addon is disabled',
                                                                                          'reason'            : 'Disabled',
                                                                                          'status'            : 'False',
                                                                                          'type'              : 'k8s.k8db.io/kstn',
                                                                                      ], [
                                                                                          'lastTransitionTime': '2023-11-30T15:26:14Z',
                                                                                          'message'           : 'Continuous archiving is working',
                                                                                          'reason'            : 'ContinuousArchivingSuccess',
                                                                                          'status'            : 'True',
                                                                                          'type'              : 'ContinuousArchiving',
                                                                                      ]],
                                                'configMapResourceVersion'         : [
                                                    'metrics': [
                                                        'postgresql-operator-default-monitoring': '2880955105',
                                                    ],
                                                ],
                                                'currentPrimary'                   : 'some-cluster-1',
                                                'currentPrimaryTimestamp'          : '2023-07-04T13:19:27.039619Z',
                                                'healthyPVC'                       : ['some-cluster-1'],
                                                'instanceNames'                    : ['some-cluster-1'],
                                                'instances'                        : 1,
                                                'instancesReportedState'           : [
                                                    'some-cluster-1': [
                                                        'isPrimary' : true,
                                                        'timeLineID': 1,
                                                    ],
                                                ],
                                                'instancesStatus'                  : [
                                                    'healthy': ['some-cluster-1'],
                                                ],
                                                'latestGeneratedNode'              : 1,
                                                'licenseStatus'                    : [
                                                    'licenseExpiration': '2999-12-31T00:00:00Z',
                                                    'licenseStatus'    : 'Valid license (My Company (my_company))',
                                                    'repositoryAccess' : false,
                                                    'valid'            : true,
                                                ],
                                                'managedRolesStatus'               : [],
                                                'phase'                            : 'Cluster in healthy state',
                                                'poolerIntegrations'               : [
                                                    'pgBouncerIntegration': [],
                                                ],
                                                'pvcCount'                         : 1,
                                                'readService'                      : 'some-cluster-r',
                                                'readyInstances'                   : 1,
                                                'secretsResourceVersion'           : [
                                                    'applicationSecretVersion': '2880969810',
                                                    'clientCaSecretVersion'   : '2880969811',
                                                    'replicationSecretVersion': '2880969813',
                                                    'serverCaSecretVersion'   : '2880969811',
                                                    'serverSecretVersion'     : '2880969815',
                                                    'superuserSecretVersion'  : '2880969816',
                                                ],
                                                'targetPrimary'                    : 'some-cluster-1',
                                                'targetPrimaryTimestamp'           : '2023-07-04T13:18:29.516149Z',
                                                'timelineID'                       : 1,
                                                'topology'                         : [
                                                    'instances'            : [
                                                        'some-cluster-1': [],
                                                    ],
                                                    'nodesUsed'            : 1,
                                                    'successfullyExtracted': true,
                                                ],
                                                'writeService'                     : 'some-cluster-rw',
                                            ],
                                        ]],
                    'v1/ConfigMap'   : [[
                                            'apiVersion': 'v1',
                                            'data'      : [
                                                'application.yaml': 'REDACTED\n',
                                            ],
                                            'kind'      : 'ConfigMap',
                                            'metadata'  : [
                                                'annotations'      : [
                                                    'meta.helm.sh/release-name'     : 'standalone-app',
                                                    'meta.helm.sh/release-namespace': 'myproject-test',
                                                ],
                                                'creationTimestamp': '2023-05-16T15:41:54Z',
                                                'labels'           : [
                                                    'app.kubernetes.io/instance'  : 'standalone-app',
                                                    'app.kubernetes.io/managed-by': 'Helm',
                                                    'app.kubernetes.io/name'      : 'core',
                                                    'app.kubernetes.io/version'   : 'ea01234567',
                                                    'helm.sh/chart'               : 'core-0.1.0_ea01234567',
                                                ],
                                                'name'             : 'core-appconfig-configmap',
                                                'namespace'        : 'myproject-test',
                                                'resourceVersion'  : '2880955101',
                                                'uid'              : '12345678-1234-1234-1234-600000000abcde',
                                            ],
                                        ]],
                    'v1/Deployment'  : [[
                                            'apiVersion': 'apps/v1',
                                            'kind'      : 'Deployment',
                                            'metadata'  : [
                                                'annotations'      : [
                                                    'deployment.kubernetes.io/revision': '36',
                                                    'meta.helm.sh/release-name'        : 'standalone-app',
                                                    'meta.helm.sh/release-namespace'   : 'myproject-test',
                                                ],
                                                'creationTimestamp': '2022-12-19T09:44:33Z',
                                                'generation'       : 42,
                                                'labels'           : [
                                                    'app.kubernetes.io/instance'  : 'standalone-app',
                                                    'app.kubernetes.io/managed-by': 'Helm',
                                                    'app.kubernetes.io/name'      : 'core',
                                                    'app.kubernetes.io/version'   : 'ea01234567',
                                                    'helm.sh/chart'               : 'core-0.1.0_ea01234567',
                                                ],
                                                'name'             : 'core',
                                                'namespace'        : 'myproject-test',
                                                'resourceVersion'  : '2865328801',
                                                'uid'              : '12345678-1234-1234-1234-100000000abcde',
                                            ],
                                            'spec'      : [
                                                'progressDeadlineSeconds': 600,
                                                'replicas'               : 1,
                                                'revisionHistoryLimit'   : 10,
                                                'selector'               : [
                                                    'matchLabels': [
                                                        'app.kubernetes.io/instance': 'standalone-app',
                                                        'app.kubernetes.io/name'    : 'core',
                                                    ],
                                                ],
                                                'strategy'               : [
                                                    'type': 'Recreate',
                                                ],
                                                'template'               : [
                                                    'metadata': [
                                                        'annotations'      : [
                                                            'checksum/appconfig-configmap'       : 'cf012345cf',
                                                            'checksum/rsa-key-secret'            : '57a57a57a57a',
                                                            'checksum/security-exandradev-secret': 'abcdef12345',
                                                            'checksum/security-unify-secret'     : '1a2b3c4d',
                                                        ],
                                                        'creationTimestamp': null,
                                                        'labels'           : [
                                                            'app.kubernetes.io/instance': 'standalone-app',
                                                            'app.kubernetes.io/name'    : 'core',
                                                        ],
                                                    ],
                                                    'spec'    : [
                                                        'containers'                   : [[
                                                                                              'env'                     : [[
                                                                                                                               'name'     : 'EXANDRADEV_CLIENT_ID',
                                                                                                                               'valueFrom': [
                                                                                                                                   'secretKeyRef': [
                                                                                                                                       'key' : 'clientId',
                                                                                                                                       'name': 'core-security-exandradev-secret',
                                                                                                                                   ],
                                                                                                                               ],
                                                                                                                           ], [
                                                                                                                               'name'     : 'EXANDRADEV_CLIENT_SECRET',
                                                                                                                               'valueFrom': [
                                                                                                                                   'secretKeyRef': [
                                                                                                                                       'key' : 'clientSecret',
                                                                                                                                       'name': 'core-security-exandradev-secret',
                                                                                                                                   ],
                                                                                                                               ],
                                                                                                                           ], [
                                                                                                                               'name'     : 'UNIFY_CLIENT_ID',
                                                                                                                               'valueFrom': [
                                                                                                                                   'secretKeyRef': [
                                                                                                                                       'key' : 'clientId',
                                                                                                                                       'name': 'core-security-unify-secret',
                                                                                                                                   ],
                                                                                                                               ],
                                                                                                                           ], [
                                                                                                                               'name'     : 'UNIFY_CLIENT_SECRET',
                                                                                                                               'valueFrom': [
                                                                                                                                   'secretKeyRef': [
                                                                                                                                       'key' : 'clientSecret',
                                                                                                                                       'name': 'core-security-unify-secret',
                                                                                                                                   ],
                                                                                                                               ],
                                                                                                                           ], [
                                                                                                                               'name'     : 'DB_USERNAME',
                                                                                                                               'valueFrom': [
                                                                                                                                   'secretKeyRef': [
                                                                                                                                       'key' : 'username',
                                                                                                                                       'name': 'some-cluster-app',
                                                                                                                                   ],
                                                                                                                               ],
                                                                                                                           ], [
                                                                                                                               'name'     : 'DB_PASSWORD',
                                                                                                                               'valueFrom': [
                                                                                                                                   'secretKeyRef': [
                                                                                                                                       'key' : 'password',
                                                                                                                                       'name': 'some-cluster-app',
                                                                                                                                   ],
                                                                                                                               ],
                                                                                                                           ]],
                                                                                              'image'                   : 'image-registry.openshift.svc:1000/myproject-test/core-standalone:ea01234567',
                                                                                              'imagePullPolicy'         : 'IfNotPresent',
                                                                                              'livenessProbe'           : [
                                                                                                  'failureThreshold': 3,
                                                                                                  'httpGet'         : [
                                                                                                      'path'  : '/q/health/live',
                                                                                                      'port'  : 'http',
                                                                                                      'scheme': 'HTTP',
                                                                                                  ],
                                                                                                  'periodSeconds'   : 5,
                                                                                                  'successThreshold': 1,
                                                                                                  'timeoutSeconds'  : 1,
                                                                                              ],
                                                                                              'name'                    : 'core',
                                                                                              'ports'                   : [[
                                                                                                                               'containerPort': 8081,
                                                                                                                               'name'         : 'http',
                                                                                                                               'protocol'     : 'TCP',
                                                                                                                           ]],
                                                                                              'resources'               : [
                                                                                                  'limits'  : [
                                                                                                      'cpu'   : '1',
                                                                                                      'memory': '512Mi',
                                                                                                  ],
                                                                                                  'requests': [
                                                                                                      'cpu'   : '1',
                                                                                                      'memory': '512Mi',
                                                                                                  ],
                                                                                              ],
                                                                                              'securityContext'         : [],
                                                                                              'startupProbe'            : [
                                                                                                  'failureThreshold': 20,
                                                                                                  'httpGet'         : [
                                                                                                      'path'  : '/q/health/started',
                                                                                                      'port'  : 'http',
                                                                                                      'scheme': 'HTTP',
                                                                                                  ],
                                                                                                  'periodSeconds'   : 3,
                                                                                                  'successThreshold': 1,
                                                                                                  'timeoutSeconds'  : 1,
                                                                                              ],
                                                                                              'terminationMessagePath'  : '/dev/termination-log',
                                                                                              'terminationMessagePolicy': 'File',
                                                                                              'volumeMounts'            : [[
                                                                                                                               'mountPath': '/deployments/core/rsa',
                                                                                                                               'name'     : 'exandra-rsa-key-volume',
                                                                                                                               'readOnly' : true,
                                                                                                                           ], [
                                                                                                                               'mountPath': '/deployments/config',
                                                                                                                               'name'     : 'exandra-config-volume',
                                                                                                                               'readOnly' : true,
                                                                                                                           ]],
                                                                                          ]],
                                                        'dnsPolicy'                    : 'ClusterFirst',
                                                        'restartPolicy'                : 'Always',
                                                        'schedulerName'                : 'default-scheduler',
                                                        'securityContext'              : [],
                                                        'terminationGracePeriodSeconds': 30,
                                                        'volumes'                      : [[
                                                                                              'name'  : 'exandra-rsa-key-volume',
                                                                                              'secret': [
                                                                                                  'defaultMode': 420,
                                                                                                  'items'      : [[
                                                                                                                      'key' : 'rsaKey',
                                                                                                                      'path': 'jwk.json',
                                                                                                                  ]],
                                                                                                  'secretName' : 'core-rsa-key-secret',
                                                                                              ],
                                                                                          ], [
                                                                                              'configMap': [
                                                                                                  'defaultMode': 420,
                                                                                                  'name'       : 'core-appconfig-configmap',
                                                                                              ],
                                                                                              'name'     : 'exandra-config-volume',
                                                                                          ]],
                                                    ],
                                                ],
                                            ],
                                            'status'    : [
                                                'availableReplicas' : 1,
                                                'conditions'        : [[
                                                                           'lastTransitionTime': '2023-05-16T15:53:18Z',
                                                                           'lastUpdateTime'    : '2024-03-04T15:21:26Z',
                                                                           'message'           : 'ReplicaSet \"core-f7f7f7f7\" has successfully progressed.',
                                                                           'reason'            : 'NewReplicaSetAvailable',
                                                                           'status'            : 'True',
                                                                           'type'              : 'Progressing',
                                                                       ], [
                                                                           'lastTransitionTime': '2024-05-25T13:43:04Z',
                                                                           'lastUpdateTime'    : '2024-05-25T13:43:04Z',
                                                                           'message'           : 'Deployment has minimum availability.',
                                                                           'reason'            : 'MinimumReplicasAvailable',
                                                                           'status'            : 'True',
                                                                           'type'              : 'Available',
                                                                       ]],
                                                'observedGeneration': 42,
                                                'readyReplicas'     : 1,
                                                'replicas'          : 1,
                                                'updatedReplicas'   : 1,
                                            ],
                                        ], [
                                            'apiVersion': 'apps/v1',
                                            'kind'      : 'Deployment',
                                            'metadata'  : [
                                                'annotations'      : [
                                                    'deployment.kubernetes.io/revision': '18',
                                                    'meta.helm.sh/release-name'        : 'standalone-app',
                                                    'meta.helm.sh/release-namespace'   : 'myproject-test',
                                                ],
                                                'creationTimestamp': '2023-05-08T09:40:33Z',
                                                'generation'       : 18,
                                                'labels'           : [
                                                    'app.kubernetes.io/instance'  : 'standalone-app',
                                                    'app.kubernetes.io/managed-by': 'Helm',
                                                    'app.kubernetes.io/name'      : 'standalone-gateway',
                                                    'app.kubernetes.io/version'   : '7b5e50e13fd78502967881f4970484ae08b76dc4',
                                                    'helm.sh/chart'               : 'standalone-gateway-0.1.0_7b5e50e13fd78502967881f4970484ae08b76d',
                                                ],
                                                'name'             : 'standalone-gateway',
                                                'namespace'        : 'myproject-test',
                                                'resourceVersion'  : '2865332166',
                                                'uid'              : '12345678-1234-1234-1234-220000000abcde',
                                            ],
                                            'spec'      : [
                                                'progressDeadlineSeconds': 600,
                                                'replicas'               : 1,
                                                'revisionHistoryLimit'   : 10,
                                                'selector'               : [
                                                    'matchLabels': [
                                                        'app.kubernetes.io/instance': 'standalone-app',
                                                        'app.kubernetes.io/name'    : 'standalone-gateway',
                                                    ],
                                                ],
                                                'strategy'               : [
                                                    'type': 'Recreate',
                                                ],
                                                'template'               : [
                                                    'metadata': [
                                                        'creationTimestamp': null,
                                                        'labels'           : [
                                                            'app.kubernetes.io/instance': 'standalone-app',
                                                            'app.kubernetes.io/name'    : 'standalone-gateway',
                                                        ],
                                                    ],
                                                    'spec'    : [
                                                        'containers'                   : [[
                                                                                              'image'                   : 'image-registry.openshift.svc:1000/myproject-test/standalone-gateway:7b5e50e13fd78502967881f4970484ae08b76dc4',
                                                                                              'imagePullPolicy'         : 'IfNotPresent',
                                                                                              'livenessProbe'           : [
                                                                                                  'failureThreshold': 3,
                                                                                                  'httpGet'         : [
                                                                                                      'path'  : '/ready',
                                                                                                      'port'  : 9901,
                                                                                                      'scheme': 'HTTP',
                                                                                                  ],
                                                                                                  'periodSeconds'   : 5,
                                                                                                  'successThreshold': 1,
                                                                                                  'timeoutSeconds'  : 1,
                                                                                              ],
                                                                                              'name'                    : 'standalone-gateway',
                                                                                              'ports'                   : [[
                                                                                                                               'containerPort': 8000,
                                                                                                                               'name'         : 'http',
                                                                                                                               'protocol'     : 'TCP',
                                                                                                                           ]],
                                                                                              'resources'               : [
                                                                                                  'limits'  : [
                                                                                                      'cpu'   : '1',
                                                                                                      'memory': '512Mi',
                                                                                                  ],
                                                                                                  'requests': [
                                                                                                      'cpu'   : '100m',
                                                                                                      'memory': '256Mi',
                                                                                                  ],
                                                                                              ],
                                                                                              'securityContext'         : [],
                                                                                              'startupProbe'            : [
                                                                                                  'failureThreshold'   : 30,
                                                                                                  'httpGet'            : [
                                                                                                      'path'  : '/ready',
                                                                                                      'port'  : 9901,
                                                                                                      'scheme': 'HTTP',
                                                                                                  ],
                                                                                                  'initialDelaySeconds': 1,
                                                                                                  'periodSeconds'      : 1,
                                                                                                  'successThreshold'   : 1,
                                                                                                  'timeoutSeconds'     : 1,
                                                                                              ],
                                                                                              'terminationMessagePath'  : '/dev/termination-log',
                                                                                              'terminationMessagePolicy': 'File',
                                                                                          ]],
                                                        'dnsPolicy'                    : 'ClusterFirst',
                                                        'restartPolicy'                : 'Always',
                                                        'schedulerName'                : 'default-scheduler',
                                                        'securityContext'              : [],
                                                        'terminationGracePeriodSeconds': 30,
                                                    ],
                                                ],
                                            ],
                                            'status'    : [
                                                'availableReplicas' : 1,
                                                'conditions'        : [[
                                                                           'lastTransitionTime': '2023-05-08T09:40:33Z',
                                                                           'lastUpdateTime'    : '2023-12-20T16:48:17Z',
                                                                           'message'           : 'ReplicaSet \"standalone-gateway-500000000c\" has successfully progressed.',
                                                                           'reason'            : 'NewReplicaSetAvailable',
                                                                           'status'            : 'True',
                                                                           'type'              : 'Progressing',
                                                                       ], [
                                                                           'lastTransitionTime': '2024-05-25T13:43:54Z',
                                                                           'lastUpdateTime'    : '2024-05-25T13:43:54Z',
                                                                           'message'           : 'Deployment has minimum availability.',
                                                                           'reason'            : 'MinimumReplicasAvailable',
                                                                           'status'            : 'True',
                                                                           'type'              : 'Available',
                                                                       ]],
                                                'observedGeneration': 18,
                                                'readyReplicas'     : 1,
                                                'replicas'          : 1,
                                                'updatedReplicas'   : 1,
                                            ],
                                        ]],
                    'v1/Pod(related)': [[
                                            'apiVersion': 'v1',
                                            'items'     : [[
                                                               'apiVersion': 'v1',
                                                               'kind'      : 'Pod',
                                                               'metadata'  : [
                                                                   'annotations'      : [
                                                                       'checksum/appconfig-configmap'            : 'cf012345cf',
                                                                       'checksum/rsa-key-secret'                 : '57a57a57a57a',
                                                                       'checksum/security-exandradev-secret'     : 'abcdef12345',
                                                                       'checksum/security-unify-secret'          : '1a2b3c4d',
                                                                       'k8s.ovn.org/pod-networks'                : '{\"default\":{\"ip_addresses\":[\"10.200.10.50/24\"],\"mac_address\":\"0a:00:00:00:00:0a\",\"gateway_ips\":[\"10.200.10.1\"],\"routes\":[{\"dest\":\"10.200.0.0/16\",\"nextHop\":\"10.200.10.1\"},{\"dest\":\"170.30.0.0/16\",\"nextHop\":\"10.200.10.1\"},{\"dest\":\"100.64.0.0/16\",\"nextHop\":\"10.200.10.1\"}],\"ip_address\":\"10.200.10.50/24\",\"gateway_ip\":\"10.200.10.1\"}}',
                                                                       'k8s.v1.cni.cncf.io/network-status'       : '[{\n    \"name\": \"ovn-kubernetes\",\n    \"interface\": \"eth0\",\n    \"ips\": [\n        \"10.200.10.50\"\n    ],\n    \"mac\": \"0a:00:00:00:00:0a\",\n    \"default\": true,\n    \"dns\": {}\n}]',
                                                                       'openshift.io/scc'                        : 'restricted-v2',
                                                                       'seccomp.security.alpha.kubernetes.io/pod': 'runtime/default',
                                                                   ],
                                                                   'creationTimestamp': '2024-05-25T13:41:18Z',
                                                                   'generateName'     : 'core-f7f7f7f7-',
                                                                   'labels'           : [
                                                                       'app.kubernetes.io/instance': 'standalone-app',
                                                                       'app.kubernetes.io/name'    : 'core',
                                                                       'pod-template-hash'         : 'f7f7f7f7',
                                                                   ],
                                                                   'name'             : 'core-f7f7f7f7-8abcx',
                                                                   'namespace'        : 'myproject-test',
                                                                   'ownerReferences'  : [[
                                                                                             'apiVersion'        : 'apps/v1',
                                                                                             'blockOwnerDeletion': true,
                                                                                             'controller'        : true,
                                                                                             'kind'              : 'ReplicaSet',
                                                                                             'name'              : 'core-f7f7f7f7',
                                                                                             'uid'               : '12345678-1234-1234-1234-900000000abcde',
                                                                                         ]],
                                                                   'resourceVersion'  : '2865328796',
                                                                   'uid'              : '12345678-1234-1234-1234-400000000abcde',
                                                               ],
                                                               'spec'      : [
                                                                   'containers'                   : [[
                                                                                                         'env'                     : [[
                                                                                                                                          'name'     : 'EXANDRADEV_CLIENT_ID',
                                                                                                                                          'valueFrom': [
                                                                                                                                              'secretKeyRef': [
                                                                                                                                                  'key' : 'clientId',
                                                                                                                                                  'name': 'core-security-exandradev-secret',
                                                                                                                                              ],
                                                                                                                                          ],
                                                                                                                                      ], [
                                                                                                                                          'name'     : 'EXANDRADEV_CLIENT_SECRET',
                                                                                                                                          'valueFrom': [
                                                                                                                                              'secretKeyRef': [
                                                                                                                                                  'key' : 'clientSecret',
                                                                                                                                                  'name': 'core-security-exandradev-secret',
                                                                                                                                              ],
                                                                                                                                          ],
                                                                                                                                      ], [
                                                                                                                                          'name'     : 'UNIFY_CLIENT_ID',
                                                                                                                                          'valueFrom': [
                                                                                                                                              'secretKeyRef': [
                                                                                                                                                  'key' : 'clientId',
                                                                                                                                                  'name': 'core-security-unify-secret',
                                                                                                                                              ],
                                                                                                                                          ],
                                                                                                                                      ], [
                                                                                                                                          'name'     : 'UNIFY_CLIENT_SECRET',
                                                                                                                                          'valueFrom': [
                                                                                                                                              'secretKeyRef': [
                                                                                                                                                  'key' : 'clientSecret',
                                                                                                                                                  'name': 'core-security-unify-secret',
                                                                                                                                              ],
                                                                                                                                          ],
                                                                                                                                      ], [
                                                                                                                                          'name'     : 'DB_USERNAME',
                                                                                                                                          'valueFrom': [
                                                                                                                                              'secretKeyRef': [
                                                                                                                                                  'key' : 'username',
                                                                                                                                                  'name': 'some-cluster-app',
                                                                                                                                              ],
                                                                                                                                          ],
                                                                                                                                      ], [
                                                                                                                                          'name'     : 'DB_PASSWORD',
                                                                                                                                          'valueFrom': [
                                                                                                                                              'secretKeyRef': [
                                                                                                                                                  'key' : 'password',
                                                                                                                                                  'name': 'some-cluster-app',
                                                                                                                                              ],
                                                                                                                                          ],
                                                                                                                                      ]],
                                                                                                         'image'                   : 'image-registry.openshift.svc:1000/myproject-test/core-standalone:ea01234567',
                                                                                                         'imagePullPolicy'         : 'IfNotPresent',
                                                                                                         'livenessProbe'           : [
                                                                                                             'failureThreshold': 3,
                                                                                                             'httpGet'         : [
                                                                                                                 'path'  : '/q/health/live',
                                                                                                                 'port'  : 'http',
                                                                                                                 'scheme': 'HTTP',
                                                                                                             ],
                                                                                                             'periodSeconds'   : 5,
                                                                                                             'successThreshold': 1,
                                                                                                             'timeoutSeconds'  : 1,
                                                                                                         ],
                                                                                                         'name'                    : 'core',
                                                                                                         'ports'                   : [[
                                                                                                                                          'containerPort': 8081,
                                                                                                                                          'name'         : 'http',
                                                                                                                                          'protocol'     : 'TCP',
                                                                                                                                      ]],
                                                                                                         'resources'               : [
                                                                                                             'limits'  : [
                                                                                                                 'cpu'   : '1',
                                                                                                                 'memory': '512Mi',
                                                                                                             ],
                                                                                                             'requests': [
                                                                                                                 'cpu'   : '1',
                                                                                                                 'memory': '512Mi',
                                                                                                             ],
                                                                                                         ],
                                                                                                         'securityContext'         : [
                                                                                                             'allowPrivilegeEscalation': false,
                                                                                                             'capabilities'            : [
                                                                                                                 'drop': ['ALL'],
                                                                                                             ],
                                                                                                             'runAsNonRoot'            : true,
                                                                                                             'runAsUser'               : 1001270000,
                                                                                                         ],
                                                                                                         'startupProbe'            : [
                                                                                                             'failureThreshold': 20,
                                                                                                             'httpGet'         : [
                                                                                                                 'path'  : '/q/health/started',
                                                                                                                 'port'  : 'http',
                                                                                                                 'scheme': 'HTTP',
                                                                                                             ],
                                                                                                             'periodSeconds'   : 3,
                                                                                                             'successThreshold': 1,
                                                                                                             'timeoutSeconds'  : 1,
                                                                                                         ],
                                                                                                         'terminationMessagePath'  : '/dev/termination-log',
                                                                                                         'terminationMessagePolicy': 'File',
                                                                                                         'volumeMounts'            : [[
                                                                                                                                          'mountPath': '/deployments/core/rsa',
                                                                                                                                          'name'     : 'exandra-rsa-key-volume',
                                                                                                                                          'readOnly' : true,
                                                                                                                                      ], [
                                                                                                                                          'mountPath': '/deployments/config',
                                                                                                                                          'name'     : 'exandra-config-volume',
                                                                                                                                          'readOnly' : true,
                                                                                                                                      ], [
                                                                                                                                          'mountPath': '/var/run/secrets/kubernetes.io/secretaccount',
                                                                                                                                          'name'     : 'kube-api-access-lkjhg',
                                                                                                                                          'readOnly' : true,
                                                                                                                                      ]],
                                                                                                     ]],
                                                                   'dnsPolicy'                    : 'ClusterFirst',
                                                                   'enableServiceLinks'           : true,
                                                                   'imagePullSecrets'             : [[
                                                                                                         'name': 'default-dockercfg-xasdf',
                                                                                                     ]],
                                                                   'nodeName'                     : 'ip-10.8.30.200.ec2.internal',
                                                                   'preemptionPolicy'             : 'PreemptLowerPriority',
                                                                   'priority'                     : 0,
                                                                   'restartPolicy'                : 'Always',
                                                                   'schedulerName'                : 'default-scheduler',
                                                                   'securityContext'              : [
                                                                       'fsGroup'       : 1001270000,
                                                                       'seLinuxOptions': [
                                                                           'level': 's0:c36,c5',
                                                                       ],
                                                                       'seccompProfile': [
                                                                           'type': 'RuntimeDefault',
                                                                       ],
                                                                   ],
                                                                   'serviceAccount'               : 'default',
                                                                   'serviceAccountName'           : 'default',
                                                                   'terminationGracePeriodSeconds': 30,
                                                                   'tolerations'                  : [[
                                                                                                         'effect'           : 'NoExecute',
                                                                                                         'key'              : 'node.kubernetes.io/not-ready',
                                                                                                         'operator'         : 'Exists',
                                                                                                         'tolerationSeconds': 300,
                                                                                                     ], [
                                                                                                         'effect'           : 'NoExecute',
                                                                                                         'key'              : 'node.kubernetes.io/unreachable',
                                                                                                         'operator'         : 'Exists',
                                                                                                         'tolerationSeconds': 300,
                                                                                                     ], [
                                                                                                         'effect'  : 'NoSchedule',
                                                                                                         'key'     : 'node.kubernetes.io/memory-pressure',
                                                                                                         'operator': 'Exists',
                                                                                                     ]],
                                                                   'volumes'                      : [[
                                                                                                         'name'  : 'exandra-rsa-key-volume',
                                                                                                         'secret': [
                                                                                                             'defaultMode': 420,
                                                                                                             'items'      : [[
                                                                                                                                 'key' : 'rsaKey',
                                                                                                                                 'path': 'jwk.json',
                                                                                                                             ]],
                                                                                                             'secretName' : 'core-rsa-key-secret',
                                                                                                         ],
                                                                                                     ], [
                                                                                                         'configMap': [
                                                                                                             'defaultMode': 420,
                                                                                                             'name'       : 'core-appconfig-configmap',
                                                                                                         ],
                                                                                                         'name'     : 'exandra-config-volume',
                                                                                                     ], [
                                                                                                         'name'     : 'kube-api-access-lkjhg',
                                                                                                         'projected': [
                                                                                                             'defaultMode': 420,
                                                                                                             'sources'    : [[
                                                                                                                                 'serviceAccountToken': [
                                                                                                                                     'expirationSeconds': 3607,
                                                                                                                                     'path'             : 'token',
                                                                                                                                 ],
                                                                                                                             ], [
                                                                                                                                 'configMap': [
                                                                                                                                     'items': [[
                                                                                                                                                   'key' : 'ca.crt',
                                                                                                                                                   'path': 'ca.crt',
                                                                                                                                               ]],
                                                                                                                                     'name' : 'kube-some-ca.crt',
                                                                                                                                 ],
                                                                                                                             ], [
                                                                                                                                 'downwardAPI': [
                                                                                                                                     'items': [[
                                                                                                                                                   'fieldRef': [
                                                                                                                                                       'apiVersion': 'v1',
                                                                                                                                                       'fieldPath' : 'metadata.namespace',
                                                                                                                                                   ],
                                                                                                                                                   'path'    : 'namespace',
                                                                                                                                               ]],
                                                                                                                                 ],
                                                                                                                             ], [
                                                                                                                                 'configMap': [
                                                                                                                                     'items': [[
                                                                                                                                                   'key' : 'service-ca.crt',
                                                                                                                                                   'path': 'service-ca.crt',
                                                                                                                                               ]],
                                                                                                                                     'name' : 'openshift-some-ca.crt',
                                                                                                                                 ],
                                                                                                                             ]],
                                                                                                         ],
                                                                                                     ]],
                                                               ],
                                                               'status'    : [
                                                                   'conditions'       : [[
                                                                                             'lastProbeTime'     : null,
                                                                                             'lastTransitionTime': '2024-05-25T13:41:18Z',
                                                                                             'status'            : 'True',
                                                                                             'type'              : 'Initialized',
                                                                                         ], [
                                                                                             'lastProbeTime'     : null,
                                                                                             'lastTransitionTime': '2024-05-25T13:43:03Z',
                                                                                             'status'            : 'True',
                                                                                             'type'              : 'Ready',
                                                                                         ], [
                                                                                             'lastProbeTime'     : null,
                                                                                             'lastTransitionTime': '2024-05-25T13:43:03Z',
                                                                                             'status'            : 'True',
                                                                                             'type'              : 'ContainersReady',
                                                                                         ], [
                                                                                             'lastProbeTime'     : null,
                                                                                             'lastTransitionTime': '2024-05-25T13:41:18Z',
                                                                                             'status'            : 'True',
                                                                                             'type'              : 'PodScheduled',
                                                                                         ]],
                                                                   'containerStatuses': [[
                                                                                             'containerID' : 'cri-o://475000574',
                                                                                             'image'       : 'image-registry.openshift.svc:1000/myproject-test/core-standalone:ea01234567',
                                                                                             'imageID'     : 'image-registry.openshift.svc:1000/myproject-test/core-standalone@sha256:6a000a6',
                                                                                             'lastState'   : [],
                                                                                             'name'        : 'core',
                                                                                             'ready'       : true,
                                                                                             'restartCount': 0,
                                                                                             'started'     : true,
                                                                                             'state'       : [
                                                                                                 'running': [
                                                                                                     'startedAt': '2024-05-25T13:42:52Z',
                                                                                                 ],
                                                                                             ],
                                                                                         ]],
                                                                   'hostIP'           : '10.8.30.200',
                                                                   'phase'            : 'Running',
                                                                   'podIP'            : '10.200.10.50',
                                                                   'podIPs'           : [[
                                                                                             'ip': '10.200.10.50',
                                                                                         ]],
                                                                   'qosClass'         : 'Guaranteed',
                                                                   'startTime'        : '2024-05-25T13:41:18Z',
                                                               ],
                                                           ]],
                                            'kind'      : 'PodList',
                                            'metadata'  : [
                                                'resourceVersion': '2886974735',
                                            ],
                                        ], [
                                            'apiVersion': 'v1',
                                            'items'     : [[
                                                               'apiVersion': 'v1',
                                                               'kind'      : 'Pod',
                                                               'metadata'  : [
                                                                   'annotations'      : [
                                                                       'k8s.ovn.org/pod-networks'                : '{\"default\":{\"ip_addresses\":[\"10.251.18.51/24\"],\"mac_address\":\"0c:00:00:00:00:0c\",\"gateway_ips\":[\"10.200.10.1\"],\"routes\":[{\"dest\":\"10.200.0.0/16\",\"nextHop\":\"10.200.10.1\"},{\"dest\":\"170.30.0.0/16\",\"nextHop\":\"10.200.10.1\"},{\"dest\":\"100.64.0.0/16\",\"nextHop\":\"10.200.10.1\"}],\"ip_address\":\"10.251.18.51/24\",\"gateway_ip\":\"10.200.10.1\"}}',
                                                                       'k8s.v1.cni.cncf.io/network-status'       : '[{\n    \"name\": \"ovn-kubernetes\",\n    \"interface\": \"eth0\",\n    \"ips\": [\n        \"10.251.18.51\"\n    ],\n    \"mac\": \"0c:00:00:00:00:0c\",\n    \"default\": true,\n    \"dns\": {}\n}]',
                                                                       'openshift.io/scc'                        : 'restricted-v2',
                                                                       'seccomp.security.alpha.kubernetes.io/pod': 'runtime/default',
                                                                   ],
                                                                   'creationTimestamp': '2024-05-25T13:41:18Z',
                                                                   'generateName'     : 'standalone-gateway-500000000c-',
                                                                   'labels'           : [
                                                                       'app.kubernetes.io/instance': 'standalone-app',
                                                                       'app.kubernetes.io/name'    : 'standalone-gateway',
                                                                       'pod-template-hash'         : '500000000c',
                                                                   ],
                                                                   'name'             : 'standalone-gateway-500000000c-6h0h6',
                                                                   'namespace'        : 'myproject-test',
                                                                   'ownerReferences'  : [[
                                                                                             'apiVersion'        : 'apps/v1',
                                                                                             'blockOwnerDeletion': true,
                                                                                             'controller'        : true,
                                                                                             'kind'              : 'ReplicaSet',
                                                                                             'name'              : 'standalone-gateway-500000000c',
                                                                                             'uid'               : '12345678-1234-1234-1234-700000000abcde',
                                                                                         ]],
                                                                   'resourceVersion'  : '2865332161',
                                                                   'uid'              : '12345678-1234-1234-1234-110000000abcde',
                                                               ],
                                                               'spec'      : [
                                                                   'containers'                   : [[
                                                                                                         'image'                   : 'image-registry.openshift.svc:1000/myproject-test/standalone-gateway:7b5e50e13fd78502967881f4970484ae08b76dc4',
                                                                                                         'imagePullPolicy'         : 'IfNotPresent',
                                                                                                         'livenessProbe'           : [
                                                                                                             'failureThreshold': 3,
                                                                                                             'httpGet'         : [
                                                                                                                 'path'  : '/ready',
                                                                                                                 'port'  : 9901,
                                                                                                                 'scheme': 'HTTP',
                                                                                                             ],
                                                                                                             'periodSeconds'   : 5,
                                                                                                             'successThreshold': 1,
                                                                                                             'timeoutSeconds'  : 1,
                                                                                                         ],
                                                                                                         'name'                    : 'standalone-gateway',
                                                                                                         'ports'                   : [[
                                                                                                                                          'containerPort': 8000,
                                                                                                                                          'name'         : 'http',
                                                                                                                                          'protocol'     : 'TCP',
                                                                                                                                      ]],
                                                                                                         'resources'               : [
                                                                                                             'limits'  : [
                                                                                                                 'cpu'   : '1',
                                                                                                                 'memory': '512Mi',
                                                                                                             ],
                                                                                                             'requests': [
                                                                                                                 'cpu'   : '100m',
                                                                                                                 'memory': '256Mi',
                                                                                                             ],
                                                                                                         ],
                                                                                                         'securityContext'         : [
                                                                                                             'allowPrivilegeEscalation': false,
                                                                                                             'capabilities'            : [
                                                                                                                 'drop': ['ALL'],
                                                                                                             ],
                                                                                                             'runAsNonRoot'            : true,
                                                                                                             'runAsUser'               : 1001270000,
                                                                                                         ],
                                                                                                         'startupProbe'            : [
                                                                                                             'failureThreshold'   : 30,
                                                                                                             'httpGet'            : [
                                                                                                                 'path'  : '/ready',
                                                                                                                 'port'  : 9901,
                                                                                                                 'scheme': 'HTTP',
                                                                                                             ],
                                                                                                             'initialDelaySeconds': 1,
                                                                                                             'periodSeconds'      : 1,
                                                                                                             'successThreshold'   : 1,
                                                                                                             'timeoutSeconds'     : 1,
                                                                                                         ],
                                                                                                         'terminationMessagePath'  : '/dev/termination-log',
                                                                                                         'terminationMessagePolicy': 'File',
                                                                                                         'volumeMounts'            : [[
                                                                                                                                          'mountPath': '/var/run/secrets/kubernetes.io/secretaccount',
                                                                                                                                          'name'     : 'kube-api-access-zxcvb',
                                                                                                                                          'readOnly' : true,
                                                                                                                                      ]],
                                                                                                     ]],
                                                                   'dnsPolicy'                    : 'ClusterFirst',
                                                                   'enableServiceLinks'           : true,
                                                                   'imagePullSecrets'             : [[
                                                                                                         'name': 'default-dockercfg-xasdf',
                                                                                                     ]],
                                                                   'nodeName'                     : 'ip-10.8.30.200.ec2.internal',
                                                                   'preemptionPolicy'             : 'PreemptLowerPriority',
                                                                   'priority'                     : 0,
                                                                   'restartPolicy'                : 'Always',
                                                                   'schedulerName'                : 'default-scheduler',
                                                                   'securityContext'              : [
                                                                       'fsGroup'       : 1001270000,
                                                                       'seLinuxOptions': [
                                                                           'level': 's0:c36,c5',
                                                                       ],
                                                                       'seccompProfile': [
                                                                           'type': 'RuntimeDefault',
                                                                       ],
                                                                   ],
                                                                   'serviceAccount'               : 'default',
                                                                   'serviceAccountName'           : 'default',
                                                                   'terminationGracePeriodSeconds': 30,
                                                                   'tolerations'                  : [[
                                                                                                         'effect'           : 'NoExecute',
                                                                                                         'key'              : 'node.kubernetes.io/not-ready',
                                                                                                         'operator'         : 'Exists',
                                                                                                         'tolerationSeconds': 300,
                                                                                                     ], [
                                                                                                         'effect'           : 'NoExecute',
                                                                                                         'key'              : 'node.kubernetes.io/unreachable',
                                                                                                         'operator'         : 'Exists',
                                                                                                         'tolerationSeconds': 300,
                                                                                                     ], [
                                                                                                         'effect'  : 'NoSchedule',
                                                                                                         'key'     : 'node.kubernetes.io/memory-pressure',
                                                                                                         'operator': 'Exists',
                                                                                                     ]],
                                                                   'volumes'                      : [[
                                                                                                         'name'     : 'kube-api-access-zxcvb',
                                                                                                         'projected': [
                                                                                                             'defaultMode': 420,
                                                                                                             'sources'    : [[
                                                                                                                                 'serviceAccountToken': [
                                                                                                                                     'expirationSeconds': 3607,
                                                                                                                                     'path'             : 'token',
                                                                                                                                 ],
                                                                                                                             ], [
                                                                                                                                 'configMap': [
                                                                                                                                     'items': [[
                                                                                                                                                   'key' : 'ca.crt',
                                                                                                                                                   'path': 'ca.crt',
                                                                                                                                               ]],
                                                                                                                                     'name' : 'kube-some-ca.crt',
                                                                                                                                 ],
                                                                                                                             ], [
                                                                                                                                 'downwardAPI': [
                                                                                                                                     'items': [[
                                                                                                                                                   'fieldRef': [
                                                                                                                                                       'apiVersion': 'v1',
                                                                                                                                                       'fieldPath' : 'metadata.namespace',
                                                                                                                                                   ],
                                                                                                                                                   'path'    : 'namespace',
                                                                                                                                               ]],
                                                                                                                                 ],
                                                                                                                             ], [
                                                                                                                                 'configMap': [
                                                                                                                                     'items': [[
                                                                                                                                                   'key' : 'service-ca.crt',
                                                                                                                                                   'path': 'service-ca.crt',
                                                                                                                                               ]],
                                                                                                                                     'name' : 'openshift-some-ca.crt',
                                                                                                                                 ],
                                                                                                                             ]],
                                                                                                         ],
                                                                                                     ]],
                                                               ],
                                                               'status'    : [
                                                                   'conditions'       : [[
                                                                                             'lastProbeTime'     : null,
                                                                                             'lastTransitionTime': '2024-05-25T13:41:18Z',
                                                                                             'status'            : 'True',
                                                                                             'type'              : 'Initialized',
                                                                                         ], [
                                                                                             'lastProbeTime'     : null,
                                                                                             'lastTransitionTime': '2024-05-25T13:43:54Z',
                                                                                             'status'            : 'True',
                                                                                             'type'              : 'Ready',
                                                                                         ], [
                                                                                             'lastProbeTime'     : null,
                                                                                             'lastTransitionTime': '2024-05-25T13:43:54Z',
                                                                                             'status'            : 'True',
                                                                                             'type'              : 'ContainersReady',
                                                                                         ], [
                                                                                             'lastProbeTime'     : null,
                                                                                             'lastTransitionTime': '2024-05-25T13:41:18Z',
                                                                                             'status'            : 'True',
                                                                                             'type'              : 'PodScheduled',
                                                                                         ]],
                                                                   'containerStatuses': [[
                                                                                             'containerID' : 'cri-o://14b000b41',
                                                                                             'image'       : 'image-registry.openshift.svc:1000/myproject-test/standalone-gateway:7b5e50e13fd78502967881f4970484ae08b76dc4',
                                                                                             'imageID'     : 'image-registry.openshift.svc:1000/myproject-test/standalone-gateway@sha256:c30003c',
                                                                                             'lastState'   : [],
                                                                                             'name'        : 'standalone-gateway',
                                                                                             'ready'       : true,
                                                                                             'restartCount': 0,
                                                                                             'started'     : true,
                                                                                             'state'       : [
                                                                                                 'running': [
                                                                                                     'startedAt': '2024-05-25T13:43:50Z',
                                                                                                 ],
                                                                                             ],
                                                                                         ]],
                                                                   'hostIP'           : '10.8.30.200',
                                                                   'phase'            : 'Running',
                                                                   'podIP'            : '10.251.18.51',
                                                                   'podIPs'           : [[
                                                                                             'ip': '10.251.18.51',
                                                                                         ]],
                                                                   'qosClass'         : 'Burstable',
                                                                   'startTime'        : '2024-05-25T13:41:18Z',
                                                               ],
                                                           ]],
                                            'kind'      : 'PodList',
                                            'metadata'  : [
                                                'resourceVersion': '2886974735',
                                            ],
                                        ]],
                    'v1/Secret'      : [[
                                            'apiVersion': 'v1',
                                            'data'      : [
                                                'rsaKey': 'REDACTED',
                                            ],
                                            'kind'      : 'Secret',
                                            'metadata'  : [
                                                'annotations'      : [
                                                    'meta.helm.sh/release-name'     : 'standalone-app',
                                                    'meta.helm.sh/release-namespace': 'myproject-test',
                                                ],
                                                'creationTimestamp': '2023-08-25T08:54:46Z',
                                                'labels'           : [
                                                    'app.kubernetes.io/instance'  : 'standalone-app',
                                                    'app.kubernetes.io/managed-by': 'Helm',
                                                    'app.kubernetes.io/name'      : 'core',
                                                    'app.kubernetes.io/version'   : 'ea01234567',
                                                    'helm.sh/chart'               : 'core-0.1.0_ea01234567',
                                                ],
                                                'name'             : 'core-rsa-key-secret',
                                                'namespace'        : 'myproject-test',
                                                'resourceVersion'  : '2880969794',
                                                'uid'              : '12345678-1234-1234-1234-300000000abcde',
                                            ],
                                            'type'      : 'Opaque',
                                        ], [
                                            'apiVersion': 'v1',
                                            'data'      : [
                                                'clientId'    : 'REDACTED',
                                                'clientSecret': 'REDACTED',
                                            ],
                                            'kind'      : 'Secret',
                                            'metadata'  : [
                                                'annotations'      : [
                                                    'meta.helm.sh/release-name'     : 'standalone-app',
                                                    'meta.helm.sh/release-namespace': 'myproject-test',
                                                ],
                                                'creationTimestamp': '2023-08-25T08:54:46Z',
                                                'labels'           : [
                                                    'app.kubernetes.io/instance'  : 'standalone-app',
                                                    'app.kubernetes.io/managed-by': 'Helm',
                                                    'app.kubernetes.io/name'      : 'core',
                                                    'app.kubernetes.io/version'   : 'ea01234567',
                                                    'helm.sh/chart'               : 'core-0.1.0_ea01234567',
                                                ],
                                                'name'             : 'core-security-exandradev-secret',
                                                'namespace'        : 'myproject-test',
                                                'resourceVersion'  : '2880969795',
                                                'uid'              : '12345678-1234-1234-1234-500000000abcde',
                                            ],
                                            'type'      : 'Opaque',
                                        ], [
                                            'apiVersion': 'v1',
                                            'data'      : [
                                                'clientId'    : 'REDACTED',
                                                'clientSecret': 'REDACTED',
                                            ],
                                            'kind'      : 'Secret',
                                            'metadata'  : [
                                                'annotations'      : [
                                                    'meta.helm.sh/release-name'     : 'standalone-app',
                                                    'meta.helm.sh/release-namespace': 'myproject-test',
                                                ],
                                                'creationTimestamp': '2023-05-16T15:41:54Z',
                                                'labels'           : [
                                                    'app.kubernetes.io/instance'  : 'standalone-app',
                                                    'app.kubernetes.io/managed-by': 'Helm',
                                                    'app.kubernetes.io/name'      : 'core',
                                                    'app.kubernetes.io/version'   : 'ea01234567',
                                                    'helm.sh/chart'               : 'core-0.1.0_ea01234567',
                                                ],
                                                'name'             : 'core-security-unify-secret',
                                                'namespace'        : 'myproject-test',
                                                'resourceVersion'  : '2880969797',
                                                'uid'              : '536ceb38-0457-4186-bd09-efe234b5fca1',
                                            ],
                                            'type'      : 'Opaque',
                                        ]],
                    'v1/Service'     : [[
                                            'apiVersion': 'v1',
                                            'kind'      : 'Service',
                                            'metadata'  : [
                                                'annotations'      : [
                                                    'meta.helm.sh/release-name'     : 'standalone-app',
                                                    'meta.helm.sh/release-namespace': 'myproject-test',
                                                ],
                                                'creationTimestamp': '2022-12-19T09:44:33Z',
                                                'labels'           : [
                                                    'app.kubernetes.io/instance'  : 'standalone-app',
                                                    'app.kubernetes.io/managed-by': 'Helm',
                                                    'app.kubernetes.io/name'      : 'core',
                                                    'app.kubernetes.io/version'   : 'ea01234567',
                                                    'helm.sh/chart'               : 'core-0.1.0_ea01234567',
                                                ],
                                                'name'             : 'core',
                                                'namespace'        : 'myproject-test',
                                                'resourceVersion'  : '2687980260',
                                                'uid'              : '12345678-1234-1234-1234-123456789abcde',
                                            ],
                                            'spec'      : [
                                                'clusterIP'            : '100.30.20.100',
                                                'clusterIPs'           : ['100.30.20.100'],
                                                'internalTrafficPolicy': 'Cluster',
                                                'ipFamilies'           : ['IPv4'],
                                                'ipFamilyPolicy'       : 'SingleStack',
                                                'ports'                : [[
                                                                              'name'      : 'http',
                                                                              'port'      : 8081,
                                                                              'protocol'  : 'TCP',
                                                                              'targetPort': 8081,
                                                                          ]],
                                                'selector'             : [
                                                    'app.kubernetes.io/instance': 'standalone-app',
                                                    'app.kubernetes.io/name'    : 'core',
                                                ],
                                                'sessionAffinity'      : 'None',
                                                'type'                 : 'ClusterIP',
                                            ],
                                            'status'    : [
                                                'loadBalancer': [],
                                            ],
                                        ], [
                                            'apiVersion': 'v1',
                                            'kind'      : 'Service',
                                            'metadata'  : [
                                                'annotations'      : [
                                                    'meta.helm.sh/release-name'     : 'standalone-app',
                                                    'meta.helm.sh/release-namespace': 'myproject-test',
                                                ],
                                                'creationTimestamp': '2023-05-08T09:40:33Z',
                                                'labels'           : [
                                                    'app.kubernetes.io/instance'  : 'standalone-app',
                                                    'app.kubernetes.io/managed-by': 'Helm',
                                                    'app.kubernetes.io/name'      : 'standalone-gateway',
                                                    'app.kubernetes.io/version'   : '7b5e50e13fd78502967881f4970484ae08b76dc4',
                                                    'helm.sh/chart'               : 'standalone-gateway-0.1.0_7b5e50e13fd78502967881f4970484ae08b76d',
                                                ],
                                                'name'             : 'standalone-gateway',
                                                'namespace'        : 'myproject-test',
                                                'resourceVersion'  : '2497441712',
                                                'uid'              : '12345678-1234-1234-1234-800000000abcde',
                                            ],
                                            'spec'      : [
                                                'clusterIP'            : '100.30.100.70',
                                                'clusterIPs'           : ['100.30.100.70'],
                                                'internalTrafficPolicy': 'Cluster',
                                                'ipFamilies'           : ['IPv4'],
                                                'ipFamilyPolicy'       : 'SingleStack',
                                                'ports'                : [[
                                                                              'name'      : 'http',
                                                                              'port'      : 80,
                                                                              'protocol'  : 'TCP',
                                                                              'targetPort': 8000,
                                                                          ]],
                                                'selector'             : [
                                                    'app.kubernetes.io/instance': 'standalone-app',
                                                    'app.kubernetes.io/name'    : 'standalone-gateway',
                                                ],
                                                'sessionAffinity'      : 'None',
                                                'type'                 : 'ClusterIP',
                                            ],
                                            'status'    : [
                                                'loadBalancer': [],
                                            ],
                                        ]],
                ],
                'status'        : 'deployed',
            ],
            'manifest' : 'REDACTED\n',
            'name'     : 'standalone-app',
            'namespace': 'myproject-test',
            'version'  : 43,
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
