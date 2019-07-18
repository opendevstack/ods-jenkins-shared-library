package org.ods.parser

import groovy.json.JsonBuilder

// JUnit XML 5 Schema: https://github.com/junit-team/junit5/blob/master/platform-tests/src/test/resources/jenkins-junit.xsd
// Collection.find() failing in Jenkins: https://stackoverflow.com/questions/54364380/same-script-work-well-at-the-jenkins-script-console-but-not-at-the-pipline
class JUnitParser {
    @NonCPS
    // Parse a JUnit XML <property> element
    private static def parseJUnitXMLPropertyElement(def property) {
        if (!property.@name) {
            throw new IllegalArgumentException("Error: unable to parse JUnit XML <property> element. Required attribute 'name' is missing.")
        }

        if (!property.@value) {
            throw new IllegalArgumentException("Error: unable to parse JUnit XML <property> element. Required attribute 'value' is missing.")
        }

        return property.attributes()
    }

    @NonCPS
    // Parse a JUnit XML <testcase> element
    private static def parseJUnitXMLTestCaseElement(def testcase) {
        if (!testcase.@name) {
            throw new IllegalArgumentException("Error: unable to parse JUnit XML <testcase> element. Required attribute 'name' is missing.")
        }

        def result = [:]

        // Parse <testcase> attributes
        result << testcase.attributes()

        // Parse <testcase>/(<error>|<failure>) elements
        result << testcase."*"
            .findAll { ["error", "failure"].contains(it.name()) }
            .collectEntries {[
                it.name(),
                it.attributes() << [ "text": it.text() ]
            ]}

        // Parse <testcase>/(<skipped>|<system-out>|<system-err>) elements
        result << testcase."*"
            .findAll { ["skipped", "system-out", "system-err"].contains(it.name()) }
            .collectEntries {
                [ it.name(), it.text() ]
            }

        return result
    }

    @NonCPS
    // Parse a JUnit XML <testsuite> element
    private static def parseJUnitXMLTestSuiteElement(def testsuite) {
        if (!testsuite.@name) {
            throw new IllegalArgumentException("Error: unable to parse JUnit XML <testsuite> element. Required attribute 'name' is missing.")
        }

        if (!testsuite.tests) {
            throw new IllegalArgumentException("Error: unable to parse JUnit XML <testsuite> element. Required attribute 'tests' is missing.")
        }

        def result = [:]

        // Parse <testsuite> attributes
        result << testsuite.attributes()

        // Parse <testsuite>/**/<property> elements
        result << [ "properties": testsuite."**"
            .findAll { it.name() == "property" }
            .collect { parseJUnitXMLPropertyElement(it) }
        ]

        // Parse <testsuite>/<testcase> elements
        result << [ "testcases": testsuite."*"
            .findAll { it.name() == "testcase" }
            .collect { parseJUnitXMLTestCaseElement(it) }
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

    @NonCPS
    // Parse a JUnit 4/5 XML document
    static def Map parseJUnitXML(String xml) {
        def root = new XmlSlurper().parseText(xml)
        if (!["testsuites", "testsuite"].contains(root.name())) {
            throw new IllegalArgumentException("Error: unable to transform JUnit XML document to JSON. 'xml' is not in a valid JUnit XML format.")
        }

        def result = [:]
        if (root.name() == "testsuite") {
            result = [
                "testsuites": [ parseJUnitXMLTestSuiteElement(root) ]
            ]
        } else if (root.name() == "testsuites") {
            result = parseJUnitXMLTestSuitesElement(root)
        }

        return result
    }

    @NonCPS
    // Parse a JUnit 4/5 XML document into a JSON string
    static def String transformJUnitXMLToJSON(String xml) {
        def parsed = parseJUnitXML(xml)
        new JsonBuilder(parsed).toPrettyString()
    }
}
