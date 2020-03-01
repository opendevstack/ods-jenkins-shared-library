package org.ods.util

import com.cloudbees.groovy.cps.NonCPS

import groovy.json.JsonSlurperClassic

import java.nio.file.Paths

import org.apache.http.client.utils.URIBuilder
import org.yaml.snakeyaml.Yaml

class Project {

    class JiraDataItem extends HashMap {
        static final String TYPE_BUGS = "bugs"
        static final String TYPE_COMPONENTS = "components"
        static final String TYPE_EPICS = "epics"
        static final String TYPE_MITIGATIONS = "mitigations"
        static final String TYPE_REQUIREMENTS = "requirements"
        static final String TYPE_RISKS = "risks"
        static final String TYPE_TECHSPECS = "techSpecs"
        static final String TYPE_TESTS = "tests"

        static final List TYPES = [
            TYPE_BUGS,
            TYPE_COMPONENTS,
            TYPE_EPICS,
            TYPE_MITIGATIONS,
            TYPE_REQUIREMENTS,
            TYPE_RISKS,
            TYPE_TECHSPECS,
            TYPE_TESTS
        ]

        private final String type

        JiraDataItem(Map map, String type) {
            super(map)
            this.type = type
        }

        @NonCPS
        // FIXME: why can we not invoke derived methods in short form, e.g. .resolvedBugs?
        private List<Map> getResolvedReferences(String type) {
            def item = Project.this.data.jiraResolved[this.type][this.getAt("key")]
            return item[type] ?: []
        }

        List<Map> getResolvedBugs() {
            return this.getResolvedReferences("bugs")
        }

        List<Map> getResolvedComponents() {
            return this.getResolvedReferences("components")
        }

        List<Map> getResolvedEpics() {
            return this.getResolvedReferences("epics")
        }

        List<Map> getResolvedMitigations() {
            return this.getResolvedReferences("mitigations")
        }

        List<Map> getResolvedSystemRequirements() {
            return this.getResolvedReferences("requirements")
        }

        List<Map> getResolvedRisks() {
            return this.getResolvedReferences("risks")
        }

        List<Map> getResolvedTechnicalSpecifications() {
            return this.getResolvedReferences("techSpecs")
        }

        List<Map> getResolvedTests() {
            return this.getResolvedReferences("tests")
        }
    }

    class TestType {
        static final String ACCEPTANCE = "Acceptance"
        static final String INSTALLATION = "Installation"
        static final String INTEGRATION = "Integration"
        static final String UNIT = "Unit"
    }

    class GampTopic {
        static final String AVAILABILITY_REQUIREMENT = "Availability Requirement"
        static final String CONSTRAINT = "Constraint"
        static final String FUNCTIONAL_REQUIREMENT = "Functional Requirement"
        static final String INTERFACE_REQUIREMENT = "Interface Requirement"
    }

    protected static final String METADATA_FILE_NAME = "metadata.yml"

