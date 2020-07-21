package org.ods.orchestration.util

import org.ods.util.IPipelineSteps
import org.ods.util.Logger
import org.ods.util.PipelineSteps
import util.SpecHelper

class DocumentHistorySpec extends SpecHelper {

    IPipelineSteps steps
    Logger logger
    Map jiraData10
    Map jiraData11_first
    Map jiraDataFix
    Map jiraData11_second
    Map jiraData20

    List<DocumentHistoryEntry> entries10
    List<DocumentHistoryEntry> entries11_first
    List<DocumentHistoryEntry> entriesFix
    List<DocumentHistoryEntry> entries11_second
    List<DocumentHistoryEntry> entries20

    def setup() {
        steps = Spy(PipelineSteps)
        logger = Mock(Logger)

        def cmp = {  name, String version = null ->  [key: "Technology-${name}" as String, name: name, version:version] }
        def epc = {  name, String version = null ->  [key: "EPIC-${name}" as String, description: name, version:version] }
        def req = {  name, String version = null ->  [key: "REQ-${name}" as String, description:name, version:version] }
        def ts  = {  name, String version = null ->  [key: "TS-${name}" as String, description:name, version:version] }
        def rsk = {  name, String version = null ->  [key: "RSK-${name}" as String, description:name, version:version] }
        def tst = {  name, String version = null ->  [key: "TST-${name}" as String, description:name, version:version] }
        def mit = {  name, String version = null ->  [key: "MIT-${name}" as String, description:name, version:version] }


        def firstProjectVersion = '1.0'
        def secondProjectVersion = '1.1'
        def bugfixProjectVersion = '1.0.1'
        def fourthProjectVersion = '2.0'

        def cmp1 = cmp('frontend', firstProjectVersion )
        def cmp2 = cmp('backend', firstProjectVersion)
        def epc1 = epc('1', secondProjectVersion)
        def req1 = req('1', firstProjectVersion)
        def req2 = req('2', secondProjectVersion)
        def req3 = req('3', fourthProjectVersion) + [predecessors: [req1.key]]
        def ts1 = ts('1', firstProjectVersion)
        def rsk1 = rsk('1', firstProjectVersion)
        def tst1 = tst('1', firstProjectVersion)
        def tst2 = tst('toDiscontinue', firstProjectVersion)
        def tst3 = tst('bugFixMandatory', bugfixProjectVersion)
        def mit1 = mit('toChange', firstProjectVersion)
        def mit2 = mit('changed', secondProjectVersion) + [predecessors: [mit1.key]]


        this.jiraData10 = [
            bugs        : [:],
            version     : firstProjectVersion,
            previousVersion: null,
            components  : [(cmp1.key):cmp1],
            epics       : [:],
            mitigations : [(mit1.key):mit1],
            requirements: [(req1.key): req1],
            risks       : [(rsk1.key): rsk1],
            tests       : [(tst1.key): tst1, (tst2.key): tst2],
            techSpecs   : [(ts1.key):ts1],
            docs        : [:],
            discontinuationsPerType : [:]
        ]

        this.entries10 = [new DocumentHistoryEntry([
            bugs        : [],
            components  : [[key: cmp1.key, action: 'add']],
            epics       : [],
            mitigations : [[key: mit1.key, action: 'add']],
            requirements: [[key: req1.key, action: 'add']],
            risks       : [[key: rsk1.key, action: 'add']],
            tests       : [[key: tst1.key, action: 'add'], [key: tst2.key, action: 'add']],
            techSpecs   : [[key: ts1.key, action: 'add']]], 1L, firstProjectVersion, '', '')]

        this.jiraData11_first = [
            bugs        : [:],
            version     : secondProjectVersion,
            previousVersion: firstProjectVersion,
            components  : [(cmp1.key):cmp1],
            epics       : [(epc1.key):epc1],
            mitigations : [(mit2.key):mit2],
            requirements: [(req1.key): req1, (req2.key): req2],
            risks       : [(rsk1.key): rsk1],
            tests       : [(tst1.key): tst1],
            techSpecs   : [(ts1.key):ts1],
            docs        : [:],
            discontinuationsPerType : [tests: [tst2.key]]
        ]

        this.entries11_first = [new DocumentHistoryEntry([
            bugs        : [],
            components  : [],
            epics       : [[key: epc1.key, action:'add']],
            mitigations : [[key: mit2.key, action: 'change', predecessors: mit2.predecessors]],
            requirements: [[key: req2.key, action: 'add']],
            risks       : [],
            tests       : [[key: tst2.key, action: 'discontinue']],
            techSpecs   : []], 2L, secondProjectVersion, firstProjectVersion ,'')] + entries10

        this.jiraDataFix = [
            bugs        : [:],
            version     : bugfixProjectVersion,
            previousVersion: firstProjectVersion,
            components  : [(cmp1.key):cmp1, (cmp2.key): cmp2],
            epics       : [:],
            mitigations : [(mit1.key):mit1],
            requirements: [(req1.key): req1],
            risks       : [(rsk1.key): rsk1],
            tests       : [(tst1.key): tst1, (tst2.key): tst2, (tst3.key):tst3],
            techSpecs   : [(ts1.key):ts1],
            docs        : [:],
            discontinuationsPerType : [:]
        ]

        this.entriesFix = [new DocumentHistoryEntry([
            bugs        : [],
            components  : [],
            epics       : [],
            mitigations : [],
            requirements: [],
            risks       : [],
            tests       : [[key: tst3.key, action: 'add']],
            techSpecs   : []], 3L, bugfixProjectVersion, firstProjectVersion,
            "This document version invalidates the changes done in version '${secondProjectVersion}'.")] + entries11_first

        this.jiraData11_second = [
            bugs        : [:],
            version     : secondProjectVersion,
            previousVersion: bugfixProjectVersion,
            components  : [(cmp1.key):cmp1],
            epics       : [(epc1.key):epc1],
            mitigations : [(mit2.key):mit2],
            requirements: [(req1.key): req1, (req2.key): req2],
            risks       : [(rsk1.key): rsk1],
            tests       : [(tst1.key): tst1, (tst3.key):tst3],
            techSpecs   : [(ts1.key):ts1],
            docs        : [:],
            discontinuationsPerType : [tests: [tst2.key]]
        ]

        this.entries11_second = [new DocumentHistoryEntry([
            bugs        : [],
            components  : [],
            epics       : [[key: epc1.key, action:'add']],
            mitigations : [[key: mit2.key, action: 'change', predecessors: mit2.predecessors]],
            requirements: [[key: req2.key, action: 'add']],
            risks       : [],
            tests       : [[key: tst2.key, action: 'discontinue']],
            techSpecs   : [],
        ], 4L, secondProjectVersion, bugfixProjectVersion, '')] + entriesFix

        this.jiraData20 = [
            bugs        : [:],
            version     : fourthProjectVersion,
            previousVersion: secondProjectVersion,
            components  : [(cmp1.key):cmp1],
            epics       : [:],
            mitigations : [(mit2.key):mit2],
            requirements: [(req2.key): req2, (tst3.key):tst3, (req3.key): req3],
            risks       : [(rsk1.key): rsk1],
            tests       : [(tst1.key): tst1],
            techSpecs   : [(ts1.key):ts1],
            docs        : [:],
            discontinuationsPerType : [:]
        ]

        this.entries20 = [new DocumentHistoryEntry([
            bugs        : [],
            components  : [],
            epics       : [],
            mitigations : [],
            requirements: [[key: req3.key, action: 'change', predecessors: req3.predecessors]],
            risks       : [],
            tests       : [],
            techSpecs   : []], 5L, fourthProjectVersion, secondProjectVersion, '')] + entries11_second
    }



