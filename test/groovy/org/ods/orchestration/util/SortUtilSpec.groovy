package org.ods.orchestration.util

import spock.lang.*

import static util.FixtureHelper.*

import util.*

class SortUtilSpec extends SpecHelper {

    def "sort issues by properties"() {
        expect:
        SortUtil.sortIssuesByProperties(issues, properties) == result

        where:
        issues                           | properties || result
        [ [ name: "A" ], [ name: "B" ] ] | ["name"]   || [ [ name: "A" ], [ name: "B" ] ]
        [ [ name: "B" ], [ name: "A" ] ] | ["name"]   || [ [ name: "A" ], [ name: "B" ] ]

        [ [ key: "1", name: "A" ], [ key: "2", name: "B" ] ] | ["key", "name"] || [ [ key: "1", name: "A" ], [ key: "2", name: "B" ] ]
        [ [ key: "2", name: "B" ], [ key: "1", name: "A" ] ] | ["key", "name"] || [ [ key: "1", name: "A" ], [ key: "2", name: "B" ] ]
    }

    def "sort issues by numeric part of jira keys"() {
        expect:
        SortUtil.sortIssuesByProperties(issues, properties) == findResult

        where:
        issues                                                                  | properties       || findResult
        [ [ key: "KEY-1" ], [ key: "KEY-2" ], [ key: "KEY-12"] ]                | ["key"]          || [ [ key: "KEY-1" ], [ key: "KEY-2" ], [ key: "KEY-12" ] ]
        [ [ key: "KEY-12"], [ key: "KEY-2" ], [ key: "KEY-1" ] ]                | ["key"]          || [ [ key: "KEY-1" ], [ key: "KEY-2" ], [ key: "KEY-12" ] ]

        [ [ key: "KEY-1", key_2: "KEY-2" ], [ key: "KEY-1", key_2: "KEY-1" ] ]  | ["key", "key_2"] || [ [ key: "KEY-1", key_2: "KEY-1" ], [ key: "KEY-1", key_2: "KEY-2" ] ]
        [ [ key: "KEY-2", key_2: "KEY-2" ], [ key: "KEY-1", key_2: "KEY-20" ] ] | ["key", "key_2"] || [ [ key: "KEY-1", key_2: "KEY-20" ], [ key: "KEY-2", key_2: "KEY-2" ] ]
    }

    def "sort issues by key"() {
        expect:
        SortUtil.sortIssuesByKey(issues) == findResult

        where:
        issues                                                   || findResult
        [ [ key: "KEY-1" ], [ key: "KEY-2" ], [ key: "KEY-12"] ] || [ [ key: "KEY-1" ], [ key: "KEY-2" ], [ key: "KEY-12" ] ]
        [ [ key: "KEY-12"], [ key: "KEY-2" ], [ key: "KEY-1" ] ] || [ [ key: "KEY-1" ], [ key: "KEY-2" ], [ key: "KEY-12" ] ]
    }

    def "sort list of issue keys by numeric part of jira keys"() {
        expect:
        SortUtil.sortHeadingNumbers(issues, properties) == findResult

        where:
        issues                                                | properties || findResult
        [ [ key: "1.1" ], [ key: "2.3.2" ], [ key: "11"] ]    | "key"      || [ [ key: "1.1" ], [ key: "2.3.2" ], [ key: "11" ] ]
        [ [ key: "11"], [ key: "2.3.2" ], [ key: "1.1" ] ]    | "key"      || [ [ key: "1.1" ], [ key: "2.3.2" ], [ key: "11" ] ]
        [ [ key: "2.4"], [ key: "2.3.2" ], [ key: "2.3.1" ] ] | "key"      || [ [ key: "2.3.1" ], [ key: "2.3.2" ], [ key: "2.4" ] ]

    }

}
