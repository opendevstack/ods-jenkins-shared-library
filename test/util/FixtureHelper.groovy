package util

import org.ods.parser.JUnitParser
import org.ods.service.JiraService

class FixtureHelper {

    static Map createJiraIssue(String id, String summary = null, String description = null, boolean convertToSimpleFormat = true) {
        def result = [
            id: id,
            key: "JIRA-${id}",
            fields: [:],
            self: "http://${id}"
        ]

        result.fields.summary = summary ?: "${id}-summary"
        result.fields.description = description ?: "${id}-description"

        if (convertToSimpleFormat) {
            result = JiraService.Helper.toSimpleIssuesFormat([result])[id]
        }

        return result
    }

    static def createJiraIssues(boolean convertToSimpleFormat = true) {
        def result = []

        // Create some parents
        def issue0815 = createJiraIssue("0815", "0815-summary", "0815-description", false)
        result << issue0815

        def issue4711 = createJiraIssue("4711", "4711-summary", "4711-description", false)
        result << issue4711

        // Create some children
        def issue123 = createJiraIssue("123", "123-summary", "123-description", false)
        issue123.fields.parent = issue0815
        result << issue123

        def issue456 = createJiraIssue("456", "456-summary", "456-description", false)
        issue456.fields.parent = issue0815
        result << issue456

        def issue789 = createJiraIssue("789", "789-summary", "789-description", false)
        issue789.fields.parent = issue4711
        result << issue789

        if (convertToSimpleFormat) {
            result = JiraService.Helper.toSimpleIssuesFormat(result)
        }

        return result
    }

    static def createJiraTestCaseIssues(boolean convertToSimpleFormat = true) {
        def result = []

        // Create some parents
        def mySuite1 = createJiraIssue("1", "my-suite-1", "Test Suite 1", false)
        result << mySuite1

        def mySuite2 = createJiraIssue("2", "my-suite-2", "Test Suite 2", false)
        result << mySuite2

        def mySuite3 = createJiraIssue("3", "my-suite-3", "Test Suite 3", false)
        result << mySuite3

        // Create some children
        def myTestCase1 = createJiraIssue("4", "my-testcase-1", "Test Case 1", false)
        myTestCase1.fields.parent = mySuite1
        result << myTestCase1

        def myTestCase2 = createJiraIssue("5", "my-testcase-2", "Test Case 2", false)
        myTestCase2.fields.parent = mySuite1
        result << myTestCase2

        def myTestCase3 = createJiraIssue("6", "my-testcase-3", "Test Case 3", false)
        myTestCase3.fields.parent = mySuite2
        result << myTestCase3

        def myTestCase4 = createJiraIssue("7", "my-testcase-4", "Test Case 4", false)
        myTestCase4.fields.parent = mySuite2
        result << myTestCase4

        def myTestCase5 = createJiraIssue("8", "my-testcase-5", "Test Case 5", false)
        myTestCase5.fields.parent = mySuite3
        result << myTestCase5

        if (convertToSimpleFormat) {
            result = JiraService.Helper.toSimpleIssuesFormat(result)
        }

        return result
    }

    static String createJUnitXMLTestResults() {
        return """
        <testsuites name="my-suites" tests="4" failures="1" errors="1">
            <testsuite name="my-suite-1" tests="2" failures="0" errors="1" skipped="0">
                <properties>
                    <property name="my-property-a" value="my-property-a-value"/>
                </properties>
                <testcase name="my-testcase-1" classname="app.MyTestCase1" status="Succeeded" time="1"/>
                <testcase name="my-testcase-2" classname="app.MyTestCase2" status="Error" time="2">
                    <error type="my-error-type" message="my-error-message">This is an error.</error>
                </testcase>
            </testsuite>
            <testsuite name="my-suite-2" tests="3" failures="1" errors="0" skipped="1">
                <testcase name="my-testcase-3" classname="app.MyTestCase3" status="Failed" time="3">
                    <failure type="my-failure-type" message="my-failure-message">This is a failure.</failure>
                </testcase>
                <testcase name="my-testcase-4" classname="app.MyTestCase4" status="Missing" time="4">
                    <skipped/>
                </testcase>
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
            name: "A super sophisticated project."
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
                branch: "master"
            ],
            [
                id: "B",
                name: "my-repo-B",
                branch: "master"
            ],
            [
                id: "C"
            ]
        ]

        return result
    }

    static Map createTestResults(boolean convertToSimpleFormat = true) {
        def result = JUnitParser.parseJUnitXML(
            createJUnitXMLTestResults()
        )

        if (convertToSimpleFormat) {
            result = JUnitParser.Helper.toSimpleFormat(result)
        }

        return result
    }

    static Set createTestResultErrors() {
        return JUnitParser.Helper.toSimpleErrorsFormat(createTestResults(true))
    }

    static Set createTestResultFailures() {
        return JUnitParser.Helper.toSimpleFailuresFormat(createTestResults(true))
    }
}