    def "builds docHistory for first project version"() {
        given:
        def jiraData = jiraData10
        def targetEnvironment = 'D'
        def savedVersionId = null

        def versionEntries = entries10
        DocumentHistory history = Spy(constructorArgs: [steps, logger, targetEnvironment])

        when:
        history.load(jiraData, savedVersionId)

        then:
        0 * history.loadSavedDocHistoryData(_)

        then:
        history.latestVersionId == 1L
        println("Entries are equals " + entryListIsEquals(history.data, versionEntries))
        assert entryListIsEquals(history.data, versionEntries)
        history.data == versionEntries
    }

    def "builds docHistory for first second version"() {
        given:
        def jiraData = jiraData11_first
        def targetEnvironment = 'D'
        def savedVersionId = 1L
        def savedData = entries10

        def versionEntries = entries11_first
        DocumentHistory history = Spy(constructorArgs: [steps, logger, targetEnvironment])

        when:
        history.load(jiraData, savedVersionId)

        then:
        1 * history.loadSavedDocHistoryData(_) >> savedData

        then:
        history.latestVersionId == 2L
        assert entryListIsEquals(history.data, versionEntries)
        history.data == versionEntries
    }

    def "builds add rationale when there are concurrent versions"() {
        given:
        def jiraData = jiraDataFix
        def targetEnvironment = 'D'
        def savedVersionId = 2L
        def savedData = entries11_first

        def versionEntries = entriesFix
        DocumentHistory history = Spy(constructorArgs: [steps, logger, targetEnvironment])

        when:
        history.load(jiraData, savedVersionId)

        then:
        1 * history.loadSavedDocHistoryData(savedVersionId) >> savedData

        then:
        history.latestVersionId == 3L
        assert entryListIsEquals(history.data, versionEntries)
        history.data == versionEntries
    }