    private static final TEMP_FAKE_JIRA_DATA = """
{
  "project": {
      "name": "PLTFMDEV",
      "description": "Sample Project with 68 fictional Issues",
      "key": "PLTFMDEV",
      "id": 4711,
      "jiraBaseUrl": "http://localhost:2990/jira/DEMO"
  },
  "components": {
      "DEMO-2": {
          "name": "Technology-demo-app-front-end",
          "description": "Technology component demo-app-front-end stored at https://bitbucket-dev.biscrum.com/projects/PLTFMDEV/repos/pltfmdev-demo-app-front-end/browse",
          "key": "DEMO-2",
          "epics": [
              "DEMO-5",
              "DEMO-39"
          ],
          "requirements": [
              "DEMO-40",
              "DEMO-6"
          ],
          "techSpecs": [
              "DEMO-60",
              "DEMO-49",
              "DEMO-26"
          ],
          "tests": [
              "PLTFMDEV-549",
              "PLTFMDEV-550",
              "PLTFMDEV-551",
              "PLTFMDEV-552",
              "PLTFMDEV-553"
          ],
          "mitigations": [
              "DEMO-8",
              "DEMO-46",
              "DEMO-12",
              "DEMO-42"
          ]
      },
      "DEMO-3": {
          "name": "Technology-demo-app-carts",
          "description": "Technology component demo-app-carts stored at https://bitbucket-dev.biscrum.com/projects/PLTFMDEV/repos/pltfmdev-demo-app-carts/browse",
          "key": "DEMO-3",
          "epics": [
              "DEMO-5",
              "DEMO-39"
          ],
          "requirements": [
              "DEMO-40",
              "DEMO-6"
          ],
          "techSpecs": [
              "DEMO-49",
              "DEMO-15",
              "DEMO-26"
          ],
          "tests": [
              "PLTFMDEV-1045"
          ],
          "mitigations": [
              "DEMO-8",
              "DEMO-46",
              "DEMO-12",
              "DEMO-42"
          ]
      },
      "DEMO-4": {
          "name": "Technology-demo-app-catalogue",
          "description": "Technology component demo-app-catalogue stored at https://bitbucket-dev.biscrum.com/projects/PLTFMDEV/repos/pltfmdev-demo-app-catalogue/browse",
          "key": "DEMO-4",
          "epics": [
              "DEMO-5",
              "DEMO-39"
          ],
          "requirements": [
              "DEMO-40",
              "DEMO-6"
          ],
          "techSpecs": [
              "DEMO-60",
              "DEMO-15"
          ],
          "tests": [],
          "mitigations": [
              "DEMO-8",
              "DEMO-46",
              "DEMO-12",
              "DEMO-42"
          ]
      }
  },
  "epics": {
      "DEMO-5": {
          "name": "Epic-1",
          "description": "Epic-1 is described here...",
          "key": "DEMO-5",
          "version": "1.0",
          "status": "TO DO",
          "epicName": "Epic-1",
          "requirements": [
              "DEMO-6"
          ]
      },
      "DEMO-39": {
          "name": "Epic-2",
          "description": "Epic-2 is described here...",
          "key": "DEMO-39",
          "version": "1.0",
          "status": "TO DO",
          "epicName": "Epic-2",
          "requirements": [
              "DEMO-40"
          ]
      }
  },
  "requirements": {
      "DEMO-6": {
          "name": "Req-1",
          "description": "Req-1 is described here...",
          "key": "DEMO-6",
          "version": "1.0",
          "status": "IN DESIGN",
          "gampTopic": "performance requirements",
          "acceptanceCriteria": "acceptance of Req-1 only if ...",
          "configSpec": {
              "name": "Config.Spec for Req-1",
              "description": "Config.Spec for Req-1 description: ..."
          },
          "funcSpec": {
              "name": "Func.Spec for Req-1",
              "description": "Func.Spec for Req-1 description: ...",
              "acceptanceCriteria": "Func.Spec accepted only, if Req-1 works as described here."
          },
          "components": [
              "DEMO-2"
          ],
          "epics": [
              "DEMO-5"
          ],
          "risks": [
              "DEMO-7",
              "DEMO-11"
          ],
          "tests": [],
          "mitigations": [
              "DEMO-8",
              "DEMO-12"
          ],
          "techSpecs": [
              "DEMO-15",
              "DEMO-26"
          ]
      },
      "DEMO-40": {
          "name": "Req-2",
          "description": "Req-2 is described here...",
          "key": "DEMO-40",
          "version": "1.0",
          "status": "IN DESIGN",
          "gampTopic": "compatibility",
          "acceptanceCriteria": "acceptance of Req-2 only if ...",
          "configSpec": {
              "name": "Config.Spec for Req-2",
              "description": "Config.Spec for Req-2 description: ..."
          },
          "funcSpec": {
              "name": "Func.Spec for Req-2",
              "description": "Func.Spec for Req-2 description: ...",
              "acceptanceCriteria": "Func.Spec accepted only, if Req-2 works as described here."
          },
          "components": [
              "DEMO-4"
          ],
          "epics": [
              "DEMO-39"
          ],
          "risks": [
              "DEMO-41",
              "DEMO-45"
          ],
          "tests": [],
          "mitigations": [
              "DEMO-46",
              "DEMO-42"
          ],
          "techSpecs": [
              "DEMO-60",
              "DEMO-49"
          ]
      }
  },
  "risks": {
      "DEMO-7": {
          "name": "Risk-1 on Req DEMO-6",
          "description": "Risk-1 on Req DEMO-6 is described here...",
          "key": "DEMO-7",
          "version": "1.0",
          "status": "IN DESIGN",
          "gxpRelevance": "Not relevant/ZERO",
          "probabilityOfOccurrence": "MEDIUM",
          "severityOfImpact": "Medium",
          "probabilityOfDetection": "After Impact",
          "requirements": [
              "DEMO-6"
          ],
          "tests": []
      },
      "DEMO-11": {
          "name": "Risk-2 on Req DEMO-6",
          "description": "Risk-2 on Req DEMO-6 is described here...",
          "key": "DEMO-11",
          "version": "1.0",
          "status": "IN DESIGN",
          "gxpRelevance": "Not relevant/LESS",
          "probabilityOfOccurrence": "HIGH",
          "severityOfImpact": "High",
          "probabilityOfDetection": "Immediate",
          "requirements": [
              "DEMO-6"
          ],
          "tests": []
      },
      "DEMO-16": {
          "name": "Risk-1 on TechSpec DEMO-15",
          "description": "Risk-1 on TechSpec DEMO-15 is described here...",
          "key": "DEMO-16",
          "version": "1.0",
          "status": "IN DESIGN",
          "gxpRelevance": "Not relevant/EQUAL",
          "probabilityOfOccurrence": "MEDIUM",
          "severityOfImpact": "High",
          "probabilityOfDetection": "Immediate",
          "requirements": [
              "DEMO-6"
          ],
          "tests": []
      },
      "DEMO-20": {
          "name": "Risk-2 on TechSpec DEMO-15",
          "description": "Risk-2 on TechSpec DEMO-15 is described here...",
          "key": "DEMO-20",
          "version": "1.0",
          "status": "IN DESIGN",
          "gxpRelevance": "Relevant",
          "probabilityOfOccurrence": "HIGH",
          "severityOfImpact": "Low",
          "probabilityOfDetection": "Immediate",
          "requirements": [
              "DEMO-6"
          ],
          "tests": []
      },
      "DEMO-27": {
          "name": "Risk-1 on TechSpec DEMO-26",
          "description": "Risk-1 on TechSpec DEMO-26 is described here...",
          "key": "DEMO-27",
          "version": "1.0",
          "status": "IN DESIGN",
          "gxpRelevance": "Relevant",
          "probabilityOfOccurrence": "HIGH",
          "severityOfImpact": "Medium",
          "probabilityOfDetection": "After Impact",
          "requirements": [
              "DEMO-6"
          ],
          "tests": []
      },
      "DEMO-31": {
          "name": "Risk-2 on TechSpec DEMO-26",
          "description": "Risk-2 on TechSpec DEMO-26 is described here...",
          "key": "DEMO-31",
          "version": "1.0",
          "status": "IN DESIGN",
          "gxpRelevance": "Not relevant/LESS",
          "probabilityOfOccurrence": "HIGH",
          "severityOfImpact": "Medium",
          "probabilityOfDetection": "Immediate",
          "requirements": [
              "DEMO-6"
          ],
          "tests": []
      },
      "DEMO-41": {
          "name": "Risk-1 on Req DEMO-40",
          "description": "Risk-1 on Req DEMO-40 is described here...",
          "key": "DEMO-41",
          "version": "1.0",
          "status": "IN DESIGN",
          "gxpRelevance": "Not relevant/LESS",
          "probabilityOfOccurrence": "HIGH",
          "severityOfImpact": "Medium",
          "probabilityOfDetection": "Immediate",
          "requirements": [
              "DEMO-40"
          ],
          "tests": []
      },
      "DEMO-45": {
          "name": "Risk-2 on Req DEMO-40",
          "description": "Risk-2 on Req DEMO-40 is described here...",
          "key": "DEMO-45",
          "version": "1.0",
          "status": "IN DESIGN",
          "gxpRelevance": "Not relevant/EQUAL",
          "probabilityOfOccurrence": "HIGH",
          "severityOfImpact": "Low",
          "probabilityOfDetection": "After Impact",
          "requirements": [
              "DEMO-40"
          ],
          "tests": []
      },
      "DEMO-50": {
          "name": "Risk-1 on TechSpec DEMO-49",
          "description": "Risk-1 on TechSpec DEMO-49 is described here...",
          "key": "DEMO-50",
          "version": "1.0",
          "status": "IN DESIGN",
          "gxpRelevance": "Relevant",
          "probabilityOfOccurrence": "MEDIUM",
          "severityOfImpact": "Medium",
          "probabilityOfDetection": "Immediate",
          "requirements": [
              "DEMO-40"
          ],
          "tests": []
      },
      "DEMO-54": {
          "name": "Risk-2 on TechSpec DEMO-49",
          "description": "Risk-2 on TechSpec DEMO-49 is described here...",
          "key": "DEMO-54",
          "version": "1.0",
          "status": "IN DESIGN",
          "gxpRelevance": "Not relevant/EQUAL",
          "probabilityOfOccurrence": "MEDIUM",
          "severityOfImpact": "High",
          "probabilityOfDetection": "After Impact",
          "requirements": [
              "DEMO-40"
          ],
          "tests": []
      },
      "DEMO-61": {
          "name": "Risk-1 on TechSpec DEMO-60",
          "description": "Risk-1 on TechSpec DEMO-60 is described here...",
          "key": "DEMO-61",
          "version": "1.0",
          "status": "IN DESIGN",
          "gxpRelevance": "Not relevant/ZERO",
          "probabilityOfOccurrence": "HIGH",
          "severityOfImpact": "High",
          "probabilityOfDetection": "Immediate",
          "requirements": [
              "DEMO-40"
          ],
          "tests": []
      },
      "DEMO-65": {
          "name": "Risk-2 on TechSpec DEMO-60",
          "description": "Risk-2 on TechSpec DEMO-60 is described here...",
          "key": "DEMO-65",
          "version": "1.0",
          "status": "IN DESIGN",
          "gxpRelevance": "Not relevant/ZERO",
          "probabilityOfOccurrence": "MEDIUM",
          "severityOfImpact": "Low",
          "probabilityOfDetection": "Immediate",
          "requirements": [
              "DEMO-40"
          ],
          "tests": []
      }
  },
  "tests": {
      "PLTFMDEV-549": {
          "name": "User interacts with the cart",
          "description": "User interacts with the cart",
          "key": "PLTFMDEV-549",
          "version": "1.0",
          "status": "READY TO TEST",
          "testType": "Acceptance",
          "executionType": "Automated",
          "components": [
              "DEMO-2"
          ],
          "requirements": [
              "DEMO-6"
          ]
      },
      "PLTFMDEV-550": {
          "name": "User shows catalogue",
          "description": "User shows catalogue",
          "key": "PLTFMDEV-550",
          "version": "1.0",
          "status": "READY TO TEST",
          "testType": "Acceptance",
          "executionType": "Automated",
          "components": [
              "DEMO-2"
          ],
          "requirements": [
              "DEMO-6"
          ]
      },
      "PLTFMDEV-551": {
          "name": "User buys some socks",
          "description": "User buys some socks",
          "key": "PLTFMDEV-551",
          "version": "1.0",
          "status": "READY TO TEST",
          "testType": "Acceptance",
          "executionType": "Automated",
          "components": [
              "DEMO-2"
          ],
          "requirements": [
              "DEMO-6"
          ]
      },
      "PLTFMDEV-552": {
          "name": "Home page looks sexy",
          "description": "Home page looks sexy",
          "key": "PLTFMDEV-552",
          "version": "1.0",
          "status": "READY TO TEST",
          "testType": "Acceptance",
          "components": [
              "DEMO-2"
          ],
          "requirements": [
              "DEMO-6"
          ]
      },
      "PLTFMDEV-553": {
          "name": "User logs in",
          "description": "User logs in",
          "key": "PLTFMDEV-553",
          "version": "1.0",
          "status": "READY TO TEST",
          "testType": "Acceptance",
          "components": [
              "DEMO-2"
          ],
          "requirements": [
              "DEMO-6"
          ]
      },
      "PLTFMDEV-1045": {
          "name": "FirstResultOrDefault returns the default for an empty list",
          "description": "FirstResultOrDefault returns the default for an empty list",
          "key": "PLTFMDEV-1045",
          "version": "1.0",
          "status": "READY TO TEST",
          "testType": "Unit",
          "components": [
              "DEMO-3"
          ],
          "requirements": [
              "DEMO-6"
          ]
      }
  },
  "mitigations": {
      "DEMO-8": {
          "name": "Mitigation-1 for Risk-1 on Req DEMO-6",
          "description": "Mitigation-1 for Risk-1 on Req DEMO-6 is described here...",
          "key": "DEMO-8",
          "version": "1.0",
          "status": "TO DO",
          "components": [
              "DEMO-2"
          ],
          "requirements": [
              "DEMO-6"
          ],
          "risks": [
              "DEMO-7"
          ]
      },
      "DEMO-12": {
          "name": "Mitigation-1 for Risk-2 on Req DEMO-6",
          "description": "Mitigation-1 for Risk-2 on Req DEMO-6 is described here...",
          "key": "DEMO-12",
          "version": "1.0",
          "status": "TO DO",
          "components": [
              "DEMO-2"
          ],
          "requirements": [
              "DEMO-6"
          ],
          "risks": [
              "DEMO-11"
          ]
      },
      "DEMO-17": {
          "name": "Mitigation-1 for Risk-1 on TechSpec DEMO-15",
          "description": "Mitigation-1 for Risk-1 on TechSpec DEMO-15 is described here...",
          "key": "DEMO-17",
          "version": "1.0",
          "status": "TO DO",
          "components": [
              "DEMO-4",
              "DEMO-2",
              "DEMO-3"
          ],
          "requirements": [
              "DEMO-6"
          ],
          "risks": [
              "DEMO-16"
          ]
      },
      "DEMO-21": {
          "name": "Mitigation-1 for Risk-2 on TechSpec DEMO-15",
          "description": "Mitigation-1 for Risk-2 on TechSpec DEMO-15 is described here...",
          "key": "DEMO-21",
          "version": "1.0",
          "status": "TO DO",
          "components": [
              "DEMO-4",
              "DEMO-2",
              "DEMO-3"
          ],
          "requirements": [
              "DEMO-6"
          ],
          "risks": [
              "DEMO-20"
          ]
      },
      "DEMO-28": {
          "name": "Mitigation-1 for Risk-1 on TechSpec DEMO-26",
          "description": "Mitigation-1 for Risk-1 on TechSpec DEMO-26 is described here...",
          "key": "DEMO-28",
          "version": "1.0",
          "status": "TO DO",
          "components": [
              "DEMO-2",
              "DEMO-3"
          ],
          "requirements": [
              "DEMO-6"
          ],
          "risks": [
              "DEMO-27"
          ]
      },
      "DEMO-32": {
          "name": "Mitigation-1 for Risk-2 on TechSpec DEMO-26",
          "description": "Mitigation-1 for Risk-2 on TechSpec DEMO-26 is described here...",
          "key": "DEMO-32",
          "version": "1.0",
          "status": "TO DO",
          "components": [
              "DEMO-2",
              "DEMO-3"
          ],
          "requirements": [
              "DEMO-6"
          ],
          "risks": [
              "DEMO-31"
          ]
      },
      "DEMO-42": {
          "name": "Mitigation-1 for Risk-1 on Req DEMO-40",
          "description": "Mitigation-1 for Risk-1 on Req DEMO-40 is described here...",
          "key": "DEMO-42",
          "version": "1.0",
          "status": "TO DO",
          "components": [
              "DEMO-4"
          ],
          "requirements": [
              "DEMO-40"
          ],
          "risks": [
              "DEMO-41"
          ]
      },
      "DEMO-46": {
          "name": "Mitigation-1 for Risk-2 on Req DEMO-40",
          "description": "Mitigation-1 for Risk-2 on Req DEMO-40 is described here...",
          "key": "DEMO-46",
          "version": "1.0",
          "status": "TO DO",
          "components": [
              "DEMO-4"
          ],
          "requirements": [
              "DEMO-40"
          ],
          "risks": [
              "DEMO-45"
          ]
      },
      "DEMO-51": {
          "name": "Mitigation-1 for Risk-1 on TechSpec DEMO-49",
          "description": "Mitigation-1 for Risk-1 on TechSpec DEMO-49 is described here...",
          "key": "DEMO-51",
          "version": "1.0",
          "status": "TO DO",
          "components": [
              "DEMO-4",
              "DEMO-2",
              "DEMO-3"
          ],
          "requirements": [
              "DEMO-40"
          ],
          "risks": [
              "DEMO-50"
          ]
      },
      "DEMO-55": {
          "name": "Mitigation-1 for Risk-2 on TechSpec DEMO-49",
          "description": "Mitigation-1 for Risk-2 on TechSpec DEMO-49 is described here...",
          "key": "DEMO-55",
          "version": "1.0",
          "status": "TO DO",
          "components": [
              "DEMO-4",
              "DEMO-2",
              "DEMO-3"
          ],
          "requirements": [
              "DEMO-40"
          ],
          "risks": [
              "DEMO-54"
          ]
      },
      "DEMO-62": {
          "name": "Mitigation-1 for Risk-1 on TechSpec DEMO-60",
          "description": "Mitigation-1 for Risk-1 on TechSpec DEMO-60 is described here...",
          "key": "DEMO-62",
          "version": "1.0",
          "status": "TO DO",
          "components": [
              "DEMO-4",
              "DEMO-2"
          ],
          "requirements": [
              "DEMO-40"
          ],
          "risks": [
              "DEMO-61"
          ]
      },
      "DEMO-66": {
          "name": "Mitigation-1 for Risk-2 on TechSpec DEMO-60",
          "description": "Mitigation-1 for Risk-2 on TechSpec DEMO-60 is described here...",
          "key": "DEMO-66",
          "version": "1.0",
          "status": "TO DO",
          "components": [
              "DEMO-4",
              "DEMO-2"
          ],
          "requirements": [
              "DEMO-40"
          ],
          "risks": [
              "DEMO-65"
          ]
      }
  },
  "techSpecs": {
      "DEMO-15": {
          "name": "TechSpec-1",
          "description": "TechSpec-1 is described here...",
          "key": "DEMO-15",
          "version": "1.0",
          "status": "IN DESIGN",
          "components": [
              "DEMO-4",
              "DEMO-3"
          ],
          "requirements": [
              "DEMO-6"
          ],
          "risks": [
              "DEMO-16",
              "DEMO-20"
          ],
          "tests": []
      },
      "DEMO-26": {
          "name": "TechSpec-2",
          "description": "TechSpec-2 is described here...",
          "key": "DEMO-26",
          "version": "1.0",
          "status": "IN DESIGN",
          "components": [
              "DEMO-2",
              "DEMO-3"
          ],
          "requirements": [
              "DEMO-6"
          ],
          "risks": [
              "DEMO-27",
              "DEMO-31"
          ],
          "tests": []
      },
      "DEMO-49": {
          "name": "TechSpec-1",
          "description": "TechSpec-1 is described here...",
          "key": "DEMO-49",
          "version": "1.0",
          "status": "IN DESIGN",
          "components": [
              "DEMO-2",
              "DEMO-3"
          ],
          "requirements": [
              "DEMO-40"
          ],
          "risks": [
              "DEMO-50",
              "DEMO-54"
          ],
          "tests": []
      },
      "DEMO-60": {
          "name": "TechSpec-2",
          "description": "TechSpec-2 is described here...",
          "key": "DEMO-60",
          "version": "1.0",
          "status": "IN DESIGN",
          "components": [
              "DEMO-4",
              "DEMO-2"
          ],
          "requirements": [
              "DEMO-40"
          ],
          "risks": [
              "DEMO-61",
              "DEMO-65"
          ],
          "tests": []
      }
  },
  "bugs": {}
}"""

