package org.ods.orchestration.parser

import groovy.json.JsonBuilder

import spock.lang.*

import static util.FixtureHelper.*

import util.*

class JUnitParserSpec extends SpecHelper {

    def "invalid root"() {
        when:
        JUnitParser.parseJUnitXML(xmlString)

        then:
        thrown IllegalArgumentException

        where:
        xmlString << [ null, "", "<testcase/>" ]
    }

    def "properties"() {
        when:
        def result = JUnitParser.parseJUnitXML(xmlString)

        then:
        result == expected

        where:
        xmlString << [
            """
            <testsuite name="my-suite" tests="0">
                <properties>
                    <property name="my-property-a" value="my-property-a-value"/>
                    <property name="my-property-b" value="my-property-b-value"/>
                    <property name="my-property-c" value="my-property-c-value"/>
                </properties>
            </testsuite>
            """
        ]

        expected << [
            [
                testsuites: [
                    [
                        name: "my-suite",
                        tests: "0",
                        properties: [
                            [ name: "my-property-a", value: "my-property-a-value" ],
                            [ name: "my-property-b", value: "my-property-b-value" ],
                            [ name: "my-property-c", value: "my-property-c-value" ]
                        ],
                        testcases: [],
                        systemOut: "",
                        systemErr: ""
                    ]
                ]
            ]
        ]
    }

    def "testcase without name attribute"() {
        given:
        def xmlString = """
        <testsuite name="my-suite" tests="0">
            <testcase/>
        </testsuite>
        """

        when:
        JUnitParser.parseJUnitXML(xmlString)

        then:
        thrown IllegalArgumentException
    }

    def "testcase"() {
        when:
        def result = JUnitParser.parseJUnitXML(xmlString)

        then:
        result == expected

        where:
        xmlString << [
            """
            <testsuite name="my-suite" tests="0">
                <testcase name="my-testcase"/>
            </testsuite>
            """,
            """
            <testsuite name="my-suite" tests="0">
                <testcase name="my-testcase">
                    <error type="my-error-type" message="my-error-message">This is an error.</error>
                </testcase>
            </testsuite>
            """,
            """
            <testsuite name="my-suite" tests="0">
                <testcase name="my-testcase">
                    <failure type="my-failure-type" message="my-failure-message">This is a failure.</failure>
                </testcase>
            </testsuite>
            """,
            """
            <testsuite name="my-suite" tests="0">
                <testcase name="my-testcase">
                    <skipped/>
                </testcase>
            </testsuite>
            """,
            """
            <testsuite name="my-suite" tests="0">
                <testcase name="my-testcase">
                    <system-out>my-testcase-stdout</system-out>
                    <system-err>my-testcase-stderr</system-err>
                </testcase>
            </testsuite>
            """
        ]

        expected << [
            [
                testsuites: [
                    [
                        name: "my-suite",
                        tests: "0",
                        properties: [],
                        testcases: [
                            [
                                name: "my-testcase",
                                skipped: false,
                                systemOut: "",
                                systemErr: ""
                            ]
                        ],
                        systemOut: "",
                        systemErr: ""
                    ]
                ]
            ],
            [
                testsuites: [
                    [
                        name: "my-suite",
                        tests: "0",
                        properties: [],
                        testcases: [
                            [
                                name: "my-testcase",
                                error: [
                                    type: "my-error-type",
                                    message: "my-error-message",
                                    text: "This is an error."
                                ],
                                skipped: false,
                                systemOut: "",
                                systemErr: ""
                            ]
                        ],
                        systemOut: "",
                        systemErr: ""
                    ]
                ]
            ],
            [
                testsuites: [
                    [
                        name: "my-suite",
                        tests: "0",
                        properties: [],
                        testcases: [
                            [
                                name: "my-testcase",
                                failure: [
                                    type: "my-failure-type",
                                    message: "my-failure-message",
                                    text: "This is a failure."
                                ],
                                skipped: false,
                                systemOut: "",
                                systemErr: ""
                            ]
                        ],
                        systemOut: "",
                        systemErr: ""
                    ]
                ]
            ],
            [
                testsuites: [
                    [
                        name: "my-suite",
                        tests: "0",
                        properties: [],
                        testcases: [
                            [
                                name: "my-testcase",
                                skipped: true,
                                systemOut: "",
                                systemErr: ""
                            ]
                        ],
                        systemOut: "",
                        systemErr: ""
                    ]
                ]
            ],
            [
                testsuites: [
                    [
                        name: "my-suite",
                        tests: "0",
                        properties: [],
                        testcases: [
                            [
                                name: "my-testcase",
                                skipped: false,
                                systemOut: "my-testcase-stdout",
                                systemErr: "my-testcase-stderr"
                            ]
                        ],
                        systemOut: "",
                        systemErr: ""
                    ]
                ]
            ]
        ]
    }

