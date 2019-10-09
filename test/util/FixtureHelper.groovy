package util

import org.ods.parser.JUnitParser
import org.ods.service.JiraService

class FixtureHelper {

    static Map createBuildEnvironment(def env) {
        def params = createBuildParams()
        params.each { key, value ->
            env.set(key, value)
        }

        return params
    }

    static Map createBuildParams() {
        return [
            changeDescription: "The change I've wanted.",
            changeId: "0815",
            configItem: "myItem",
            sourceEnvironmentToClone: "dev",
            targetEnvironment: "dev",
            version: "0.1"
        ]
    }

    static Map createJiraIssue(String id, String summary = null, String description = null) {
        def result = [
            id: id,
            key: "JIRA-${id}",
            fields: [:],
            self: "http://${id}"
        ]

        result.fields.summary = summary ?: "${id}-summary"
        result.fields.description = description ?: "${id}-description"

        return result
    }

    static List createJiraIssues() {
        def result = []

        // Create some parents
        def issue0815 = createJiraIssue("0815", "0815-summary", "0815-description")
        def issue4711 = createJiraIssue("4711", "4711-summary", "4711-description")

        // Create some children
        def issue123 = createJiraIssue("123", "123-summary", "123-description")
        issue123.fields.parent = issue0815
        result << issue123

        def issue456 = createJiraIssue("456", "456-summary", "456-description")
        issue456.fields.parent = issue0815
        result << issue456

        def issue789 = createJiraIssue("789", "789-summary", "789-description")
        issue789.fields.parent = issue4711
        result << issue789

        result << issue0815
        result << issue4711

        return result
    }

    static List createJiraDocumentIssues() {
        def result = []

        result << createJiraIssue("1", "my-doc-A", "Document A")
        result << createJiraIssue("2", "my-doc-B", "Document B")
        result << createJiraIssue("3", "my-doc-C", "Document C")

        return result
    }

    static List createJiraTestIssues() {
        def result = []

        result << createJiraIssue("1", "my-testcase-1", "Test Case 1")
        result << createJiraIssue("2", "my-testcase-2", "Test Case 2")
        result << createJiraIssue("3", "my-testcase-3", "Test Case 3")
        result << createJiraIssue("4", "my-testcase-4", "Test Case 4")
        result << createJiraIssue("5", "my-testcase-5", "Test Case 5")

        return result
    }

    static String createJUnitXMLTestResults() {
        return """
        <testsuites name="my-suites" tests="4" failures="1" errors="1">
            <testsuite name="my-suite-1" tests="2" failures="0" errors="1" skipped="0">
                <properties>
                    <property name="my-property-a" value="my-property-a-value"/>
                </properties>
                <testcase name="JIRA1_my-testcase-1" classname="app.MyTestCase1" status="Succeeded" time="1"/>
                <testcase name="JIRA2_my-testcase-2" classname="app.MyTestCase2" status="Error" time="2">
                    <error type="my-error-type" message="my-error-message">This is an error.</error>
                </testcase>
            </testsuite>
            <testsuite name="my-suite-2" tests="2" failures="1" errors="0" skipped="1">
                <testcase name="JIRA3_my-testcase-3" classname="app.MyTestCase3" status="Failed" time="3">
                    <failure type="my-failure-type" message="my-failure-message">This is a failure.</failure>
                </testcase>
                <testcase name="JIRA4_my-testcase-4" classname="app.MyTestCase4" status="Missing" time="4">
                    <skipped/>
                </testcase>
            </testsuite>
            <testsuite name="my-suite-3" tests="1" failures="0" errors="0" skipped="0">
                <testcase name="my-testcase-5" classname="app.MyTestCase5" status="Succeeded" time="5"/>
            </testsuite>
        </testsuites>
        """
    }

    static Map createProject() {
        def result = [
            id: "PHOENIX",
            key: "PHOENIX-123",
            name: "Project Phoenix",
            description: "A super sophisticated project.",
            data: [:]
        ]

        result.services = [
            bitbucket: [
                credentials: [
                    id: "myBitBucketCredentials"
                ]
            ],
            jira: [
                credentials: [
                    id: "myJiraCredentials"
                ]
            ],
            nexus: [
                repository: [
                    name: "myNexusRepository"
                ]
            ]
        ]

        result.repositories = [
            [
                id: "A",
                url: "https://github.com/my-org/my-repo-A.git",
                branch: "master",
                data: [:]
            ],
            [
                id: "B",
                name: "my-repo-B",
                branch: "master",
                data: [:]
            ],
            [
                id: "C",
                data: [:]
            ]
        ]

        return result
    }

    static Map createOpenShiftPodDataForComponent() {
        return [
            items: [
                [
                    metadata: [
                        name: "myPodName",
                        namespace: "myPodNamespace",
                        creationTimestamp: "myPodCreationTimestamp",
                        labels: [
                            env: "myPodEnvironment"
                        ]
                    ],
                    spec: [
                        nodeName: "myPodNode"
                    ],
                    status: [
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

    static Set createTestResultErrors() {
        return JUnitParser.Helper.getErrors(createTestResults())
    }

    static Set createTestResultFailures() {
        return JUnitParser.Helper.getFailures(createTestResults())
    }
}
