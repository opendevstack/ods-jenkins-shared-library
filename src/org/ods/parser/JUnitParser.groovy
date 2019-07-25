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
                [:] << it.attributes() << [ "text": it.text() ]
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
    static Map parseJUnitXML(String xml) {
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

    class Helper {
        @NonCPS
        // Transform the parser's result into a JSON string.
        static String toJSONString(Map xml) {
            new JsonBuilder(xml).toPrettyString()
        }

        @NonCPS
        /*
         * Transforms the parser's result into a (unique) set of test errors.
         * Annotates each error with indications on affected testsuite and -case.
         */
        static Set toSimpleErrorsFormat(def xml) {
            /*
             * Produces the following format:
             *
             * [
             *     [ // Error
             *         message: _,
             *         text: _,
             *         type: _,
             *         testsuites: [
             *             "Test Suite X": [
             *                 "testcases": [
             *                     "Test Case A",
             *                     "Test Case B"
             *                 ]
             *             ],
             *             "Test Suite Y": [
             *                 "testcases": [
             *                     "Test Case C"
             *                 ]
             *             ]
             *         ]
             *     ],
             *     ...
             * ]
             */

            def tests = toSimpleFormat(xml)

            // Compute a (unique) set of errors
            def errors = [] as Set
            tests.each { testsuiteName, testcases ->
                testcases.each { testcaseName, testcase ->
                    if (testcase.error) {
                        errors << testcase.error
                    }
                }
            }

            def result = errors
            result.each { error ->
                // Find all testcases that exhibit the current error
                tests.each { testsuiteName, testcases ->
                    testcases.each { testcaseName, testcase ->
                        // As we augment errors in result with "testsuites", we must
                        // remove this key for comparing with errors in testcases
                        def _error = error.findAll { it.key != "testsuites" }
                        if (testcase.error && testcase.error.equals(_error)) {
                            if (!error.containsKey("testsuites")) {
                                error.testsuites = [:]
                            }
                            
                            if (!error.testsuites.containsKey(testsuiteName)) {
                                error.testsuites[testsuiteName] = [ "testcases": [] ]
                            }

                            // Attach the name of each affected testsuite and testcase
                            error.testsuites[testsuiteName].testcases << testcaseName
                        }
                    }
                }
            }

            return result
        }

        @NonCPS
        /*
         * Transforms the parser's result into a (unique) set of test failures.
         * Annotates each failure with indications on affected testsuite and -case.
         */
        static Set toSimpleFailuresFormat(def xml) {
            /*
             * Produces the following format:
             *
             * [
             *     [ // Failure
             *         message: _,
             *         text: _,
             *         type: _,
             *         testsuites: [
             *             "Test Suite X": [
             *                 "testcases": [
             *                     "Test Case A",
             *                     "Test Case B"
             *                 ]
             *             ],
             *             "Test Suite Y": [
             *                 "testcases": [
             *                     "Test Case C"
             *                 ]
             *             ]
             *         ]
             *     ],
             *     ...
             * ]
             */

            def tests = toSimpleFormat(xml)

            // Compute a (unique) set of failures
            def failures = [] as Set
            tests.each { testsuiteName, testcases ->
                testcases.each { testcaseName, testcase ->
                    if (testcase.failure) {
                        failures << testcase.failure
                    }
                }
            }

            def result = failures
            result.each { failure ->
                // Find all testcases that exhibit the current failure
                tests.each { testsuiteName, testcases ->
                    testcases.each { testcaseName, testcase ->
                        // As we augment failures in result with "testsuites", we must
                        // remove this key for comparing with failures in testcases
                        def _failure = failure.findAll { it.key != "testsuites" }
                        if (testcase.failure && testcase.failure.equals(_failure)) {
                            if (!failure.containsKey("testsuites")) {
                                failure.testsuites = [:]
                            }
                            
                            if (!failure.testsuites.containsKey(testsuiteName)) {
                                failure.testsuites[testsuiteName] = [ "testcases": [] ]
                            }

                            // Attach the name of each affected testsuite and testcase
                            failure.testsuites[testsuiteName].testcases << testcaseName
                        }
                    }
                }
            }

            return result
        }

        @NonCPS
        // Transform the parser's result into a simple format.
        static Map toSimpleFormat(Map xml) {
            /*
             * Produces the following format:
             *
             * Returns
             * [
             *     "Test Suite X": [
             *         "Test Case A": [
             *             "classname": _,
             *             "failure": [
             *                 "message": _,
             *                 "text": _,
             *                 "type": _
             *             ],
             *             "name": "Test Case A",
             *             "time": 0.1
             *         ]
             *     ],
             *     "Test Suite Y": [
             *         "Test Case B": [
             *             "classname": _,
             *             "name": "Test Case B",
             *             "time": 0.2
             *         ],
             *         "Test Case C": [
             *             "classname": _,
             *             "name": "Test Case C",
             *             "time": 0.3
             *         ]
             *     ]
             * ]
             */

            def result = [:]

            xml.testsuites.each { testsuite ->
                def testcases = [:]

                testsuite.testcases.each { testcase ->
                    testcases << [ (testcase.name): testcase ]
                }

                result << [ (testsuite.name): testcases ]
            }

            return result
        }
    }
}