    def "testsuite without name attribute"() {
        given:
        def xmlString = "<testsuite/>"

        when:
        JUnitParser.parseJUnitXML(xmlString)

        then:
        thrown IllegalArgumentException
    }

    def "testsuite without tests attribute"() {
        given:
        def xmlString = '<testsuite name="my-suite"/>'

        when:
        JUnitParser.parseJUnitXML(xmlString)

        then:
        def e = thrown IllegalArgumentException
        e.message == "Error: unable to parse JUnit XML <testsuite> element. Required attribute 'tests' is missing."
    }

    def "testsuite"() {
        when:
        def result = JUnitParser.parseJUnitXML(xmlString)

        then:
        result == expected

        where:
        xmlString << [
            '<testsuite name="my-suite" tests="0" failures="0" errors="0" skipped="0" timestamp="2000-01-01T00:00:00"/>',
            """
            <testsuite name="my-suite" tests="0">
                <testcase name="my-testcase"/>
            </testsuite>
            """,
        ]

        expected << [
            [
                testsuites: [
                    [
                        name: "my-suite",
                        tests: "0",
                        failures: "0",
                        errors: "0",
                        skipped: "0",
                        properties: [],
                        testcases: [],
                        systemOut: "",
                        systemErr: "",
                        timestamp: "2000-01-01T00:00:00"
                    ]
                ]
            ],
            [
                testsuites: [
                    [
                        name: "my-suite",
                        tests: "0",
                        properties: [],
                        testcases: [
                            [
                                name: "my-testcase",
                                skipped: false,
                                systemOut: "",
                                systemErr: ""
                            ]
                        ],
                        systemOut: "",
                        systemErr: ""
                    ]
                ]
            ]
        ]
    }

    def "testsuites"() {
        when:
        def result = JUnitParser.parseJUnitXML(xmlString)

        then:
        result == expected

        where:
        xmlString << [
            "<testsuites/>",
            '<testsuites name="my-suites" tests="0" failures="0" errors="0" skipped="0"/>',
            """
            <testsuites name="my-suites">
                <testsuite name="my-suite" tests="0"/>
            </testsuites>
            """,
            """
            <testsuites name="my-suites">
                <testsuite name="my-suite" tests="0">
                    <testcase name="my-testcase"/>
                </testsuite>
            </testsuites>
            """,
        ]

        expected << [
            [
                testsuites: []
            ],
            [
                name: "my-suites",
                tests: "0",
                failures: "0",
                errors: "0",
                skipped: "0",
                testsuites: []
            ],
            [
                name: "my-suites",
                testsuites: [
                    [
                        name: "my-suite",
                        tests: "0",
                        properties: [],
                        testcases: [],
                        systemOut: "",
                        systemErr: ""
                    ]
                ]
            ],
            [
                name: "my-suites",
                testsuites: [
                    [
                        name: "my-suite",
                        tests: "0",
                        properties: [],
                        testcases: [
                            [
                                name: "my-testcase",
                                skipped: false,
                                systemOut: "",
                                systemErr: ""
                            ]
                        ],
                        systemOut: "",
                        systemErr: ""
                    ]
                ]
            ]
        ]
    }

    def "Helper.toJSONString"() {
        given:
        def xml = JUnitParser.parseJUnitXML(
            """
            <testsuites name="my-suites">
                <testsuite name="my-suite" tests="0">
                    <testcase name="my-testcase"/>
                </testsuite>
            </testsuites>
            """
        )

        when:
        def result = JUnitParser.Helper.toJSONString(xml)

        then:
        def expected = new JsonBuilder([
            name: "my-suites",
            testsuites: [
                [
                    tests: "0",
                    name: "my-suite",
                    properties: [],
                    testcases: [
                        [
                            name: "my-testcase",
                            skipped: false,
                            systemOut: "",
                            systemErr: ""
                        ]
                    ],
                    systemOut: "",
                    systemErr: ""
                ]
            ]
        ]).toPrettyString()

        result == expected
    }

    def "Helper.getErrors"() {
        given:
        def xml = createTestResults()

        when:
        def result = JUnitParser.Helper.getErrors(xml)

        then:
        def expected = [
            [
                type: "my-error-type",
                message: "my-error-message",
                text: "This is an error.",
                testsuites: [
                    [
                        name: "my-suite-1",
                        testcases: [
                            [
                                name: "JIRA2_my-testcase-2"
                            ]
                        ]
                    ]
                ]
            ]
        ]

        result == expected as Set
    }

    def "Helper.getFailures"() {
        given:
        def xml = createTestResults()

        when:
        def result = JUnitParser.Helper.getFailures(xml)

        then:
        def expected = [
            [
                type: "my-failure-type",
                message: "my-failure-message",
                text: "This is a failure.",
                testsuites: [
                    [
                        name: "my-suite-2",
                        testcases: [
                            [ name: "JIRA3_my-testcase-3" ]
                        ]
                    ]
                ]
            ]
        ]

        result == expected as Set
    }
}