    protected IPipelineSteps steps
    protected GitUtil git

    protected Map data = [:]

    Project(IPipelineSteps steps, GitUtil git) {
        this.steps = steps
        this.git = git
    }

    Project load() {
        this.data.build = [:]
        this.data.build.hasFailingTests = false

        this.data.buildParams = loadBuildParams(steps)
        this.data.git = [ commit: git.getCommit(), url: git.getURL() ]
        this.data.metadata = this.loadMetadata(METADATA_FILE_NAME)
        this.data.jira = this.convertJiraDataToJiraDataItems(this.loadJiraData(this.data.metadata.id))
        this.data.jiraResolved = this.resolveJiraDataItemReferences(this.data.jira)

        return this
    }

    protected Map convertJiraDataToJiraDataItems(Map data) {
        JiraDataItem.TYPES.each { type ->
            if (data[type] == null) {
                throw new IllegalArgumentException("Error: Jira data does not include references to items of type '${type}'.")
            }

            data[type] = data[type].collectEntries { key, item ->
                return [key, new JiraDataItem(item, type)]
            }
        }        

        return data
    }

    List<Map> getAutomatedTests(String componentName = null, List<String> testTypes = []) {
        return this.data.jira.tests.findAll { key, testIssue ->
            def result = testIssue.status.toLowerCase() == "ready to test"

            if (result && componentName) {
                result = testIssue.getResolvedComponents().collect{ it.name.toLowerCase() }.contains(componentName.toLowerCase()) 
            }

            if (result && testTypes) {
                result = testTypes.collect{ it.toLowerCase() }.contains(testIssue.testType.toLowerCase()) 
            }

            return result
        }.values() as List
    }

