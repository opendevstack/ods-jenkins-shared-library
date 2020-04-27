package org.ods.util

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

}
