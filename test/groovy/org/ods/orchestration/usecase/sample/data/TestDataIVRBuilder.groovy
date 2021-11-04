package org.ods.orchestration.usecase.sample.data

import groovy.json.JsonSlurper
import util.FixtureHelper

import java.nio.file.Files
import java.nio.file.Paths

class TestDataIVRBuilder extends TestDataBuilder {

    static def doctype = "IVR"

    @Override
    def getDocType() {
        return doctype
    }

    @Override
    def createData(def projectKey) {
        def issueKeys = [projectKey + "137", projectKey + "140", projectKey + "139"]

        [
            tests: [
                installation: [
                    testReportFiles: getTestReportFiles(),
                    testResults    : [
                        testsuites: [
                            getInstallationTestSuite(issueKeys)
                        ]
                    ]
                ]
            ]
        ]
    }

    def getInstallationTestSuite(List<String> issueKeys) {
        def jsonInstallationTestSuite = '''
                        {
                           "hostname": "pod-5dd03da0-9bdb-485c-97dc-55a3bc4c483c-dg573-flht4",
                           "failures": "0",
                           "tests": "3",
                           "name": "DemoInstallationSpec",
                           "time": "0.016",
                           "errors": "1",
                           "timestamp": "2021-10-18T11:26:22",
                           "skipped": "0",
                           "properties": [

                           ],
                           "testcases": [
                               ''' + getTests(issueKeys) + '''
                           ],
                           "systemOut": "",
                           "systemErr": ""
                        }'''
        new JsonSlurper().parseText jsonInstallationTestSuite
    }

    def getTests(List<String> issueKeys) {
        def tests = '''''';
        issueKeys.each {key ->
            tests += ''',{
                "classname": "DemoInstallationSpec",
                "name": "''' + key +''' Installation",
                "time": "0.012",
                "skipped": false,
                "systemOut": "",
                "systemErr": "",
                "timestamp": "2021-10-18T11:26:22"
            }'''
        }
        tests.replaceFirst(",", "")
    }

    def getTestReportFiles() {
        def file = Files.createTempFile("junit", ".xml").toFile()
        def xmlFile = Paths.get(file.getPath().replace(file.getName(), "") + "junitResultsIVR.xml").toFile()
        xmlFile << "<?xml version='1.0' ?>\n" + FixtureHelper.createJUnitXMLTestResults()
        return [xmlFile]
    }
}