    List<Map> getAutomatedTestsTypeAcceptance(String componentName = null) {
        return this.getAutomatedTests(componentName, [TestType.ACCEPTANCE])
    }

    List<Map> getAutomatedTestsTypeInstallation(String componentName = null) {
        return this.getAutomatedTests(componentName, [TestType.INSTALLATION])
    }

    List<Map> getAutomatedTestsTypeIntegration(String componentName = null) {
        return this.getAutomatedTests(componentName, [TestType.INTEGRATION])
    }

    List<Map> getAutomatedTestsTypeUnit(String componentName = null) {
        return this.getAutomatedTests(componentName, [TestType.UNIT])
    }

    Map getBuildParams() {
        return this.data.buildParams
    }

    static List<String> getBuildEnvironment(IPipelineSteps steps, boolean debug = false) {
        def params = loadBuildParams(steps)

        return [
            "DEBUG=${debug}",
            "MULTI_REPO_BUILD=true",
            "MULTI_REPO_ENV=${params.targetEnvironment}",
            "MULTI_REPO_ENV_TOKEN=${params.targetEnvironmentToken}",
            "RELEASE_PARAM_CHANGE_ID=${params.changeId}",
            "RELEASE_PARAM_CHANGE_DESC=${params.changeDescription}",
            "RELEASE_PARAM_CONFIG_ITEM=${params.configItem}",
            "RELEASE_PARAM_VERSION=${params.version}",
            "SOURCE_CLONE_ENV=${params.sourceEnvironmentToClone}",
            "SOURCE_CLONE_ENV_TOKEN=${params.sourceEnvironmentToCloneToken}"
        ]
    }

