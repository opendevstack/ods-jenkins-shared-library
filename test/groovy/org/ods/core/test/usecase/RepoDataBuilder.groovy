package org.ods.core.test.usecase

class RepoDataBuilder {
    static Map getRepoForComponent(component) {
        Map data = [
            tests: [
                unit: [

                ],
            ],
            openshift: [
                builds:                    [
                    "${component}": [
                        buildId: "${component}-3",
                        image:   "172.30.1.1:5000/ofi2004-cd/${component}@sha256:f6bc9aaed8a842a8e0a4f7e69b044a12c69e057333cd81906c08fd94be044ac4"
                    ]
                ],
                deployments:               [
                    "${component}": [
                        podName:                      "${component}-3-dshjl",
                        podNamespace:                 'ofi2004-dev',
                        podMetaDataCreationTimestamp: '2021-11-21T22:31:04Z',
                        deploymentId:                 "${component}-3",
                        podNode:                      'localhost', 'podIp': '172.17.0.39',
                        podStatus:                    'Running',
                        podStartupTimeStamp:          '2021-11-21T22:31:04Z',
                        containers:                   [
                            "${component}": "172.30.1.1:5000/ofi2004-cd/${component}@sha256:f6bc9aaed8a842a8e0a4f7e69b044a12c69e057333cd81906c08fd94be044ac4"
                        ]
                    ]
                ],
                sonarqubeScanStashPath:    "scrr-report-${component}-1",
                SCRR:                      "SCRR-ofi2004-${component}.docx",
                'SCRR-MD':                 "SCRR-ofi2004-${component}.md",
                testResultsFolder:         'build/test-results/test',
                testResults:               '1',
                xunitTestResultsStashPath: "test-reports-junit-xml-${component}-1",
                CREATED_BY_BUILD:          'WIP/1'
            ],
            documents: [:],
            git:       [
                branch:                  'master',
                commit:                  '46a05fce73c811e74f4f96d8f418daa4246ace09',
                previousCommit:          null,
                previousSucessfulCommit: null,
                url:                     "http://bitbucket.odsbox.lan:7990/scm/ofi2004/ofi2004-${component}.git",
                baseTag:                 '',
                targetTag:               ''
            ]
        ]
        Map repo = [
            id:             "${component}",
            type:           'ods',
            data:           data,
            url:            "http://bitbucket.odsbox.lan:7990/scm/ofi2004/ofi2004-${component}.git",
            branch:         'master',
            pipelineConfig: [
                dependencies: []
            ],
            metadata:       [
                name:        'OpenJDK',
                description: 'OpenJDK is a free and open-source implementation of the Java Platform, Standard Edition. Technologies: Spring Boot 2.1, OpenJDK 11, supplier:https://adoptopenjdk.net',
                version:     '3.x',
                type:        'ods'
            ]
        ]
        return repo
    }
}
