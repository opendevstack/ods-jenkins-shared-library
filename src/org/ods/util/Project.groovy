package org.ods.util

import groovy.json.JsonSlurperClassic

import java.nio.file.Paths

import org.apache.http.client.utils.URIBuilder
import org.yaml.snakeyaml.Yaml

class Project {

    class TestType {
        static final String ACCEPTANCE = "AcceptanceTests"
        static final String INSTALLATION = "InstallationTest"
        static final String INTEGRATION = "IntegrationTest"
        static final String UNIT = "UnitTest"
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
  "components": [
    {
      "name": "Technology-Comp-1",
      "description": "Technology component demo-technology-comp-1 stored at https://bitbucket-dev.biscrum.com/projects/PLTFMDEV/repos/demo-technology-comp-1/browse",
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
        "DEMO-72",
        "DEMO-71",
        "DEMO-38",
        "DEMO-37"
      ],
      "mitigations": [
        "DEMO-8",
        "DEMO-46",
        "DEMO-12",
        "DEMO-42"
      ]
    },
    {
      "name": "Technology-Comp-2",
      "description": "Technology component demo-technology-comp-2 stored at https://bitbucket-dev.biscrum.com/projects/PLTFMDEV/repos/demo-technology-comp-2/browse",
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
        "DEMO-72",
        "DEMO-71",
        "DEMO-38",
        "DEMO-37"
      ],
      "mitigations": [
        "DEMO-8",
        "DEMO-46",
        "DEMO-12",
        "DEMO-42"
      ]
    },
    {
      "name": "Technology-Comp-3",
      "description": "Technology component demo-technology-comp-3 stored at https://bitbucket-dev.biscrum.com/projects/PLTFMDEV/repos/demo-technology-comp-3/browse",
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
      "tests": [
        "DEMO-72",
        "DEMO-71",
        "DEMO-38",
        "DEMO-37"
      ],
      "mitigations": [
        "DEMO-8",
        "DEMO-46",
        "DEMO-12",
        "DEMO-42"
      ]
    }
  ],
  "epics": [
    {
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
    {
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
  ],
  "requirements": [
    {
      "name": "Req-1",
      "description": "Req-1 is described here...",
      "key": "DEMO-6",
      "version": "1.0",
      "status": "IN DESIGN",
      "gampTopic": "compatibility",
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
      "epic": "DEMO-5",
      "risks": [
        "DEMO-7",
        "DEMO-11"
      ],
      "tests": [
        "DEMO-38",
        "DEMO-37"
      ],
      "mitigations": [
        "DEMO-8",
        "DEMO-12"
      ],
      "techSpecs": [
        "DEMO-15",
        "DEMO-26"
      ]
    },
    {
      "name": "Req-2",
      "description": "Req-2 is described here...",
      "key": "DEMO-40",
      "version": "1.0",
      "status": "IN DESIGN",
      "gampTopic": "procedural constraints",
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
      "epic": "DEMO-39",
      "risks": [
        "DEMO-41",
        "DEMO-45"
      ],
      "tests": [
        "DEMO-72",
        "DEMO-71"
      ],
      "mitigations": [
        "DEMO-46",
        "DEMO-42"
      ],
      "techSpecs": [
        "DEMO-60",
        "DEMO-49"
      ]
    }
  ],
  "risks": [
    {
      "name": "Risk-1 on Req DEMO-6",
      "description": "Risk-1 on Req DEMO-6 is described here...",
      "key": "DEMO-7",
      "version": "1.0",
      "status": "IN DESIGN",
      "gxpRelevance": "Not relevant/ZERO",
      "probabilityOfOccurrence": "MEDIUM",
      "severityOfImpact": "Low",
      "probabilityOfDetection": "After Impact",
      "requirements": [
        "DEMO-6"
      ],
      "tests": [
        "DEMO-9",
        "DEMO-10"
      ]
    },
    {
      "name": "Risk-2 on Req DEMO-6",
      "description": "Risk-2 on Req DEMO-6 is described here...",
      "key": "DEMO-11",
      "version": "1.0",
      "status": "IN DESIGN",
      "gxpRelevance": "Relevant",
      "probabilityOfOccurrence": "LOW",
      "severityOfImpact": "Medium",
      "probabilityOfDetection": "After Impact",
      "requirements": [
        "DEMO-6"
      ],
      "tests": [
        "DEMO-14",
        "DEMO-13"
      ]
    },
    {
      "name": "Risk-1 on TechSpec DEMO-15",
      "description": "Risk-1 on TechSpec DEMO-15 is described here...",
      "key": "DEMO-16",
      "version": "1.0",
      "status": "IN DESIGN",
      "gxpRelevance": "Not relevant/EQUAL",
      "probabilityOfOccurrence": "MEDIUM",
      "severityOfImpact": "Low",
      "probabilityOfDetection": "Before Impact",
      "requirements": [
        "DEMO-6"
      ],
      "tests": [
        "DEMO-19",
        "DEMO-18"
      ]
    },
    {
      "name": "Risk-2 on TechSpec DEMO-15",
      "description": "Risk-2 on TechSpec DEMO-15 is described here...",
      "key": "DEMO-20",
      "version": "1.0",
      "status": "IN DESIGN",
      "gxpRelevance": "Not relevant/ZERO",
      "probabilityOfOccurrence": "LOW",
      "severityOfImpact": "Low",
      "probabilityOfDetection": "Before Impact",
      "requirements": [
        "DEMO-6"
      ],
      "tests": [
        "DEMO-23",
        "DEMO-22"
      ]
    },
    {
      "name": "Risk-1 on TechSpec DEMO-26",
      "description": "Risk-1 on TechSpec DEMO-26 is described here...",
      "key": "DEMO-27",
      "version": "1.0",
      "status": "IN DESIGN",
      "gxpRelevance": "Not relevant/EQUAL",
      "probabilityOfOccurrence": "MEDIUM",
      "severityOfImpact": "Medium",
      "probabilityOfDetection": "Immediate",
      "requirements": [
        "DEMO-6"
      ],
      "tests": [
        "DEMO-30",
        "DEMO-29"
      ]
    },
    {
      "name": "Risk-2 on TechSpec DEMO-26",
      "description": "Risk-2 on TechSpec DEMO-26 is described here...",
      "key": "DEMO-31",
      "version": "1.0",
      "status": "IN DESIGN",
      "gxpRelevance": "Not relevant/LESS",
      "probabilityOfOccurrence": "MEDIUM",
      "severityOfImpact": "Medium",
      "probabilityOfDetection": "After Impact",
      "requirements": [
        "DEMO-6"
      ],
      "tests": [
        "DEMO-34",
        "DEMO-33"
      ]
    },
    {
      "name": "Risk-1 on Req DEMO-40",
      "description": "Risk-1 on Req DEMO-40 is described here...",
      "key": "DEMO-41",
      "version": "1.0",
      "status": "IN DESIGN",
      "gxpRelevance": "Not relevant/EQUAL",
      "probabilityOfOccurrence": "MEDIUM",
      "severityOfImpact": "Medium",
      "probabilityOfDetection": "Immediate",
      "requirements": [
        "DEMO-40"
      ],
      "tests": [
        "DEMO-44",
        "DEMO-43"
      ]
    },
    {
      "name": "Risk-2 on Req DEMO-40",
      "description": "Risk-2 on Req DEMO-40 is described here...",
      "key": "DEMO-45",
      "version": "1.0",
      "status": "IN DESIGN",
      "gxpRelevance": "Relevant",
      "probabilityOfOccurrence": "LOW",
      "severityOfImpact": "High",
      "probabilityOfDetection": "Before Impact",
      "requirements": [
        "DEMO-40"
      ],
      "tests": [
        "DEMO-48",
        "DEMO-47"
      ]
    },
    {
      "name": "Risk-1 on TechSpec DEMO-49",
      "description": "Risk-1 on TechSpec DEMO-49 is described here...",
      "key": "DEMO-50",
      "version": "1.0",
      "status": "IN DESIGN",
      "gxpRelevance": "Not relevant/LESS",
      "probabilityOfOccurrence": "MEDIUM",
      "severityOfImpact": "High",
      "probabilityOfDetection": "Before Impact",
      "requirements": [
        "DEMO-40"
      ],
      "tests": [
        "DEMO-52",
        "DEMO-53"
      ]
    },
    {
      "name": "Risk-2 on TechSpec DEMO-49",
      "description": "Risk-2 on TechSpec DEMO-49 is described here...",
      "key": "DEMO-54",
      "version": "1.0",
      "status": "IN DESIGN",
      "gxpRelevance": "Not relevant/EQUAL",
      "probabilityOfOccurrence": "HIGH",
      "severityOfImpact": "High",
      "probabilityOfDetection": "After Impact",
      "requirements": [
        "DEMO-40"
      ],
      "tests": [
        "DEMO-57",
        "DEMO-56"
      ]
    },
    {
      "name": "Risk-1 on TechSpec DEMO-60",
      "description": "Risk-1 on TechSpec DEMO-60 is described here...",
      "key": "DEMO-61",
      "version": "1.0",
      "status": "IN DESIGN",
      "gxpRelevance": "Not relevant/LESS",
      "probabilityOfOccurrence": "HIGH",
      "severityOfImpact": "Low",
      "probabilityOfDetection": "After Impact",
      "requirements": [
        "DEMO-40"
      ],
      "tests": [
        "DEMO-63",
        "DEMO-64"
      ]
    },
    {
      "name": "Risk-2 on TechSpec DEMO-60",
      "description": "Risk-2 on TechSpec DEMO-60 is described here...",
      "key": "DEMO-65",
      "version": "1.0",
      "status": "IN DESIGN",
      "gxpRelevance": "Not relevant/LESS",
      "probabilityOfOccurrence": "LOW",
      "severityOfImpact": "Medium",
      "probabilityOfDetection": "Immediate",
      "requirements": [
        "DEMO-40"
      ],
      "tests": [
        "DEMO-68",
        "DEMO-67"
      ]
    }
  ],
  "tests": [
    {
      "name": "RiskTest-1 for DEMO-7",
      "description": "RiskTest-1 for DEMO-7 is described here...",
      "key": "DEMO-9",
      "version": "1.0",
      "status": "TO DO",
      "testLevel": "Integration",
      "executionType": "Automated"
    },
    {
      "name": "RiskTest-2 for DEMO-7",
      "description": "RiskTest-2 for DEMO-7 is described here...",
      "key": "DEMO-10",
      "version": "1.0",
      "status": "TO DO",
      "testLevel": "Installation",
      "executionType": "Automated"
    },
    {
      "name": "RiskTest-1 for DEMO-11",
      "description": "RiskTest-1 for DEMO-11 is described here...",
      "key": "DEMO-13",
      "version": "1.0",
      "status": "TO DO",
      "testLevel": "Installation",
      "executionType": "Automated"
    },
    {
      "name": "RiskTest-2 for DEMO-11",
      "description": "RiskTest-2 for DEMO-11 is described here...",
      "key": "DEMO-14",
      "version": "1.0",
      "status": "TO DO",
      "testLevel": "Integration",
      "executionType": "Automated"
    },
    {
      "name": "RiskTest-1 for DEMO-16",
      "description": "RiskTest-1 for DEMO-16 is described here...",
      "key": "DEMO-18",
      "version": "1.0",
      "status": "TO DO",
      "testLevel": "Acceptance",
      "executionType": "Manual"
    },
    {
      "name": "RiskTest-2 for DEMO-16",
      "description": "RiskTest-2 for DEMO-16 is described here...",
      "key": "DEMO-19",
      "version": "1.0",
      "status": "TO DO",
      "testLevel": "Unit",
      "executionType": "Manual"
    },
    {
      "name": "RiskTest-1 for DEMO-20",
      "description": "RiskTest-1 for DEMO-20 is described here...",
      "key": "DEMO-22",
      "version": "1.0",
      "status": "TO DO",
      "testLevel": "Integration",
      "executionType": "Automated"
    },
    {
      "name": "RiskTest-2 for DEMO-20",
      "description": "RiskTest-2 for DEMO-20 is described here...",
      "key": "DEMO-23",
      "version": "1.0",
      "status": "TO DO",
      "testLevel": "Integration",
      "executionType": "Manual"
    },
    {
      "name": "TechSpec_AccTest-1 for DEMO-15",
      "description": "TechSpec_AccTest-1 for DEMO-15 is described here...",
      "key": "DEMO-24",
      "version": "1.0",
      "status": "TO DO",
      "testLevel": "Integration",
      "executionType": "Manual",
      "components": [
        "DEMO-4",
        "DEMO-2",
        "DEMO-3"
      ],
      "requirements": [
        "DEMO-6"
      ]
    },
    {
      "name": "TechSpec_AccTest-2 for DEMO-15",
      "description": "TechSpec_AccTest-2 for DEMO-15 is described here...",
      "key": "DEMO-25",
      "version": "1.0",
      "status": "TO DO",
      "testLevel": "Installation",
      "executionType": "Automated",
      "components": [
        "DEMO-4",
        "DEMO-2",
        "DEMO-3"
      ],
      "requirements": [
        "DEMO-6"
      ]
    },
    {
      "name": "RiskTest-1 for DEMO-27",
      "description": "RiskTest-1 for DEMO-27 is described here...",
      "key": "DEMO-29",
      "version": "1.0",
      "status": "TO DO",
      "testLevel": "Unit",
      "executionType": "Manual"
    },
    {
      "name": "RiskTest-2 for DEMO-27",
      "description": "RiskTest-2 for DEMO-27 is described here...",
      "key": "DEMO-30",
      "version": "1.0",
      "status": "TO DO",
      "testLevel": "Acceptance",
      "executionType": "Manual"
    },
    {
      "name": "RiskTest-1 for DEMO-31",
      "description": "RiskTest-1 for DEMO-31 is described here...",
      "key": "DEMO-33",
      "version": "1.0",
      "status": "TO DO",
      "testLevel": "Unit",
      "executionType": "Automated"
    },
    {
      "name": "RiskTest-2 for DEMO-31",
      "description": "RiskTest-2 for DEMO-31 is described here...",
      "key": "DEMO-34",
      "version": "1.0",
      "status": "TO DO",
      "testLevel": "Installation",
      "executionType": "Manual"
    },
    {
      "name": "TechSpec_AccTest-1 for DEMO-26",
      "description": "TechSpec_AccTest-1 for DEMO-26 is described here...",
      "key": "DEMO-35",
      "version": "1.0",
      "status": "TO DO",
      "testLevel": "Unit",
      "executionType": "Automated",
      "components": [
        "DEMO-4",
        "DEMO-2",
        "DEMO-3"
      ],
      "requirements": [
        "DEMO-6"
      ]
    },
    {
      "name": "TechSpec_AccTest-2 for DEMO-26",
      "description": "TechSpec_AccTest-2 for DEMO-26 is described here...",
      "key": "DEMO-36",
      "version": "1.0",
      "status": "TO DO",
      "testLevel": "Acceptance",
      "executionType": "Manual",
      "components": [
        "DEMO-4",
        "DEMO-2",
        "DEMO-3"
      ],
      "requirements": [
        "DEMO-6"
      ]
    },
    {
      "name": "Req_AccTest-1 for DEMO-6",
      "description": "Req_AccTest-1 for DEMO-6 is described here...",
      "key": "DEMO-37",
      "version": "1.0",
      "status": "TO DO",
      "testLevel": "Integration",
      "executionType": "Automated",
      "components": [
        "DEMO-4",
        "DEMO-2",
        "DEMO-3"
      ],
      "requirements": [
        "DEMO-6"
      ]
    },
    {
      "name": "Req_AccTest-2 for DEMO-6",
      "description": "Req_AccTest-2 for DEMO-6 is described here...",
      "key": "DEMO-38",
      "version": "1.0",
      "status": "TO DO",
      "testLevel": "Unit",
      "executionType": "Automated",
      "components": [
        "DEMO-4",
        "DEMO-2",
        "DEMO-3"
      ],
      "requirements": [
        "DEMO-6"
      ]
    },
    {
      "name": "RiskTest-1 for DEMO-41",
      "description": "RiskTest-1 for DEMO-41 is described here...",
      "key": "DEMO-43",
      "version": "1.0",
      "status": "TO DO",
      "testLevel": "Integration",
      "executionType": "Automated"
    },
    {
      "name": "RiskTest-2 for DEMO-41",
      "description": "RiskTest-2 for DEMO-41 is described here...",
      "key": "DEMO-44",
      "version": "1.0",
      "status": "TO DO",
      "testLevel": "Acceptance",
      "executionType": "Manual"
    },
    {
      "name": "RiskTest-1 for DEMO-45",
      "description": "RiskTest-1 for DEMO-45 is described here...",
      "key": "DEMO-47",
      "version": "1.0",
      "status": "TO DO",
      "testLevel": "Installation",
      "executionType": "Manual"
    },
    {
      "name": "RiskTest-2 for DEMO-45",
      "description": "RiskTest-2 for DEMO-45 is described here...",
      "key": "DEMO-48",
      "version": "1.0",
      "status": "TO DO",
      "testLevel": "Acceptance",
      "executionType": "Automated"
    },
    {
      "name": "RiskTest-1 for DEMO-50",
      "description": "RiskTest-1 for DEMO-50 is described here...",
      "key": "DEMO-52",
      "version": "1.0",
      "status": "TO DO",
      "testLevel": "Installation",
      "executionType": "Automated"
    },
    {
      "name": "RiskTest-2 for DEMO-50",
      "description": "RiskTest-2 for DEMO-50 is described here...",
      "key": "DEMO-53",
      "version": "1.0",
      "status": "TO DO",
      "testLevel": "Unit",
      "executionType": "Automated"
    },
    {
      "name": "RiskTest-1 for DEMO-54",
      "description": "RiskTest-1 for DEMO-54 is described here...",
      "key": "DEMO-56",
      "version": "1.0",
      "status": "TO DO",
      "testLevel": "Integration",
      "executionType": "Automated"
    },
    {
      "name": "RiskTest-2 for DEMO-54",
      "description": "RiskTest-2 for DEMO-54 is described here...",
      "key": "DEMO-57",
      "version": "1.0",
      "status": "TO DO",
      "testLevel": "Installation",
      "executionType": "Manual"
    },
    {
      "name": "TechSpec_AccTest-1 for DEMO-49",
      "description": "TechSpec_AccTest-1 for DEMO-49 is described here...",
      "key": "DEMO-58",
      "version": "1.0",
      "status": "TO DO",
      "testLevel": "Acceptance",
      "executionType": "Automated",
      "components": [
        "DEMO-4",
        "DEMO-2",
        "DEMO-3"
      ],
      "requirements": [
        "DEMO-40"
      ]
    },
    {
      "name": "TechSpec_AccTest-2 for DEMO-49",
      "description": "TechSpec_AccTest-2 for DEMO-49 is described here...",
      "key": "DEMO-59",
      "version": "1.0",
      "status": "TO DO",
      "testLevel": "Acceptance",
      "executionType": "Automated",
      "components": [
        "DEMO-4",
        "DEMO-2",
        "DEMO-3"
      ],
      "requirements": [
        "DEMO-40"
      ]
    },
    {
      "name": "RiskTest-1 for DEMO-61",
      "description": "RiskTest-1 for DEMO-61 is described here...",
      "key": "DEMO-63",
      "version": "1.0",
      "status": "TO DO",
      "testLevel": "Acceptance",
      "executionType": "Manual"
    },
    {
      "name": "RiskTest-2 for DEMO-61",
      "description": "RiskTest-2 for DEMO-61 is described here...",
      "key": "DEMO-64",
      "version": "1.0",
      "status": "TO DO",
      "testLevel": "Installation",
      "executionType": "Automated"
    },
    {
      "name": "RiskTest-1 for DEMO-65",
      "description": "RiskTest-1 for DEMO-65 is described here...",
      "key": "DEMO-67",
      "version": "1.0",
      "status": "TO DO",
      "testLevel": "Unit",
      "executionType": "Automated"
    },
    {
      "name": "RiskTest-2 for DEMO-65",
      "description": "RiskTest-2 for DEMO-65 is described here...",
      "key": "DEMO-68",
      "version": "1.0",
      "status": "TO DO",
      "testLevel": "Unit",
      "executionType": "Manual"
    },
    {
      "name": "TechSpec_AccTest-1 for DEMO-60",
      "description": "TechSpec_AccTest-1 for DEMO-60 is described here...",
      "key": "DEMO-69",
      "version": "1.0",
      "status": "TO DO",
      "testLevel": "Acceptance",
      "executionType": "Manual",
      "components": [
        "DEMO-4",
        "DEMO-2",
        "DEMO-3"
      ],
      "requirements": [
        "DEMO-40"
      ]
    },
    {
      "name": "TechSpec_AccTest-2 for DEMO-60",
      "description": "TechSpec_AccTest-2 for DEMO-60 is described here...",
      "key": "DEMO-70",
      "version": "1.0",
      "status": "TO DO",
      "testLevel": "Unit",
      "executionType": "Automated",
      "components": [
        "DEMO-4",
        "DEMO-2",
        "DEMO-3"
      ],
      "requirements": [
        "DEMO-40"
      ]
    },
    {
      "name": "Req_AccTest-1 for DEMO-40",
      "description": "Req_AccTest-1 for DEMO-40 is described here...",
      "key": "DEMO-71",
      "version": "1.0",
      "status": "TO DO",
      "testLevel": "Acceptance",
      "executionType": "Automated",
      "components": [
        "DEMO-4",
        "DEMO-2",
        "DEMO-3"
      ],
      "requirements": [
        "DEMO-40"
      ]
    },
    {
      "name": "Req_AccTest-2 for DEMO-40",
      "description": "Req_AccTest-2 for DEMO-40 is described here...",
      "key": "DEMO-72",
      "version": "1.0",
      "status": "TO DO",
      "testLevel": "Installation",
      "executionType": "Automated",
      "components": [
        "DEMO-4",
        "DEMO-2",
        "DEMO-3"
      ],
      "requirements": [
        "DEMO-40"
      ]
    }
  ],
  "mitigations": [
    {
      "name": "Mitigation-1 for Risk-1 on Req DEMO-6",
      "description": "Mitigation-1 for Risk-1 on Req DEMO-6 is described here...",
      "key": "DEMO-8",
      "version": "1.0",
      "status": "TO DO"
    },
    {
      "name": "Mitigation-1 for Risk-2 on Req DEMO-6",
      "description": "Mitigation-1 for Risk-2 on Req DEMO-6 is described here...",
      "key": "DEMO-12",
      "version": "1.0",
      "status": "TO DO"
    },
    {
      "name": "Mitigation-1 for Risk-1 on TechSpec DEMO-15",
      "description": "Mitigation-1 for Risk-1 on TechSpec DEMO-15 is described here...",
      "key": "DEMO-17",
      "version": "1.0",
      "status": "TO DO"
    },
    {
      "name": "Mitigation-1 for Risk-2 on TechSpec DEMO-15",
      "description": "Mitigation-1 for Risk-2 on TechSpec DEMO-15 is described here...",
      "key": "DEMO-21",
      "version": "1.0",
      "status": "TO DO"
    },
    {
      "name": "Mitigation-1 for Risk-1 on TechSpec DEMO-26",
      "description": "Mitigation-1 for Risk-1 on TechSpec DEMO-26 is described here...",
      "key": "DEMO-28",
      "version": "1.0",
      "status": "TO DO"
    },
    {
      "name": "Mitigation-1 for Risk-2 on TechSpec DEMO-26",
      "description": "Mitigation-1 for Risk-2 on TechSpec DEMO-26 is described here...",
      "key": "DEMO-32",
      "version": "1.0",
      "status": "TO DO"
    },
    {
      "name": "Mitigation-1 for Risk-1 on Req DEMO-40",
      "description": "Mitigation-1 for Risk-1 on Req DEMO-40 is described here...",
      "key": "DEMO-42",
      "version": "1.0",
      "status": "TO DO"
    },
    {
      "name": "Mitigation-1 for Risk-2 on Req DEMO-40",
      "description": "Mitigation-1 for Risk-2 on Req DEMO-40 is described here...",
      "key": "DEMO-46",
      "version": "1.0",
      "status": "TO DO"
    },
    {
      "name": "Mitigation-1 for Risk-1 on TechSpec DEMO-49",
      "description": "Mitigation-1 for Risk-1 on TechSpec DEMO-49 is described here...",
      "key": "DEMO-51",
      "version": "1.0",
      "status": "TO DO"
    },
    {
      "name": "Mitigation-1 for Risk-2 on TechSpec DEMO-49",
      "description": "Mitigation-1 for Risk-2 on TechSpec DEMO-49 is described here...",
      "key": "DEMO-55",
      "version": "1.0",
      "status": "TO DO"
    },
    {
      "name": "Mitigation-1 for Risk-1 on TechSpec DEMO-60",
      "description": "Mitigation-1 for Risk-1 on TechSpec DEMO-60 is described here...",
      "key": "DEMO-62",
      "version": "1.0",
      "status": "TO DO"
    },
    {
      "name": "Mitigation-1 for Risk-2 on TechSpec DEMO-60",
      "description": "Mitigation-1 for Risk-2 on TechSpec DEMO-60 is described here...",
      "key": "DEMO-66",
      "version": "1.0",
      "status": "TO DO"
    }
  ],
  "techSpecs": [
    {
      "name": "TechSpec-1",
      "description": "TechSpec-1 is described here...",
      "key": "DEMO-15",
      "version": "1.0",
      "status": "IN DESIGN",
      "requirements": [
        "DEMO-6"
      ]
    },
    {
      "name": "TechSpec-2",
      "description": "TechSpec-2 is described here...",
      "key": "DEMO-26",
      "version": "1.0",
      "status": "IN DESIGN",
      "requirements": [
        "DEMO-6"
      ]
    },
    {
      "name": "TechSpec-1",
      "description": "TechSpec-1 is described here...",
      "key": "DEMO-49",
      "version": "1.0",
      "status": "IN DESIGN",
      "requirements": [
        "DEMO-40"
      ]
    },
    {
      "name": "TechSpec-2",
      "description": "TechSpec-2 is described here...",
      "key": "DEMO-60",
      "version": "1.0",
      "status": "IN DESIGN",
      "requirements": [
        "DEMO-40"
      ]
    }
  ]
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
        this.data.jira = this.loadJiraData(this.data.metadata.id)

        return this
    }

    List<Map> getAutomatedTests(String componentName = null, List<String> testTypes = []) {
        return this.data.jira.tests.findAll { testIssue ->
            def result = testIssue.status.toLowerCase() == "ready to test"

            if (result && componentName) {
                result = testIssue.components.collect{ it.toLowerCase() }.contains(componentName.toLowerCase()) 
            }

            if (result && testTypes) {
                result = testTypes.collect{ it.toLowerCase() }.contains(testIssue.testType.toLowerCase()) 
            }

            return result
        }
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

    List<Map> getComponents() {
        return this.data.jira.components
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

    String getId() {
        return this.data.jira.id 
    }

    String getKey() {
        return this.data.metadata.key
    }

    String getName() {
        return this.data.metadata.name
    }

    List<Map> getRepositories() {
        return this.data.metadata.repositories
    }

    Map getServices() {
        return this.data.metadata.services
    }

    List<Map> getSystemRequirements(String componentName = null, List<String> gampTopics = []) {
        return this.data.jira.requirements.findAll { req ->
            def result = true

            if (result && componentName) {
                result = req.components.collect{ it.toLowerCase() }.contains(componentName.toLowerCase())
            }

            if (result && gampTopics) {
                result = gampTopics.collect{ it.toLowerCase() }.contains(req.gampTopic.toLowerCase())
            }

            return result
        }
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
        return this.data.jira.techSpecs.findAll { techSpec ->
            def result = true

            if (result && componentName) {
                result = techSpec.components.collect{ it.toLowerCase() }.contains(componentName.toLowerCase())
            }

            return result
        }
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

    void setHasFailingTests(boolean status) {
        this.data.build.hasFailingTests = status
    }
}