    List getCapabilities() {
        return this.data.metadata.capabilities
    }

    List<Map> getBugs() {
        return this.data.jira.bugs.values() as List
    }

    List<Map> getComponents() {
        return this.data.jira.components.values() as List
    }

    String getDescription() {
        return this.data.metadata.description
    }

    Map getGitData() {
        return this.data.git
    }

    protected URI getGitURLFromPath(String path, String remote = "origin") {
        if (!path?.trim()) {
            throw new IllegalArgumentException("Error: unable to get Git URL. 'path' is undefined.")
        }

        if (!path.startsWith(this.steps.env.WORKSPACE)) {
            throw new IllegalArgumentException("Error: unable to get Git URL. 'path' must be inside the Jenkins workspace: ${path}")
        }

        if (!remote?.trim()) {
            throw new IllegalArgumentException("Error: unable to get Git URL. 'remote' is undefined.")
        }

        def result = null

        this.steps.dir(path) {
            result = this.steps.sh(
                label : "Get Git URL for repository at path '${path}' and origin '${remote}'",
                script: "git config --get remote.${remote}.url",
                returnStdout: true
            ).trim()
        }

        return new URIBuilder(result).build()
    }

    List<Map> getEpics() {
        return this.data.jira.epics.values() as List
    }

    String getId() {
        return this.data.jira.id 
    }

