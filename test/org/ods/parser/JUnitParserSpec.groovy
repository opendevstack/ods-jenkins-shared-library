package org.ods.parser

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
            '<testsuite name="my-suite" tests="0" failures="0" errors="0" skipped="0"/>',
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

    def "Helper.toSimpleFormat"() {
        given:
        def xml = createTestResults(false)

        when:
        def result = JUnitParser.Helper.toSimpleFormat(xml)

        then:
        def expected = [
            "my-suite-1": [
                "my-testcase-1": [
                    name: "my-testcase-1",
                    classname: "app.MyTestCase1",
                    status: "Succeeded",
                    time: "1",
                    skipped: false,
                    systemOut: "",
                    systemErr: ""
                ],
                "my-testcase-2": [
                    name: "my-testcase-2",
                    classname: "app.MyTestCase2",
                    status: "Error",
                    time: "2",
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
            "my-suite-2": [
                "my-testcase-3": [
                    name: "my-testcase-3",
                    classname: "app.MyTestCase3",
                    status: "Failed",
                    time: "3",
                    failure: [
                        type: "my-failure-type",
                        message: "my-failure-message",
                        text: "This is a failure."
                    ],
                    skipped: false,
                    systemOut: "",
                    systemErr: ""
                ],
                "my-testcase-4": [
                    name: "my-testcase-4",
                    classname: "app.MyTestCase4",
                    status: "Missing",
                    time: "4",
                    skipped: true,
                    systemOut: "",
                    systemErr: ""
                ],
                "my-testcase-5": [
                    name: "my-testcase-5",
                    classname: "app.MyTestCase5",
                    status: "Succeeded",
                    time: "5",
                    skipped: false,
                    systemOut: "",
                    systemErr: ""
                ]
            ]
        ]

        result == expected
    }

    def "Helper.toSimpleErrorsFormat"() {
        given:
        def xml = createTestResults(false)

        when:
        def result = JUnitParser.Helper.toSimpleErrorsFormat(JUnitParser.Helper.toSimpleFormat(xml))

        then:
        def expected = [
            [
                type: "my-error-type",
                message: "my-error-message",
                text: "This is an error.",
                testsuites: [
                    "my-suite-1": [
                        testcases: [ "my-testcase-2" ]
                    ]
                ]
            ]
        ]

        result == expected as Set
    }

    def "Helper.toSimpleFailuresFormat"() {
        given:
        def xml = createTestResults(false)

        when:
        def result = JUnitParser.Helper.toSimpleFailuresFormat(JUnitParser.Helper.toSimpleFormat(xml))

        then:
        def expected = [
            [
                type: "my-failure-type",
                message: "my-failure-message",
                text: "This is a failure.",
                testsuites: [
                    "my-suite-2": [
                        testcases: [ "my-testcase-3" ]
                    ]
                ]
            ]
        ]

        result == expected as Set
    }
}
