package org.ods.parser

import groovy.json.JsonBuilder

// JUnit XML 5 Schema: https://github.com/junit-team/junit5/blob/master/platform-tests/src/test/resources/jenkins-junit.xsd
// Collection.find() failing in Jenkins: https://stackoverflow.com/questions/54364380/same-script-work-well-at-the-jenkins-script-console-but-not-at-the-pipline
class JUnitParser {
    @NonCPS
    // Parse a JUnit XML <testsuite> element including children
    private static def parseJUnitXMLTestSuiteElement(def testsuite) {
        def result = [:]

        // Parse testsuite attributes
        result << testsuite.attributes()

        // Parse testsuite.property elements
        result << [ "properties": testsuite."**"
            .findAll { it.name() == "property" }
            // Parse testsuite.property attributes
            .collect { it.attributes() }
        ]

        // Parse testsuite.testcase elements
        result << [ "testcases": testsuite."*"
            .findAll { it.name() == "testcase" }
            .collect {
                def testcase = [:]

                // Parse testsuite.testcase attributes
                testcase << it.attributes()

                testcase << it."*"
                    // Parse testsuite.testcase.(error/failure) elements
                    .findAll { ["error", "failure"].contains(it.name()) }
                    .collectEntries {[
                        it.name(),
                        // Parse testsuite.testcase.(error/failure) attributes
                        it.attributes() << [ "text": it.text() ]
                    ]}

                testcase << it."*"
                    // Parse testsuite.testcase.(skipped/system-out/system-err) elements
                    .findAll { ["skipped", "system-out", "system-err"].contains(it.name()) }
                    .collectEntries {
                        [ it.name(), it.text() ]
                    }
            }
        ]

        result << [
            // Handle testsuite.system-out elements
            "systemOut": testsuite."*".find { it.name() == "system-out" }.text(),
            // Handle testsuite.system-err elements
            "systemErr": testsuite."*".find { it.name() == "system-err" }.text()
        ]

        return result
    }

    @NonCPS
    // Parse a JUnit XML <testsuites> element including children
    private static def parseJUnitXMLTestSuitesElement(def testsuites) {
        def result = [:]

        // Parse testsuites attributes
        result << testsuites.attributes()
        result << [(testsuites.name()): testsuites.children()
            // Parse testsuite elements
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