    String getKey() {
        return this.data.metadata.id
    }

    List<Map> getMitigations() {
        return this.data.jira.mitigations.values() as List
    }

    String getName() {
        return this.data.metadata.name
    }

    List<Map> getRepositories() {
        return this.data.metadata.repositories
    }

    List<Map> getRisks() {
        return this.data.jira.risks.values() as List
    }

    Map getServices() {
        return this.data.metadata.services
    }

    List<Map> getSystemRequirements(String componentName = null, List<String> gampTopics = []) {
        return this.data.jira.requirements.findAll { key, req ->
            def result = true

            if (result && componentName) {
                result = req.getResolvedComponents().collect{ it.name.toLowerCase() }.contains(componentName.toLowerCase())
            }

            if (result && gampTopics) {
                result = gampTopics.collect{ it.toLowerCase() }.contains(req.gampTopic.toLowerCase())
            }

            return result
        }.values() as List
    }

    List<Map> getSystemRequirementsTypeAvailability(String componentName = null) {
        return this.getSystemRequirements(componentName, [GampTopic.AVAILABILITY_REQUIREMENT])
    }

    List<Map> getSystemRequirementsTypeConstraints(String componentName = null) {
        return this.getSystemRequirements(componentName, [GampTopic.CONSTRAINT])
    }

