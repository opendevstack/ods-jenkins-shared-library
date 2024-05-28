package org.ods.orchestration.util

import groovy.json.JsonOutput
import org.ods.orchestration.service.leva.ProjectDataBitbucketRepository
import org.ods.util.IPipelineSteps
import org.ods.util.Logger
import org.ods.util.PipelineSteps
import spock.lang.Unroll
import util.SpecHelper

import java.nio.file.NoSuchFileException

class DocumentHistorySpec extends SpecHelper {

    IPipelineSteps steps
    Logger logger
    Map jiraData10
    Map noJiraData
    Map noJiraDataTwo
    Map jiraData11_first
    Map jiraDataFix
    Map jiraData11_second
    Map jiraData20
    Map jiraData20Alt

    List<DocumentHistoryEntry> entries10
    List<DocumentHistoryEntry> entries11_first
    List<DocumentHistoryEntry> entriesFix
    List<DocumentHistoryEntry> noEntries
    List<DocumentHistoryEntry> noEntriesOne
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
        def fifthProjectVersion = '3.0'

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

        this.noJiraData = [
            bugs        : [:],
            version     : fourthProjectVersion,
            previousVersion: secondProjectVersion,
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

        this.noJiraDataTwo = [
            bugs        : [:],
            version     : fifthProjectVersion,
            previousVersion: fourthProjectVersion,
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
            "1.0/1", "Initial document version.")]

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
            techSpecs   : []], 2L, secondProjectVersion, firstProjectVersion,
            "1.1/2", "Modifications for project version '${secondProjectVersion}'.")] + entries10

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
            "1.0.1/3", "Modifications for project version '${bugfixProjectVersion}'." +
                " This document version invalidates the previous document version '${bugfixProjectVersion}/2'.")] + entries11_first

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
            "1.1/4", "Modifications for project version '${secondProjectVersion}'.")] + entriesFix

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
            "2.0/5", "Modifications for project version '${fourthProjectVersion}'.")] + entries11_second

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
            "2.0/6", "Modifications for project version '${fourthProjectVersion}'. This document version invalidates the previous document version '${fourthProjectVersion}/5'.")] + entries20

        this.noEntries = [new DocumentHistoryEntry([
            bugs        : [],
            (Project.JiraDataItem.TYPE_DOCS): [],
            components  : [],
            epics       : [],
            mitigations : [],
            requirements: [],
            risks       : [],
            tests       : [],
            techSpecs   : []], 7L, fourthProjectVersion, secondProjectVersion,
            "2.0/7", "No changes were made to this document for project version '${fourthProjectVersion}'. This document version invalidates the previous document versions '${fourthProjectVersion}/6', '${fourthProjectVersion}/5'.")] + entries20Alt

        this.noEntriesOne = [new DocumentHistoryEntry([
            bugs        : [],
            (Project.JiraDataItem.TYPE_DOCS): [],
            components  : [],
            epics       : [],
            mitigations : [],
            requirements: [],
            risks       : [],
            tests       : [],
            techSpecs   : []], 8L, fifthProjectVersion, fourthProjectVersion,
            "3.0/8", "No changes were made to this document for project version '${fifthProjectVersion}'.")] + noEntries


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

        def versionEntries = entries10
        def docContent = computeIssuesDoc(versionEntries)
        DocumentHistory history = Spy(constructorArgs: [steps, logger, targetEnvironment, 'DocType'])

        when:
        history.load(jiraData, docContent)

        then:
        1 * history.loadSavedDocHistoryData() >> {
            throw new NoSuchFileException('projectData/documentHistory-D-DocType.json')
        }

        then:
        history.latestVersionId == 1L
        assert entryListIsEquals(history.data, versionEntries)
        history.data == versionEntries
    }

    def "builds docHistory for first second version"() {
        given:
        def jiraData = jiraData11_first
        def targetEnvironment = 'D'
        def savedData = entries10

        def versionEntries = entries11_first
        def docContent = computeIssuesDoc(versionEntries)
        DocumentHistory history = Spy(constructorArgs: [steps, logger, targetEnvironment, 'DocType'])

        when:
        history.load(jiraData, docContent)

        then:
        1 * history.loadSavedDocHistoryData() >> savedData

        then:
        history.latestVersionId == 2L
        assert entryListIsEquals(history.data, versionEntries)
        history.data == versionEntries
    }

    def "builds anomalous docHistory with a failed attempt to freeze a version"() {
        given:
        def jiraData = jiraData20Alt
        def targetEnvironment = 'D'
        def savedData = entries20

        def versionEntries = entries20Alt
        def docContent = computeIssuesDoc(versionEntries)
        DocumentHistory history = Spy(constructorArgs: [steps, logger, targetEnvironment, 'DocType'])

        when:
        history.load(jiraData, docContent)

        then:
        1 * history.loadSavedDocHistoryData() >> savedData

        then:
        history.latestVersionId == 6L
        assert entryListIsEquals(history.data, versionEntries)
        history.data == versionEntries
    }

    def "builds add rationale when there are concurrent versions"() {
        given:
        def jiraData = jiraDataFix
        def targetEnvironment = 'D'
        def savedData = entries11_first

        def versionEntries = entriesFix
        def docContent = computeIssuesDoc(versionEntries)
        DocumentHistory history = Spy(constructorArgs: [steps, logger, targetEnvironment, 'DocType'])

        when:
        history.load(jiraData, docContent)

        then:
        1 * history.loadSavedDocHistoryData() >> savedData

        then:
        history.latestVersionId == 3L
        assert entryListIsEquals(history.data, versionEntries)
        history.data == versionEntries
    }


    def "builds the reset from second version ontop of the bugfix"() {
        given:
        def jiraData = jiraData11_second
        def targetEnvironment = 'D'
        def savedData = entriesFix

        def versionEntries = entries11_second
        def docContent = computeIssuesDoc(versionEntries)
        DocumentHistory history = Spy(constructorArgs: [steps, logger, targetEnvironment, 'DocType'])

        when:
        history.load(jiraData, docContent)

        then:
        1 * history.loadSavedDocHistoryData() >> savedData

        then:
        history.latestVersionId == 4L
        assert entryListIsEquals(history.data, versionEntries)
        history.data == versionEntries
    }

    def "builds normal version without concurrent message"() {
        given:
        def jiraData = jiraData20
        def targetEnvironment = 'D'
        def savedData = entries11_second

        def versionEntries = entries20
        def docContent = computeIssuesDoc(versionEntries)
        DocumentHistory history = Spy(constructorArgs: [steps, logger, targetEnvironment, 'DocType'])

        when:
        history.load(jiraData, docContent)

        then:
        1 * history.loadSavedDocHistoryData() >> savedData

        then:
        history.latestVersionId == 5L
        assert entryListIsEquals(history.data, versionEntries)
        history.data == versionEntries
    }

    def "builds docHistory for no entries in current version but, existed previous version entries"() {
        given:
        def jiraData = noJiraData
        def targetEnvironment = 'D'
        def savedData = entries20Alt

        def versionEntries = noEntries
        def docContent = computeIssuesDoc(versionEntries)
        DocumentHistory history = Spy(constructorArgs: [steps, logger, targetEnvironment, 'DocType'])

        when:
        history.load(jiraData, docContent)

        then:
        1 * history.loadSavedDocHistoryData() >> savedData

        then:
        history.latestVersionId == 7L
        assert entryListIsEquals(history.data, versionEntries)
        history.data == versionEntries
    }

    def "builds docHistory for no entries in current version"() {
        given:
        def jiraData = noJiraDataTwo
        def targetEnvironment = 'D'
        def savedData = noEntries

        def versionEntries = noEntriesOne
        def docContent = computeIssuesDoc(versionEntries)
        DocumentHistory history = Spy(constructorArgs: [steps, logger, targetEnvironment, 'DocType'])

        when:
        history.load(jiraData, docContent)

        then:
        1 * history.loadSavedDocHistoryData() >> savedData

        then:
        history.latestVersionId == 8L
        assert entryListIsEquals(history.data, versionEntries)
        history.data == versionEntries
    }

    def "builds docHistory for all project versions"() {
        setup:
          def initialJiraData = jiraData10
          def initialEntries = entries10
          def firstVersionJiraData = jiraData11_first
          def firstVersionEntries = entries11_first
          def fixJiraData = jiraDataFix
          def fixVersionEntries = entriesFix
          def secondVersionJiraData = jiraData11_second
          def secondVersionEntries = entries11_second
          def noJiraData = noJiraData
          def savedDataForNoEntries = entries20Alt
          def noVersionEntries = noEntries
          def noJiraDataTwo = noJiraDataTwo
          def noVersionEntriesOne = noEntriesOne
          def targetEnvironment = 'D'
          DocumentHistory history = Spy(constructorArgs: [steps, logger, targetEnvironment, 'DocType'])
          def docContent

          /**
           * Initial document version.
           */
        when:
          docContent = computeIssuesDoc(initialEntries)
          history.load(initialJiraData, docContent)

        then:
          1 * history.loadSavedDocHistoryData() >> {
              throw new NoSuchFileException('projectData/documentHistory-D-DocType.json')
          }

        then:
          history.latestVersionId == 1L
          assert entryListIsEquals(history.data, initialEntries)
          history.data == initialEntries

          /**
           * Modifications for project version
           */
        when:
          docContent = computeIssuesDoc(firstVersionEntries)
          history.load(firstVersionJiraData, docContent)

        then:
          1 * history.loadSavedDocHistoryData() >> initialEntries

        then:
          history.latestVersionId == 2L
          assert entryListIsEquals(history.data, firstVersionEntries)
          history.data == firstVersionEntries

          /** Modifications for project version,
           * This document version invalidates the previous document version
           */
        when:
          docContent = computeIssuesDoc(fixVersionEntries)
          history.load(fixJiraData, docContent)

        then:
          1 * history.loadSavedDocHistoryData() >> firstVersionEntries

        then:
          history.latestVersionId == 3L
          assert entryListIsEquals(history.data, fixVersionEntries)
          history.data == fixVersionEntries

          /**
           * Modifications for project version.
           */
        when:
          docContent = computeIssuesDoc(secondVersionEntries)
          history.load(secondVersionJiraData, docContent)

        then:
          1 * history.loadSavedDocHistoryData() >> fixVersionEntries

        then:
          history.latestVersionId == 4L
          assert entryListIsEquals(history.data, secondVersionEntries)
          history.data == secondVersionEntries

          /** No changes were made to this document for project version,
           * This document version invalidates the previous document version
           */
        when:
          docContent = computeIssuesDoc(noVersionEntries)
          history.load(noJiraData, docContent)

        then:
          1 * history.loadSavedDocHistoryData() >> savedDataForNoEntries

        then:
          history.latestVersionId == 7L
          assert entryListIsEquals(history.data, noVersionEntries)
          history.data == noVersionEntries

          /**
           * No changes were made to this document for project version
           */
        when:
          docContent = computeIssuesDoc(noVersionEntriesOne)
          history.load(noJiraDataTwo, docContent)

        then:
          1 * history.loadSavedDocHistoryData() >> noVersionEntries

        then:
          history.latestVersionId == 8L
          assert entryListIsEquals(history.data, noVersionEntriesOne)
          history.data == noVersionEntriesOne
    }

    @Unroll
    def "builds docHistory for all project versions test"(String issueType, boolean changes) {
        given:
        DocumentHistory history = Spy(constructorArgs: [steps, logger, 'D', 'DocType'])
        def existingHistory = [new DocumentHistoryEntry([
            bugs        : [],
            'docs'      : [],
            components  : [],
            epics       : [],
            mitigations : [],
            requirements: [],
            risks       : [],
            tests       : [],
            techSpecs   : []], 1L, '1.0', '',
            "1.0/1", "Initial document version.")]
        history.loadSavedDocHistoryData() >> existingHistory

        def currentVersionData = [
            bugs        : [:],
            version     : '1.1',
            previousVersion: '1.0',
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
        if (issueType) {
            currentVersionData[issueType] = ['anyKey': [key: 'anyKey', versions: ['1.1']]]
        }

        def noChangesPrefix = 'No changes were made to this document for project version'
        def changesPrefix = 'Modifications for project version'
        def rationalPrefix = changes ? changesPrefix : noChangesPrefix

        when:
          history.load(currentVersionData, ['anyKey'])

        then:
        def entries = history.docHistoryEntries
        entries.last().getRational().startsWith(rationalPrefix)

        where:
          issueType      || changes
          null           || false
          'bugs'         || true
          'components'   || true
          'epics'        || true
          'mitigations'  || true
          'requirements' || true
          'risks'        || true
          'techSpecs'    || true
          'tests'        || true
          'docs'         || true

    }

    def "returns empty doc history and logs a warning if some issues do not have a version"() {
        setup:
        def targetEnvironment = 'D'
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
        history.loadSavedDocHistoryData() >> {
            throw new NoSuchFileException('projectData/documentHistory-D-DocType.json')
        }

        when: "We have a versioned component"
        history.load(base_saved_data + [components: [(issueV.key):issueV]], docContent)

        then:
        history.allIssuesAreValid

        when: "We have a non versioned component"
        history.load(base_saved_data + [components: [(issueNV.key):issueNV]], docContent)

        then:
        ! history.allIssuesAreValid

        when: "We have a versioned epic"
        history.load(base_saved_data + [epics: [(issueV.key):issueV]], docContent)

        then:
        history.allIssuesAreValid

        when: "We have a non versioned epic"
        history.load(base_saved_data + [epics: [(issueNV.key):issueNV]], docContent)

        then:
        ! history.allIssuesAreValid

        when: "We have a versioned risks"
        history.load(base_saved_data + [risks: [(issueV.key):issueV]], docContent)

        then:
        history.allIssuesAreValid

        when: "We have a non versioned risks"
        history.load(base_saved_data + [risks: [(issueNV.key):issueNV]], docContent)

        then:
        ! history.allIssuesAreValid

        when: "We have a versioned requirements"
        history.load(base_saved_data + [requirements: [(issueV.key):issueV]], docContent)

        then:
        history.allIssuesAreValid

        when: "We have a non versioned requirements"
        history.load(base_saved_data + [requirements: [(issueNV.key):issueNV]], docContent)

        then:
        ! history.allIssuesAreValid

        when: "We have a versioned tests"
        history.load(base_saved_data + [tests: [(issueV.key):issueV]], docContent)

        then:
        history.allIssuesAreValid

        when: "We have a non versioned tests"
        history.load(base_saved_data + [tests: [(issueNV.key):issueNV]], docContent)

        then:
        ! history.allIssuesAreValid

        when: "We have a versioned techSpecs"
        history.load(base_saved_data + [techSpecs: [(issueV.key):issueV]], docContent)

        then:
        history.allIssuesAreValid

        when: "We have a non versioned techSpecs"
        history.load(base_saved_data + [techSpecs: [(issueNV.key):issueNV]], docContent)

        then:
        ! history.allIssuesAreValid
    }

    def "issue type docs is shown as documentation chapter in the history and includes heading number and summary"() {
        setup:
        def targetEnvironment = 'D'

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
        history.loadSavedDocHistoryData() >> [new DocumentHistoryEntry([:], 1L, '1.0', '', null, 'Initial document version.')]
        history.load(base_saved_data, ['added1', 'added2', 'otherDocCh', 'changed1', 'discontinued'])

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
        def savedData = entries10
        def versionEntries = entries11_first
        def docContent = computeIssuesDoc(versionEntries)

        DocumentHistory history = Spy(constructorArgs: [steps, logger, targetEnvironment, 'DocType'])

        when:
        history.load(jiraData, docContent)
        def result = history.getDocGenFormat()

        then:
        1 * history.loadSavedDocHistoryData() >> savedData

        then:
        result.collect { it.entryId } == [1, 2]
    }

    def "returns data for DocGen with "() {
        setup:
        def jiraData = jiraData11_first
        def targetEnvironment = 'D'
        def savedData = entries10
        def versionEntries = entries11_first
        def docContent = computeIssuesDoc(versionEntries)
        DocumentHistory history = Spy(constructorArgs: [steps, logger, targetEnvironment, 'DocType'])

        when:
        history.load(jiraData, docContent)
        def result = history.getDocGenFormat()

        then:
        1 * history.loadSavedDocHistoryData() >> savedData

        then:
        result.collect { it.entryId } == [1, 2]
    }

    def "loads saved data as DocumentEntry"() {
        given:
        def targetEnvironment = 'D'
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
            entry.docVersion,
            entry.rational
        )}

        DocumentHistory history = Spy(constructorArgs: [steps, logger, targetEnvironment, 'DocType'])
        ProjectDataBitbucketRepository repo = Spy(constructorArgs: [steps])

        when:
        def result = history.loadSavedDocHistoryData(repo)

        then:
        1 * repo.loadFile(_) >> savedData
        result.size() == expectedResult.size()
        result.getClass() == expectedResult.getClass()
        result == expectedResult
        result.first().getEntryId() == expectedResult.first().getEntryId()

        when:
        history.loadSavedDocHistoryData(repo)

        then:
        1 * repo.loadFile(_) >> [wrong: "saved data"]

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("Unable to load saved document history for file")

        when:
        history.loadSavedDocHistoryData(repo)

        then:
        1 * repo.loadFile(_) >> [[bugs: ["BUG-1"], projectVersion: 'version']]

        then:
        e = thrown(IllegalArgumentException)
        e.message.contains("Unable to load saved document history for file")
        e.message.contains('EntryId cannot be empty')

        when:
        history.loadSavedDocHistoryData(repo)

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
        def savedData = entries10
        def docContent = computeIssuesDoc(entries10)
        def versionEntries = [new DocumentHistoryEntry(entries10.first().getDelegate(), 2L, firstProjectVersion, '',
            "1.0/2", "Modifications for project version '${firstProjectVersion}'. This document version invalidates the previous document version '${firstProjectVersion}/1'.")] + entries10
        DocumentHistory history = Spy(constructorArgs: [steps, logger, targetEnvironment, 'DocType'])

        when:
        history.load(jiraData, docContent)

        then:
        1 * history.loadSavedDocHistoryData() >> savedData

        then:
        history.latestVersionId == 2L
        assert entryListIsEquals(history.data, versionEntries)
        history.data == versionEntries
    }

    def "filters issues to what we have in the document"() {
        given:
        def targetEnvironment = 'D'

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
            null, "Initial document version.")]

        def compareItems = ['bugs', 'components', 'epics', 'mitigations', 'requirements', 'risks',
                            'tests', 'techSpecs', Project.JiraDataItem.TYPE_DOCS]
        def issuesToInclude = [req1.key, tst1.key]

        DocumentHistory history = Spy(constructorArgs: [steps, logger, targetEnvironment, 'DocType'])
        history.loadSavedDocHistoryData() >> {
            throw new NoSuchFileException('projectData/documentHistory-D-DocType.json')
        }

        when:
        history.load(jiraData, issuesToInclude)

        then:
        compareItems.each {
            history.data.first()[it] == result.first()[it]
        }
        history.data.first() == result.first()
    }


    def "handle filtered issues that might have no longer be in the document"() {
        given:
        def targetEnvironment = 'D'

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
            null, "Initial document version.")]

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
            null, "Modifications for project version '${secondProjectVersion}'.")

        def issuesToInclude = [req1.key, req2.key]

        DocumentHistory history = Spy(constructorArgs: [steps, logger, targetEnvironment, 'DocType'])
        history.loadSavedDocHistoryData() >> savedData


        when:
        history.load(jiraData, issuesToInclude)

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


    def "inherit history from D"() {
        given:
        def jiraData = jiraData11_first
        def targetEnvironment = 'Q'
        def savedData = entries10
        def savedJson = JsonOutput.toJson(savedData)

        def versionEntries = entries11_first
        def docContent = computeIssuesDoc(versionEntries)
        DocumentHistory history = Spy(constructorArgs: [steps, logger, targetEnvironment, 'TIR-component'])
        steps.readFile(file: 'projectData/documentHistory-D-TIR-component.json') >> savedJson

        when:
        history.load(jiraData, docContent)

        then:
        1 * history.loadSavedDocHistoryData()

        then:
        history.latestVersionId == 1L
        assert entryListIsEquals(history.data, savedData)
        history.data == savedData
    }

    @Unroll
    def "inherit history from Q"() {
        given:
        def jiraData = jiraData11_first
        def targetEnvironment = 'P'
        def savedData = entries10
        def savedJson = JsonOutput.toJson(savedData)

        def versionEntries = entries11_first
        def docContent = computeIssuesDoc(versionEntries)
        DocumentHistory history = Spy(constructorArgs: [steps, logger, targetEnvironment, docName])
        steps.readFile(file: "projectData/documentHistory-Q-${docName}.json") >> savedJson

        when:
        history.load(jiraData, docContent)

        then:
        1 * history.loadSavedDocHistoryData()

        then:
        history.latestVersionId == 1L
        assert entryListIsEquals(history.data, savedData)
        history.data == savedData

        where:
        docName << ['TIR-component', 'IVR']
    }

    Boolean entryIsEquals(DocumentHistoryEntry a, DocumentHistoryEntry b) {
        if (a.getEntryId() != b.getEntryId()) return false
        if (a.getProjectVersion() != b.getProjectVersion()) return false
        if (a.getPreviousProjectVersion() != b.getPreviousProjectVersion()) return false
        if (a.getDocVersion() != b.getDocVersion()) return false
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
