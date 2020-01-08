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
}