    List<Map> getSystemRequirementsTypeFunctional(String componentName = null) {
        return this.getSystemRequirements(componentName, [GampTopic.FUNCTIONAL_REQUIREMENT])
    }

    List<Map> getSystemRequirementsTypeInterfaces(String componentName = null) {
        return this.getSystemRequirements(componentName, [GampTopic.INTERFACE_REQUIREMENT])
    }

    List<Map> getTechnicalSpecifications(String componentName = null) {
        return this.data.jira.techSpecs.findAll { key, techSpec ->
            def result = true

            if (result && componentName) {
                result = techSpec.getResolvedComponents().collect{ it.name.toLowerCase() }.contains(componentName.toLowerCase())
            }

            return result
        }.values() as List
    }

    List<Map> getTests() {
        return this.data.jira.tests.values() as List
    }

    boolean hasFailingTests() {
        return this.data.build.hasFailingTests
    }

    static Map loadBuildParams(IPipelineSteps steps) {
        def version = steps.env.version?.trim() ?: "WIP"
        def targetEnvironment = steps.env.environment?.trim() ?: "dev"
        def targetEnvironmentToken = targetEnvironment[0].toUpperCase()
        def sourceEnvironmentToClone = steps.env.sourceEnvironmentToClone?.trim() ?: targetEnvironment
        def sourceEnvironmentToCloneToken = sourceEnvironmentToClone[0].toUpperCase()

        def changeId = steps.env.changeId?.trim() ?: "${version}-${targetEnvironment}"
        def configItem = steps.env.configItem?.trim() ?: "UNDEFINED"
        def changeDescription = steps.env.changeDescription?.trim() ?: "UNDEFINED"

        return [
            changeDescription: changeDescription,
            changeId: changeId,
            configItem: configItem,
            sourceEnvironmentToClone: sourceEnvironmentToClone,
            sourceEnvironmentToCloneToken: sourceEnvironmentToCloneToken,
            targetEnvironment: targetEnvironment,
            targetEnvironmentToken: targetEnvironmentToken,
            version: version
        ]
    }

