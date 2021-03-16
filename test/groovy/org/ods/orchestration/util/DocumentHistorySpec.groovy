package org.ods.orchestration.util

import org.ods.orchestration.service.leva.ProjectDataBitbucketRepository
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
    Map jiraData20Alt

    List<DocumentHistoryEntry> entries10
    List<DocumentHistoryEntry> entries11_first
    List<DocumentHistoryEntry> entriesFix
    List<DocumentHistoryEntry> entries11_second
    List<DocumentHistoryEntry> entries20
    List<DocumentHistoryEntry> entries20Alt

    def setup() {
        steps = Mock(PipelineSteps) {
            getEnv() >> { return [WORKSPACE: '/work/spa/ce'] }
        }
        logger = Mock(Logger)

        def cmp = {  name, String version = null ->  [key: "Technology-${name}" as String, name: name, versions: [version]]}
        def epc = {  name, String version = null ->  [key: "EPC-${name}" as String, description: name, versions: [version]]}
        def req = {  name, String version = null ->  [key: "REQ-${name}" as String, description:name, versions: [version]]}
        def ts  = {  name, String version = null ->  [key: "TS-${name}"  as String, description:name, versions: [version]]}
        def rsk = {  name, String version = null ->  [key: "RSK-${name}" as String, description:name, versions: [version]]}
        def tst = {  name, String version = null ->  [key: "TST-${name}" as String, description:name, versions: [version]]}
        def mit = {  name, String version = null ->  [key: "MIT-${name}" as String, description:name, versions: [version]]}


        def firstProjectVersion = '1.0'
        def secondProjectVersion = '1.1'
        def bugfixProjectVersion = '1.0.1'
        def fourthProjectVersion = '2.0'

        def cmp1 = cmp('frontend', firstProjectVersion )
        def cmp2 = cmp('backend', firstProjectVersion)
        def epc1 = epc('1', secondProjectVersion)
        def epc2 = epc('2', fourthProjectVersion)
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
            bugs                  : [],
            (Project.JiraDataItem.TYPE_DOCS): [],
            components            : [[key: cmp1.key, action: 'add']],
            epics                 : [],
            mitigations           : [[key: mit1.key, action: 'add']],
            requirements          : [[key: req1.key, action: 'add']],
            risks                 : [[key: rsk1.key, action: 'add']],
            tests                 : [[key: tst1.key, action: 'add'], [key: tst2.key, action: 'add']],
            techSpecs             : [[key: ts1.key, action: 'add']]], 1L, firstProjectVersion, '',
            "Initial document version.")]

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
            discontinuationsPerType : [tests: [tst2]]
        ]

        this.entries11_first = [new DocumentHistoryEntry([
            bugs        : [],
            (Project.JiraDataItem.TYPE_DOCS): [],
            components  : [],
            epics       : [[key: epc1.key, action:'add']],
            mitigations : [[key: mit2.key, action: 'change', predecessors: mit2.predecessors]],
            requirements: [[key: req2.key, action: 'add']],
            risks       : [],
            tests       : [[key: tst2.key, action: 'discontinue']],
            techSpecs   : []], 2L, secondProjectVersion, firstProjectVersion ,
            "Modifications for project version '${secondProjectVersion}'.")] + entries10

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
            (Project.JiraDataItem.TYPE_DOCS): [],
            components  : [],
            epics       : [],
            mitigations : [],
            requirements: [],
            risks       : [],
            tests       : [[key: tst3.key, action: 'add']],
            techSpecs   : []], 3L, bugfixProjectVersion, firstProjectVersion,
            "Modifications for project version '${bugfixProjectVersion}'." +
                " This document version invalidates the changes done in document version '2'.")] + entries11_first

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
            discontinuationsPerType : [tests: [tst2]]
        ]

        this.entries11_second = [new DocumentHistoryEntry([
            bugs        : [],
            (Project.JiraDataItem.TYPE_DOCS): [],
            components  : [],
            epics       : [[key: epc1.key, action:'add']],
            mitigations : [[key: mit2.key, action: 'change', predecessors: mit2.predecessors]],
            requirements: [[key: req2.key, action: 'add']],
            risks       : [],
            tests       : [[key: tst2.key, action: 'discontinue']],
            techSpecs   : [],
        ], 4L, secondProjectVersion, bugfixProjectVersion,
            "Modifications for project version '${secondProjectVersion}'.")] + entriesFix

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

        this.jiraData20Alt = this.jiraData20 + [
            epics       : [(epc2.key):epc2]
        ]

        this.entries20 = [new DocumentHistoryEntry([
            bugs        : [],
            (Project.JiraDataItem.TYPE_DOCS): [],
            components  : [],
            epics       : [],
            mitigations : [],
            requirements: [[key: req3.key, action: 'change', predecessors: req3.predecessors]],
            risks       : [],
            tests       : [],
            techSpecs   : []], 5L, fourthProjectVersion, secondProjectVersion,
            "Modifications for project version '${fourthProjectVersion}'.")] + entries11_second

        this.entries20Alt = [new DocumentHistoryEntry([
            bugs        : [],
            (Project.JiraDataItem.TYPE_DOCS): [],
            components  : [],
            epics       : [[key: epc2.key, action: 'add']],
            mitigations : [],
            requirements: [[key: req3.key, action: 'change', predecessors: req3.predecessors]],
            risks       : [],
            tests       : [],
            techSpecs   : []], 6L, fourthProjectVersion, secondProjectVersion,
            "Modifications for project version '${fourthProjectVersion}'. This document version invalidates the changes done in document version '5'.")] + entries20
    }

    protected List<String> computeIssuesDoc(List<DocumentHistoryEntry> dhe) {
        dhe.collect { e ->
            e.findAll { Project.JiraDataItem.TYPES.contains(it.key) }
                .collect { type, actions -> actions.collect { it.key } }
        }.flatten()
    }


    def "builds docHistory for first project version"() {
        given:
        def jiraData = jiraData10
        def targetEnvironment = 'D'
        def savedVersionId = null

        def versionEntries = entries10
        def docContent = computeIssuesDoc(versionEntries)
        DocumentHistory history = Spy(constructorArgs: [steps, logger, targetEnvironment, 'DocType'])

        when:
        history.load(jiraData, savedVersionId, docContent)

        then:
        0 * history.loadSavedDocHistoryData(_)

        then:
        history.latestVersionId == 1L
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
        def docContent = computeIssuesDoc(versionEntries)
        DocumentHistory history = Spy(constructorArgs: [steps, logger, targetEnvironment, 'DocType'])

        when:
        history.load(jiraData, savedVersionId, docContent)

        then:
        1 * history.loadSavedDocHistoryData(_) >> savedData

        then:
        history.latestVersionId == 2L
        assert entryListIsEquals(history.data, versionEntries)
        history.data == versionEntries
    }

    def "builds anomalous docHistory with a failed attempt to freeze a version"() {
        given:
        def jiraData = jiraData20Alt
        def targetEnvironment = 'D'
        def savedVersionId = 5L
        def savedData = entries20

        def versionEntries = entries20Alt
        def docContent = computeIssuesDoc(versionEntries)
        DocumentHistory history = Spy(constructorArgs: [steps, logger, targetEnvironment, 'DocType'])

        when:
        history.load(jiraData, savedVersionId, docContent)

        then:
        1 * history.loadSavedDocHistoryData(savedVersionId) >> savedData

        then:
        history.latestVersionId == 6L
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
        def docContent = computeIssuesDoc(versionEntries)
        DocumentHistory history = Spy(constructorArgs: [steps, logger, targetEnvironment, 'DocType'])

        when:
        history.load(jiraData, savedVersionId, docContent)

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
        def docContent = computeIssuesDoc(versionEntries)
        DocumentHistory history = Spy(constructorArgs: [steps, logger, targetEnvironment, 'DocType'])

        when:
        history.load(jiraData, savedVersionId, docContent)

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
        def docContent = computeIssuesDoc(versionEntries)
        DocumentHistory history = Spy(constructorArgs: [steps, logger, targetEnvironment, 'DocType'])

        when:
        history.load(jiraData, savedVersionId, docContent)

        then:
        1 * history.loadSavedDocHistoryData(savedVersionId) >> savedData

        then:
        history.latestVersionId == 5L
        assert entryListIsEquals(history.data, versionEntries)
        history.data == versionEntries
    }

    def "returns empty doc history and logs a warning if some issues do not have a version"() {
        setup:
        def targetEnvironment = 'D'
        def savedVersionId = 0L
        def issueNV = [key: "ISSUE-A"]
        def issueV = [key: "ISSUE-A", versions: ['2']]

        def base_saved_data = [
            bugs        : [:],
            version     : "1",
            components  : [:],
            epics       : [:],
            mitigations : [:],
            requirements: [:],
            risks       : [:],
            tests       : [:],
            techSpecs   : [:],
            docs        : [:],
            discontinuationsPerType : [:]
        ]

        def docContent = ["ISSUE-A"]

        DocumentHistory history = Spy(constructorArgs: [steps, logger, targetEnvironment, 'DocType'])

        when: "We have a versioned component"
        history.load(base_saved_data + [components: [(issueV.key):issueV]], savedVersionId, docContent)

        then:
        history.allIssuesAreValid

        when: "We have a non versioned component"
        history.load(base_saved_data + [components: [(issueNV.key):issueNV]], savedVersionId, docContent)

        then:
        ! history.allIssuesAreValid

        when: "We have a versioned epic"
        history.load(base_saved_data + [epics: [(issueV.key):issueV]], savedVersionId, docContent)

        then:
        history.allIssuesAreValid

        when: "We have a non versioned epic"
        history.load(base_saved_data + [epics: [(issueNV.key):issueNV]], savedVersionId, docContent)

        then:
        ! history.allIssuesAreValid

        when: "We have a versioned risks"
        history.load(base_saved_data + [risks: [(issueV.key):issueV]], savedVersionId, docContent)

        then:
        history.allIssuesAreValid

        when: "We have a non versioned risks"
        history.load(base_saved_data + [risks: [(issueNV.key):issueNV]], savedVersionId, docContent)

        then:
        ! history.allIssuesAreValid

        when: "We have a versioned requirements"
        history.load(base_saved_data + [requirements: [(issueV.key):issueV]], savedVersionId, docContent)

        then:
        history.allIssuesAreValid

        when: "We have a non versioned requirements"
        history.load(base_saved_data + [requirements: [(issueNV.key):issueNV]], savedVersionId, docContent)

        then:
        ! history.allIssuesAreValid

        when: "We have a versioned tests"
        history.load(base_saved_data + [tests: [(issueV.key):issueV]], savedVersionId, docContent)

        then:
        history.allIssuesAreValid

        when: "We have a non versioned tests"
        history.load(base_saved_data + [tests: [(issueNV.key):issueNV]], savedVersionId, docContent)

        then:
        ! history.allIssuesAreValid

        when: "We have a versioned techSpecs"
        history.load(base_saved_data + [techSpecs: [(issueV.key):issueV]], savedVersionId, docContent)

        then:
        history.allIssuesAreValid

        when: "We have a non versioned techSpecs"
        history.load(base_saved_data + [techSpecs: [(issueNV.key):issueNV]], savedVersionId, docContent)

        then:
        ! history.allIssuesAreValid
    }

    def "issue type docs is shown as documentation chapter in the history and includes heading number and summary"() {
        setup:
        def targetEnvironment = 'D'
        def savedVersionId = 1L

        def base_saved_data = [
            bugs                   : [:],
            version                : "1",
            components             : [:],
            epics                  : [:],
            mitigations            : [:],
            requirements           : [:],
            risks                  : [:],
            tests                  : [:],
            techSpecs              : [:],
            (Project.JiraDataItem.TYPE_DOCS) : [
                'added1':[key: "added1", versions: ['1'], number: 'numberOfAdded1', heading: 'heading', documents: ['doc1', 'doc2']],
                'added2':[key: "added2", versions: ['1'], number: 'numberOfAdded2', heading: 'heading', documents: ['doc1'], predecessors: []],
                'otherDocCh':[key: "otherDocCh", versions: ['1'], number: 'shouldNotAppear', heading: 'heading', documents: ['doc2']],
                'changed1':[key: "changed1", versions: ['1'], number: 'numberOfChanged', heading: 'heading', documents: ['doc1', 'doc2'], predecessors: ['somePredec']],
            ],
            discontinuationsPerType: [(Project.JiraDataItem.TYPE_DOCS) : [[key: "discontinued", versions: ['1'], number: 'discontinuedNum', heading: 'heading', documents: ['doc1']]]]
        ]


        DocumentHistory history = Spy(constructorArgs: [steps, logger, targetEnvironment, 'doc1'])
        history.loadSavedDocHistoryData(savedVersionId) >> [new DocumentHistoryEntry([:], 1L, '1.0', '', 'Initial document version.')]
        history.load(base_saved_data, savedVersionId, ['added1', 'added2', 'otherDocCh', 'changed1', 'discontinued'])

        when:
        def result = history.getDocGenFormat()

        then:
        result.last().issueType.first().type == 'documentation chapters'
        result.last().issueType.first().added == [[action:'add', key: 'added1', details:'numberOfAdded1 heading'], [action:'add', key: 'added2', details:'numberOfAdded2 heading']]
        result.last().issueType.first().changed == [[action:'change', key: 'changed1', details:'numberOfChanged heading', predecessors: 'somePredec']]
        result.last().issueType.first().discontinued == [[action:'discontinue', key: 'discontinued', details:'discontinuedNum heading']]
    }

    def "returns data for DocGen sorted"() {
        setup:
        def jiraData = jiraData11_first
        def targetEnvironment = 'D'
        def savedVersionId = 1L
        def savedData = entries10
        def versionEntries = entries11_first
        def docContent = computeIssuesDoc(versionEntries)

        DocumentHistory history = Spy(constructorArgs: [steps, logger, targetEnvironment, 'DocType'])

        when:
        history.load(jiraData, savedVersionId, docContent)
        def result = history.getDocGenFormat()

        then:
        1 * history.loadSavedDocHistoryData(_) >> savedData

        then:
        result.collect { it.entryId } == [1, 2]
    }

    def "returns data for DocGen with "() {
        setup:
        def jiraData = jiraData11_first
        def targetEnvironment = 'D'
        def savedVersionId = 1L
        def savedData = entries10
        def versionEntries = entries11_first
        def docContent = computeIssuesDoc(versionEntries)
        DocumentHistory history = Spy(constructorArgs: [steps, logger, targetEnvironment, 'DocType'])

        when:
        history.load(jiraData, savedVersionId, docContent)
        def result = history.getDocGenFormat()

        then:
        1 * history.loadSavedDocHistoryData(_) >> savedData

        then:
        result.collect { it.entryId } == [1, 2]
    }

    def "loads saved data as DocumentEntry"() {
        given:
        def targetEnvironment = 'D'
        def savedVersionId = 1L
        def savedProjVersion = 'version'
        def savedData = [[
                bugs                  : [],
                (Project.JiraDataItem.TYPE_DOCS): [],
                components            : [[key: "CMP", action: 'add']],
                epics                 : [[key: "EPC", action: 'add']],
                mitigations           : [[key: "MIT", action: 'add']],
                requirements          : [[key: "req1.key", action: 'add']],
                risks                 : [[key: "rsk1.key", action: 'add']],
                tests                 : [[key: "tst1.key", action: 'add']],
                techSpecs             : [[key: "ts1.key", action: 'add']],
                entryId: 1,
                projectVersion: savedProjVersion,
                previousProjectVersion: '',
                rational: "Modifications for project version '${savedProjVersion}'."
            ]]

        def expectedResult = savedData.collect{ Map entry -> new DocumentHistoryEntry(
            entry,
            entry.entryId,
            entry.projectVersion,
            entry.previousProjectVersion,
            entry.rational
        )}

        DocumentHistory history = Spy(constructorArgs: [steps, logger, targetEnvironment, 'DocType'])
        ProjectDataBitbucketRepository repo = Spy(constructorArgs: [steps])

        when:
        def result = history.loadSavedDocHistoryData(savedVersionId, repo)

        then:
        1 * repo.loadFile(_) >> savedData
        result.size() == expectedResult.size()
        result.getClass() == expectedResult.getClass()
        result == expectedResult
        result.first().getEntryId() == expectedResult.first().getEntryId()

        when:
        history.loadSavedDocHistoryData(savedVersionId, repo)

        then:
        1 * repo.loadFile(_) >> [wrong: "saved data"]

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("Unable to load saved document history for file")

        when:
        history.loadSavedDocHistoryData(savedVersionId, repo)

        then:
        1 * repo.loadFile(_) >> [[bugs: ["BUG-1"], projectVersion: 'version']]

        then:
        e = thrown(IllegalArgumentException)
        e.message.contains("Unable to load saved document history for file")
        e.message.contains('EntryId cannot be empty')

        when:
        history.loadSavedDocHistoryData(savedVersionId, repo)

        then:
        1 * repo.loadFile(_) >> [[bugs: ["BUG-1"], entryId: 12]]

        then:
        e = thrown(IllegalArgumentException)
        e.message.contains("Unable to load saved document history for file")
        e.message.contains('projectVersion cannot be empty')
    }

    def "builds docHistory with only added/removed things when redeploying same version with no changes"() {
        given:
        def jiraData = jiraData10
        def firstProjectVersion = '1.0'
        def targetEnvironment = 'D'
        def savedVersionId = 1L
        def savedData = entries10
        def docContent = computeIssuesDoc(entries10)
        def versionEntries = [new DocumentHistoryEntry(entries10.first().getDelegate(), 2L, firstProjectVersion, '',
            "Modifications for project version '${firstProjectVersion}'. This document version invalidates the changes done in document version '1'.")] + entries10
        DocumentHistory history = Spy(constructorArgs: [steps, logger, targetEnvironment, 'DocType'])

        when:
        history.load(jiraData, savedVersionId, docContent)

        then:
        1 * history.loadSavedDocHistoryData(_) >> savedData

        then:
        history.latestVersionId == 2L
        assert entryListIsEquals(history.data, versionEntries)
        history.data == versionEntries
    }

    def "filters issues to what we have in the document"() {
        given:
        def targetEnvironment = 'D'
        def savedVersionId = null

        def cmp = {  name, String version = null ->  [key: "Technology-${name}" as String, name: name, versions: [version]]}
        def req = {  name, String version = null ->  [key: "REQ-${name}" as String, description:name, versions: [version]]}
        def ts  = {  name, String version = null ->  [key: "TS-${name}"  as String, description:name, versions: [version]]}
        def rsk = {  name, String version = null ->  [key: "RSK-${name}" as String, description:name, versions: [version]]}
        def tst = {  name, String version = null ->  [key: "TST-${name}" as String, description:name, versions: [version]]}
        def mit = {  name, String version = null ->  [key: "MIT-${name}" as String, description:name, versions: [version]]}

        def firstProjectVersion = '1.0'

        def cmp1 = cmp('frontend', firstProjectVersion )
        def req1 = req('1', firstProjectVersion)
        def ts1 = ts('1', firstProjectVersion)
        def rsk1 = rsk('1', firstProjectVersion)
        def tst1 = tst('1', firstProjectVersion)
        def tst2 = tst('toDiscontinue', firstProjectVersion)
        def mit1 = mit('toChange', firstProjectVersion)

        def jiraData = [
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

        def result = [new DocumentHistoryEntry([
            bugs                  : [],
            (Project.JiraDataItem.TYPE_DOCS): [],
            components            : [],
            epics                 : [],
            mitigations           : [],
            requirements          : [[key: req1.key, action: 'add']],
            risks                 : [],
            tests                 : [[key: tst1.key, action: 'add']],
            techSpecs             : []], 1L, firstProjectVersion, '',
            "Initial document version.")]

        def compareItems = ['bugs', 'components', 'epics', 'mitigations', 'requirements', 'risks',
                            'tests', 'techSpecs', Project.JiraDataItem.TYPE_DOCS]
        def issuesToInclude = [req1.key, tst1.key]

        DocumentHistory history = Spy(constructorArgs: [steps, logger, targetEnvironment, 'DocType'])


        when:
        history.load(jiraData, savedVersionId, issuesToInclude)

        then:
        compareItems.each {
            history.data.first()[it] == result.first()[it]
        }
        history.data.first() == result.first()
    }


    def "handle filtered issues that might have no longer be in the document"() {
        given:
        def targetEnvironment = 'D'
        def savedVersionId = 1L

        def cmp = {  name, String version = null ->  [key: "Technology-${name}" as String, name: name, versions: [version]]}
        def req = {  name, String version = null ->  [key: "REQ-${name}" as String, description:name, versions: [version]]}
        def ts  = {  name, String version = null ->  [key: "TS-${name}"  as String, description:name, versions: [version]]}
        def rsk = {  name, String version = null ->  [key: "RSK-${name}" as String, description:name, versions: [version]]}
        def tst = {  name, String version = null ->  [key: "TST-${name}" as String, description:name, versions: [version]]}
        def mit = {  name, String version = null ->  [key: "MIT-${name}" as String, description:name, versions: [version]]}

        def firstProjectVersion = '1.0'
        def secondProjectVersion = '2.0'

        def cmp1 = cmp('frontend', firstProjectVersion )
        def req1 = req('1', firstProjectVersion)
        def ts1 = ts('1', firstProjectVersion)
        def rsk1 = rsk('1', firstProjectVersion)
        def tst1 = tst('1', firstProjectVersion)
        def tst2 = tst('2', firstProjectVersion)
        def mit1 = mit('1', firstProjectVersion)
        def rskDisc = rsk('disctontinued', firstProjectVersion)
        def req2 = req('2', secondProjectVersion)
        def tst1c = tst('1changed', secondProjectVersion) << [predecessors: [tst1.key]]

        def jiraData = [
            bugs        : [:],
            version     : secondProjectVersion,
            previousVersion: firstProjectVersion,
            components  : [(cmp1.key):cmp1],
            epics       : [:],
            mitigations : [(mit1.key):mit1],
            requirements: [(req1.key): req1,(req2.key): req2],
            risks       : [(rsk1.key): rsk1],
            tests       : [(tst1c.key): tst1c, (tst2.key): tst2],
            techSpecs   : [(ts1.key):ts1],
            docs        : [:],
            discontinuationsPerType : [risks:[rskDisc]]
        ]

        def savedData = [new DocumentHistoryEntry([
            bugs                  : [],
            (Project.JiraDataItem.TYPE_DOCS): [],
            components            : [],
            epics                 : [],
            mitigations           : [],
            requirements          : [[key: req1.key, action: 'add']],
            risks                 : [],
            tests                 : [[key: tst1.key, action: 'add']],
            techSpecs             : []], 1L, firstProjectVersion, '',
            "Initial document version.")]

        def result = new DocumentHistoryEntry([
            bugs        : [],
            (Project.JiraDataItem.TYPE_DOCS): [],
            components  : [],
            epics       : [],
            mitigations : [],
            requirements: [[key: req2.key, action: 'add']],
            risks       : [],
            tests       : [[key: tst1.key, action: 'discontinue']],
            techSpecs   : []], 2L, secondProjectVersion, firstProjectVersion ,
            "Modifications for project version '${secondProjectVersion}'.")

        def issuesToInclude = [req1.key, req2.key]

        DocumentHistory history = Spy(constructorArgs: [steps, logger, targetEnvironment, 'DocType'])
        history.loadSavedDocHistoryData(savedVersionId) >> savedData


        when:
        history.load(jiraData, savedVersionId, issuesToInclude)

        then:
        history.data.first().bugs == result.bugs
        history.data.first().components == result.components
        history.data.first().epics == result.epics
        history.data.first().mitigations == result.mitigations
        history.data.first().requirements == result.requirements
        history.data.first().risks == result.risks
        history.data.first().tests == result.tests
        history.data.first().techSpecs == result.techSpecs
        history.data.first()[Project.JiraDataItem.TYPE_DOCS] == result[Project.JiraDataItem.TYPE_DOCS]
    }

    Boolean entryIsEquals(DocumentHistoryEntry a, DocumentHistoryEntry b) {
        if (a.getEntryId() != b.getEntryId()) return false
        if (a.getProjectVersion() != b.getProjectVersion()) return false
        if (a.getPreviousProjectVersion() != b.getPreviousProjectVersion()) return false
        if (a.getRational() != b.getRational()) return false
        if (a.getDelegate() != b.getDelegate()) return false
        return a == b
    }

    Boolean entryListIsEquals(List<DocumentHistoryEntry> entriesA, List<DocumentHistoryEntry> entriesB) {
        if (entriesA.size() != entriesB.size()) return false
        def issuesBKeys = entriesB.collect{it.getEntryId()}
        def areEquals = entriesA.collect{ DocumentHistoryEntry issueA ->
            if (! issuesBKeys.contains(issueA.getEntryId())) return false
            def correspondentIssueB = entriesB.find{it.getEntryId() == issueA.getEntryId()}
            if (! entryIsEquals(issueA, correspondentIssueB)) {
                return false
            }
            return true
        }
        return ! areEquals.contains(false)
    }
}
