package org.ods.orchestration.parser

import com.cloudbees.groovy.cps.NonCPS
import groovy.json.JsonBuilder

// JUnit XML 5 Schema:
// https://github.com/junit-team/junit5/blob/master/platform-tests/src/test/resources/jenkins-junit.xsd
// Collection.find() failing in Jenkins:
// https://stackoverflow.com/questions/54364380/
// same-script-work-well-at-the-jenkins-script-console-but-not-at-the-pipline
class JUnitParser {

    @NonCPS
    // Parse a JUnit 4/5 XML document
    static Map parseJUnitXML(String xml) {
        if (!xml || xml.isEmpty()) {
            throw new IllegalArgumentException(
                "Error: unable to transform JUnit XML document to JSON. 'xml' is not in a valid JUnit XML format:" +
                    " No XML or empty XML provided"
            )
        }

        def root = new XmlSlurper().parseText(xml)
        if (!["testsuites", "testsuite"].contains(root.name())) {
            throw new IllegalArgumentException(
                "Error: unable to transform JUnit XML document to JSON. 'xml' is not in a valid JUnit XML format:" +
                    " Required attribute 'testsuite(s)' is missing."
            )
        }

        def result = [:]
        if (root.name() == "testsuite") {
            result = [
                "testsuites": [parseJUnitXMLTestSuiteElement(root)]
            ]
        } else if (root.name() == "testsuites") {
            result = parseJUnitXMLTestSuitesElement(root)
        }

        return result
    }

    @NonCPS
    // Parse a JUnit XML <property> element
    private static def parseJUnitXMLPropertyElement(def property) {
        if (!property.@name) {
            throw new IllegalArgumentException(
                "Error: unable to parse JUnit XML <property> element. Required attribute 'name' is missing."
            )
        }

        if (!property.@value) {
            throw new IllegalArgumentException(
                "Error: unable to parse JUnit XML <property> element. Required attribute 'value' is missing."
            )
        }

        return property.attributes()
    }

    @NonCPS
    // Parse a JUnit XML <testcase> element
    private static def parseJUnitXMLTestCaseElement(def testcase) {
        if (testcase.@name.isEmpty()) {
            throw new IllegalArgumentException(
                "Error: unable to parse JUnit XML <testcase> element. Required attribute 'name' is missing."
            )
        }

        def result = [:]

        // Parse <testcase> attributes
        result << testcase.attributes()

        // Parse <testcase>/(<error>|<failure>) elements
        result << testcase."*"
            .findAll { ["error", "failure"].contains(it.name()) }
            .collectEntries {
                [
                    it.name(),
                    [ :] << it.attributes() << ["text": it.text()],
                ]
            }

        // Parse <testcase>/<skipped> elements
        result << [
            "skipped": testcase."*".find { it.name() == "skipped" } ? true : false
        ]

        // Parse <testcase>/<system-out> elements
        result << [
            "systemOut": testcase."*".find { it.name() == "system-out" }.text()
        ]

        // Parse <testcase>/<system-err> elements
        result << [
            "systemErr": testcase."*".find { it.name() == "system-err" }.text()
        ]

        return result
    }

    @NonCPS
    // Parse a JUnit XML <testsuite> element
    private static def parseJUnitXMLTestSuiteElement(def testsuite) {
        if (testsuite.@name.isEmpty()) {
            throw new IllegalArgumentException(
                "Error: unable to parse JUnit XML <testsuite> element. Required attribute 'name' is missing."
            )
        }

        if (testsuite.@tests.isEmpty()) {
            throw new IllegalArgumentException(
                "Error: unable to parse JUnit XML <testsuite> element. Required attribute 'tests' is missing."
            )
        }

        def result = [:]

        // Parse <testsuite> attributes
        result << testsuite.attributes()

        // Parse <testsuite>/**/<property> elements
        result << ["properties": testsuite."**"
            .findAll { it.name() == "property" }
            .collect { parseJUnitXMLPropertyElement(it) }
        ]

        // Parse <testsuite>/<testcase> elements
        result << ["testcases": testsuite."*"
            .findAll { it.name() == "testcase" }
            .collect {
                def testcase = parseJUnitXMLTestCaseElement(it)

                if (result.timestamp) {
                    testcase.timestamp = result.timestamp
                }

                return testcase
            }
        ]

        // Parse <testsuite>/<system-out> elements
        result << [
            "systemOut": testsuite."*".find { it.name() == "system-out" }.text()
        ]

        // Parse <testsuite>/<system-err> elements
        result << [
            "systemErr": testsuite."*".find { it.name() == "system-err" }.text()
        ]

        return result
    }

    @NonCPS
    // Parse a JUnit XML <testsuites> element
    private static def parseJUnitXMLTestSuitesElement(def testsuites) {
        def result = [:]

        // Parse <testsuites> attributes
        result << testsuites.attributes()

        // Parse <testsuites>/<testsuite> elements
        result << [(testsuites.name()): testsuites."*"
            .findAll { it.name() == "testsuite" }
            .collect { parseJUnitXMLTestSuiteElement(it) }
        ]

        return result
    }

    class Helper {

        static Set getErrors(Map testResults) {
            return getIssues(testResults, "error")
        }

        static Set getFailures(Map testResults) {
            return getIssues(testResults, "failure")
        }

        @NonCPS
        // Transform the parser's result into a JSON string.
        static String toJSONString(Map xml) {
            new JsonBuilder(xml).toPrettyString()
        }

        @NonCPS
        private static Set getIssues(Map testResults, String type) {
            // Compute a unique set of issues
            def issues = [] as Set
            testResults.testsuites.each { testsuite ->
                testsuite.testcases.each { testcase ->
                    if (testcase[type]) {
                        issues << testcase[type]
                    }
                }
            }

            def result = issues
            result.each { issue ->
                // Find all testcases that exhibit the current issue
                testResults.testsuites.each { testsuite ->
                    testsuite.testcases.each { testcase ->
                        def i = issue.findAll { it.key != "testsuites" }
                        if (testcase[type] && testcase[type] == i) {
                            if (!issue.containsKey("testsuites")) {
                                issue.testsuites = []
                            }

                            // Check if the current testsuite already exists in the issue
                            def issueTestsuite = issue.testsuites.find { it.name == testsuite.name }
                            if (!issueTestsuite) {
                                issueTestsuite = [
                                    name: testsuite.name,
                                    testcases: [],
                                ]

                                issue.testsuites << issueTestsuite
                            }

                            // Add the current testcase to the issue
                            issueTestsuite.testcases << [
                                name: testcase.name
                            ]
                        }
                    }
                }
            }

            return result
        }

    }

}