    protected Map loadJiraData(String projectKey) {
        return new JsonSlurperClassic().parseText(TEMP_FAKE_JIRA_DATA)
    }

    protected Map loadMetadata(String filename = METADATA_FILE_NAME) {
        if (filename == null) {
            throw new IllegalArgumentException("Error: unable to parse project meta data. 'filename' is undefined.")
        }

        def file = Paths.get(this.steps.env.WORKSPACE, filename).toFile()
        if (!file.exists()) {
            throw new RuntimeException("Error: unable to load project meta data. File '${this.steps.env.WORKSPACE}/${filename}' does not exist.")
        }

        def result = new Yaml().load(file.text)

        // Check for existence of required attribute 'id'
        if (!result?.id?.trim()) {
            throw new IllegalArgumentException("Error: unable to parse project meta data. Required attribute 'id' is undefined.")
        }

        // Check for existence of required attribute 'name'
        if (!result?.name?.trim()) {
            throw new IllegalArgumentException("Error: unable to parse project meta data. Required attribute 'name' is undefined.")
        }

        if (result.description == null) {
            result.description = ""
        }

        if (result.repositories == null) {
            result.repositories = []
        }

        result.repositories.eachWithIndex { repo, index ->
            // Check for existence of required attribute 'repositories[i].id'
            if (!repo.id?.trim()) {
                throw new IllegalArgumentException("Error: unable to parse project meta data. Required attribute 'repositories[${index}].id' is undefined.")
            }

            repo.data = [:]
            repo.data.documents = [:]

            // Set repo type, if not provided
            if (!repo.type?.trim()) {
                repo.type = MROPipelineUtil.PipelineConfig.REPO_TYPE_ODS_CODE
            }

            // Resolve repo URL, if not provided
            if (!repo.url?.trim()) {
                this.steps.echo("Could not determine Git URL for repo '${repo.id}' from project meta data. Attempting to resolve automatically...")

                def gitURL = this.getGitURLFromPath(this.steps.env.WORKSPACE, "origin")
                if (repo.name?.trim()) {
                    repo.url = gitURL.resolve("${repo.name}.git").toString()
                    repo.remove("name")
                } else {
                    repo.url = gitURL.resolve("${result.id.toLowerCase()}-${repo.id}.git").toString()
                }

                this.steps.echo("Resolved Git URL for repo '${repo.id}' to '${repo.url}'")
            }

            // Resolve repo branch, if not provided
            if (!repo.branch?.trim()) {
                this.steps.echo("Could not determine Git branch for repo '${repo.id}' from project meta data. Assuming 'master'.")
                repo.branch = "master"
            }
        }

        if (result.capabilities == null) {
            result.capabilities = []
        }

        return result
    }

    protected Map resolveJiraDataItemReferences(Map data) {
        def result = [:]

        data.each { type, values ->
            if (type == "project") {
                return
            }

            result[type] = [:]

            values.each { key, item ->
                result[type][key] = [:]

                JiraDataItem.TYPES.each { referenceType ->
                    if (item.containsKey(referenceType)) {
                        result[type][key][referenceType] = []

                        item[referenceType].eachWithIndex { referenceKey, index ->
                            result[type][key][referenceType][index] = data[referenceType][referenceKey]
                        }
                    }
                }
            }
        }

        return result
    }

    void setHasFailingTests(boolean status) {
        this.data.build.hasFailingTests = status
    }

    String toString() {
        // Don't serialize resolved Jira data items
        def result = this.data.subMap(["build", "buildParams", "git", "jira", "metadata"])

        // Don't serialize temporarily stored document artefacts
        result.metadata.repositories.each { repo ->
            repo.data.documents = [:]
        }

        return result.toString()
    }
}