    def "builds the reset from second version ontop of the bugfix"() {
        given:
        def jiraData = jiraData11_second
        def targetEnvironment = 'D'
        def savedVersionId = 3L
        def savedData = entriesFix

        def versionEntries = entries11_second
        DocumentHistory history = Spy(constructorArgs: [steps, logger, targetEnvironment])

        when:
        history.load(jiraData, savedVersionId)

        then:
        1 * history.loadSavedDocHistoryData(savedVersionId) >> savedData

        then:
        history.latestVersionId == 4L
        assert entryListIsEquals(history.data, versionEntries)
        history.data == versionEntries
    }

    def "builds normal version without concurrent message"() {
        given:
        def jiraData = jiraData20
        def targetEnvironment = 'D'
        def savedVersionId = 4L
        def savedData = entries11_second

        def versionEntries = entries20
        DocumentHistory history = Spy(constructorArgs: [steps, logger, targetEnvironment])

        when:
        history.load(jiraData, savedVersionId)

        then:
        1 * history.loadSavedDocHistoryData(savedVersionId) >> savedData

        then:
        history.latestVersionId == 5L
        assert entryListIsEquals(history.data, versionEntries)
        history.data == versionEntries
    }

    Boolean entryIsEquals(DocumentHistoryEntry a, DocumentHistoryEntry b) {
        println("analyzing ID " + a.getEntryId() + " and " + b.getEntryId())
        if (a.getEntryId() != b.getEntryId()) return false
        println("\tID equals")
        if (a.getProjectVersion() != b.getProjectVersion()) return false
        println("\tversion equals")
        if (a.getPreviousProjectVersion() != b.getPreviousProjectVersion()) return false
        println("\tpreviousProjVersion equals")
        if (a.rational != b.rational) return false
        println("\trational equals")
        if (a.getDelegate() != b.getDelegate()) return false
        return true
    }

    Boolean entryListIsEquals(List<DocumentHistoryEntry> entriesA, List<DocumentHistoryEntry> entriesB) {
        if (entriesA.size() != entriesB.size()) return false
        def issuesBKeys = entriesB.collect{it.getEntryId()}
        def areEquals = entriesA.collect{ DocumentHistoryEntry issueA ->
            if (! issuesBKeys.contains(issueA.getEntryId())) return false
            def correspondentIssueB = entriesB.find{it.getEntryId() == issueA.getEntryId()}
            println("Entry A " + issueA)
            println("Entry B " + correspondentIssueB)

            //println("entries are equal?? " + entryIsEquals(issueA, correspondentIssueB))
            if (! entryIsEquals(issueA, correspondentIssueB)) {
                println("THINGS ARE NOT OK!!!! returning false")
                return false
            }
            return true
        }
        return areEquals.contains(true)
    }
}
