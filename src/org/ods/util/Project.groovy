package org.ods.util

import com.cloudbees.groovy.cps.NonCPS
import groovy.json.JsonSlurperClassic
import org.apache.http.client.utils.URIBuilder
import org.ods.service.JiraService
import org.ods.usecase.LeVADocumentUseCase
import org.yaml.snakeyaml.Yaml

import java.nio.file.Paths

class Project {

    class JiraDataItem implements Map, Serializable {
        static final String TYPE_BUGS = "bugs"
        static final String TYPE_COMPONENTS = "components"
        static final String TYPE_EPICS = "epics"
        static final String TYPE_MITIGATIONS = "mitigations"
        static final String TYPE_REQUIREMENTS = "requirements"
        static final String TYPE_RISKS = "risks"
        static final String TYPE_TECHSPECS = "techSpecs"
        static final String TYPE_TESTS = "tests"
        static final String TYPE_DOCS = "docs"

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
        private HashMap delegate

        JiraDataItem(Map map, String type) {
            this.delegate = new HashMap(map)
            this.type = type
        }

        @NonCPS
        @Override
        int size() {
            return delegate.size()
        }

        @NonCPS
        @Override
        boolean isEmpty() {
            return delegate.isEmpty()
        }

        @NonCPS
        @Override
        boolean containsKey(Object key) {
            return delegate.containsKey(key)
        }

        @NonCPS
        @Override
        boolean containsValue(Object value) {
            return delegate.containsValue(value)
        }

        @NonCPS
        @Override
        Object get(Object key) {
            return delegate.get(key)
        }

        @NonCPS
        @Override
        Object put(Object key, Object value) {
            return delegate.put(key, value)
        }

        @NonCPS
        @Override
        Object remove(Object key) {
            return delegate.remove(key)
        }

        @NonCPS
        @Override
        void putAll(Map m) {
            delegate.putAll(m)
        }

        @NonCPS
        @Override
        void clear() {
            delegate.clear()
        }

        @NonCPS
        @Override
        Set keySet() {
            return delegate.keySet()
        }

        @NonCPS
        @Override
        Collection values() {
            return delegate.values()
        }

        @NonCPS
        @Override
        Set<Entry> entrySet() {
            return delegate.entrySet()
        }

        @NonCPS
        String getType() {
            return type
        }

        @NonCPS
        Map getDelegate() {
            return delegate
        }

        @NonCPS
        JiraDataItem cloneIt() {
            def bos = new ByteArrayOutputStream()
            def os = new ObjectOutputStream(bos)
            os.writeObject(this.delegate)
            def ois = new ObjectInputStream(new ByteArrayInputStream(bos.toByteArray()))

            def newDelegate = ois.readObject()
            JiraDataItem result = new JiraDataItem(newDelegate, type)
            return result
        }

        @NonCPS
        // FIXME: why can we not invoke derived methods in short form, e.g. .resolvedBugs?
        private List<JiraDataItem> getResolvedReferences(String type) {
            // Reference this within jiraResolved (contains readily resolved references to other entities)
            def item = Project.this.data.jiraResolved[this.type][this.key]
            return item[type] ?: []
        }

        List<JiraDataItem> getResolvedBugs() {
            return this.getResolvedReferences("bugs")
        }

        List<JiraDataItem> getResolvedComponents() {
            return this.getResolvedReferences("components")
        }

        List<JiraDataItem> getResolvedEpics() {
            return this.getResolvedReferences("epics")
        }

        List<JiraDataItem> getResolvedMitigations() {
            return this.getResolvedReferences("mitigations")
        }

        List<JiraDataItem> getResolvedSystemRequirements() {
            return this.getResolvedReferences("requirements")
        }

        List<JiraDataItem> getResolvedRisks() {
            return this.getResolvedReferences("risks")
        }

        List<JiraDataItem> getResolvedTechnicalSpecifications() {
            return this.getResolvedReferences("techSpecs")
        }

        List<JiraDataItem> getResolvedTests() {
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
        "id": "12005",
        "jiraBaseUrl": "http://localhost:2990/jira/DEMO",
        "gampTopics": [
            "operational requirements",
            "functional requirements",
            "data requirements",
            "technical requirements",
            "interface requirements",
            "environment requirements",
            "performance requirements",
            "availability requirements",
            "security requirements",
            "maintenance requirements",
            "regulatory requirements",
            "roles",
            "compatibility",
            "procedural constraints",
            "overarching requirements"
        ],
        "projectProperties": {
            "PROJECT.POO_CAT.HIGH": "Frequency of the usage of the related function is >10 times per week.",
            "PROJECT.POO_CAT.LOW": "Frequency of the usage of the related function is <10 times per year.",
            "PROJECT.POO_CAT.MEDIUM": "Frequency of the usage of the related function is <10 times per week.",
            "PROJECT.USES_POO": "true"
        },
        "enumDictionary": {
            "ProbabilityOfDetection": {
                "1": {
                    "value": 1,
                    "text": "Immediate",
                    "short": "I"
                },
                "2": {
                    "value": 2,
                    "text": "Before Impact",
                    "short": "B"
                },
                "3": {
                    "value": 3,
                    "text": "After Impact",
                    "short": "A"
                }
            },
            "SeverityOfImpact": {
                "1": {
                    "value": 1,
                    "text": "Low",
                    "short": "L"
                },
                "2": {
                    "value": 2,
                    "text": "Medium",
                    "short": "M"
                },
                "3": {
                    "value": 3,
                    "text": "High",
                    "short": "H"
                }
            },
            "ProbabilityOfOccurrence": {
                "1": {
                    "value": 1,
                    "text": "LOW",
                    "short": "L"
                },
                "2": {
                    "value": 2,
                    "text": "MEDIUM",
                    "short": "M"
                },
                "3": {
                    "value": 3,
                    "text": "HIGH",
                    "short": "H"
                }
            },
            "RiskPriority": {
                "0": {
                    "value": 0,
                    "text": "N/A",
                    "short": "N"
                },
                "1": {
                    "value": 1,
                    "text": "HIGH",
                    "short": "H"
                },
                "2": {
                    "value": 2,
                    "text": "MEDIUM",
                    "short": "M"
                },
                "3": {
                    "value": 3,
                    "text": "LOW",
                    "short": "L"
                }
            },
            "GxPRelevance": {
                "R2": {
                    "value": 2,
                    "text": "Relevant",
                    "short": "R2"
                },
                "N0": {
                    "value": 0,
                    "text": "Not relevant/ZERO",
                    "short": "N0"
                },
                "N1": {
                    "value": 1,
                    "text": "Not relevant/LESS",
                    "short": "N1"
                },
                "N2": {
                    "value": 2,
                    "text": "Not relevant/EQUAL",
                    "short": "N2"
                }
            }
        }
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
                "DEMO-6",
                "DEMO-40"
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
                "PLTFMDEV-553",
                "PLTFMDEV-554",
                "PLTFMDEV-1046"
            ],
            "mitigations": [
                "DEMO-8",
                "DEMO-12",
                "DEMO-17",
                "DEMO-21",
                "DEMO-28",
                "DEMO-32",
                "DEMO-51",
                "DEMO-55",
                "DEMO-62",
                "DEMO-66"
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
                "PLTFMDEV-1045",
                "PLTFMDEV-401",
                "PLTFMDEV-1060",
                "PLTFMDEV-1061",
                "PLTFMDEV-1062",
                "PLTFMDEV-1073",
                "PLTFMDEV-1074",
                "PLTFMDEV-1075"
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
        "DEMO-41": {
            "name": "Risk-1 on Req DEMO-40",
            "description": "Risk-1 on Req DEMO-40 is described here...",
            "key": "DEMO-41",
            "version": "1.0",
            "status": "TO DO",
            "gxpRelevance": "N0",
            "probabilityOfOccurrence": 1,
            "severityOfImpact": 3,
            "probabilityOfDetection": 2,
            "riskPriorityNumber": 0,
            "riskPriority": 0,
            "mitigations": [
                "DEMO-42"
            ],
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
            "status": "TO DO",
            "gxpRelevance": "N0",
            "probabilityOfOccurrence": 3,
            "severityOfImpact": 2,
            "probabilityOfDetection": 2,
            "riskPriorityNumber": 0,
            "riskPriority": 0,
            "mitigations": [
                "DEMO-62"
            ],
            "techSpecs": [
                "DEMO-60"
            ],
            "tests": []
        },
        "DEMO-50": {
            "name": "Risk-1 on TechSpec DEMO-49",
            "description": "Risk-1 on TechSpec DEMO-49 is described here...",
            "key": "DEMO-50",
            "version": "1.0",
            "status": "TO DO",
            "gxpRelevance": "N1",
            "probabilityOfOccurrence": 2,
            "severityOfImpact": 3,
            "probabilityOfDetection": 2,
            "riskPriorityNumber": 12,
            "riskPriority": 2,
            "mitigations": [
                "DEMO-51"
            ],
            "techSpecs": [
                "DEMO-49"
            ],
            "tests": [
                "PLTFMDEV-401",
                "PLTFMDEV-1046"
            ]
        },
        "DEMO-7": {
            "name": "Risk-1 on Req DEMO-6",
            "description": "Risk-1 on Req DEMO-6 is described here...",
            "key": "DEMO-7",
            "version": "1.0",
            "status": "TO DO",
            "gxpRelevance": "N0",
            "probabilityOfOccurrence": 3,
            "severityOfImpact": 2,
            "probabilityOfDetection": 3,
            "riskPriorityNumber": 0,
            "riskPriority": 0,
            "mitigations": [
                "DEMO-8"
            ],
            "requirements": [
                "DEMO-6"
            ],
            "tests": [
                "PLTFMDEV-1045"
            ]
        },
        "DEMO-27": {
            "name": "Risk-1 on TechSpec DEMO-26",
            "description": "Risk-1 on TechSpec DEMO-26 is described here...",
            "key": "DEMO-27",
            "version": "1.0",
            "status": "TO DO",
            "gxpRelevance": "R2",
            "probabilityOfOccurrence": 2,
            "severityOfImpact": 2,
            "probabilityOfDetection": 2,
            "riskPriorityNumber": 16,
            "riskPriority": 2,
            "mitigations": [
                "DEMO-28"
            ],
            "techSpecs": [
                "DEMO-26"
            ],
            "tests": []
        },
        "DEMO-16": {
            "name": "Risk-1 on TechSpec DEMO-15",
            "description": "Risk-1 on TechSpec DEMO-15 is described here...",
            "key": "DEMO-16",
            "version": "1.0",
            "status": "TO DO",
            "gxpRelevance": "N1",
            "probabilityOfOccurrence": 3,
            "severityOfImpact": 1,
            "probabilityOfDetection": 3,
            "riskPriorityNumber": 9,
            "riskPriority": 2,
            "mitigations": [
                "DEMO-17"
            ],
            "techSpecs": [
                "DEMO-15"
            ],
            "tests": []
        },
        "DEMO-45": {
            "name": "Risk-2 on Req DEMO-40",
            "description": "Risk-2 on Req DEMO-40 is described here...",
            "key": "DEMO-45",
            "version": "1.0",
            "status": "TO DO",
            "gxpRelevance": "N0",
            "probabilityOfOccurrence": 3,
            "severityOfImpact": 3,
            "probabilityOfDetection": 1,
            "riskPriorityNumber": 0,
            "riskPriority": 0,
            "mitigations": [
                "DEMO-46"
            ],
            "requirements": [
                "DEMO-40"
            ],
            "tests": []
        },
        "DEMO-11": {
            "name": "Risk-2 on Req DEMO-6",
            "description": "Risk-2 on Req DEMO-6 is described here...",
            "key": "DEMO-11",
            "version": "1.0",
            "status": "TO DO",
            "gxpRelevance": "N0",
            "probabilityOfOccurrence": 3,
            "severityOfImpact": 3,
            "probabilityOfDetection": 3,
            "riskPriorityNumber": 0,
            "riskPriority": 0,
            "mitigations": [
                "DEMO-12"
            ],
            "requirements": [
                "DEMO-6"
            ],
            "tests": []
        },
        "DEMO-65": {
            "name": "Risk-2 on TechSpec DEMO-60",
            "description": "Risk-2 on TechSpec DEMO-60 is described here...",
            "key": "DEMO-65",
            "version": "1.0",
            "status": "TO DO",
            "gxpRelevance": "N1",
            "probabilityOfOccurrence": 2,
            "severityOfImpact": 3,
            "probabilityOfDetection": 3,
            "riskPriorityNumber": 18,
            "riskPriority": 1,
            "mitigations": [
                "DEMO-66"
            ],
            "techSpecs": [
                "DEMO-60"
            ],
            "tests": []
        },
        "DEMO-54": {
            "name": "Risk-2 on TechSpec DEMO-49",
            "description": "Risk-2 on TechSpec DEMO-49 is described here...",
            "key": "DEMO-54",
            "version": "1.0",
            "status": "TO DO",
            "gxpRelevance": "N2",
            "probabilityOfOccurrence": 1,
            "severityOfImpact": 3,
            "probabilityOfDetection": 3,
            "riskPriorityNumber": 18,
            "riskPriority": 1,
            "mitigations": [
                "DEMO-55"
            ],
            "techSpecs": [
                "DEMO-49"
            ],
            "tests": []
        },
        "DEMO-31": {
            "name": "Risk-2 on TechSpec DEMO-26",
            "description": "Risk-2 on TechSpec DEMO-26 is described here...",
            "key": "DEMO-31",
            "version": "1.0",
            "status": "TO DO",
            "gxpRelevance": "N2",
            "probabilityOfOccurrence": 3,
            "severityOfImpact": 3,
            "probabilityOfDetection": 2,
            "riskPriorityNumber": 36,
            "riskPriority": 1,
            "mitigations": [
                "DEMO-32"
            ],
            "techSpecs": [
                "DEMO-26"
            ],
            "tests": []
        },
        "DEMO-20": {
            "name": "Risk-2 on TechSpec DEMO-15",
            "description": "Risk-2 on TechSpec DEMO-15 is described here...",
            "key": "DEMO-20",
            "version": "1.0",
            "status": "TO DO",
            "gxpRelevance": "R2",
            "probabilityOfOccurrence": 2,
            "severityOfImpact": 2,
            "probabilityOfDetection": 2,
            "riskPriorityNumber": 16,
            "riskPriority": 2,
            "mitigations": [
                "DEMO-21"
            ],
            "techSpecs": [
                "DEMO-15"
            ],
            "tests": []
        }
    },
    "tests": {
        "PLTFMDEV-401": {
            "name": "verify database is correctly installed",
            "description": "verify database is correctly setup.",
            "key": "PLTFMDEV-401",
            "id": "24888",
            "version": "1.0",
            "status": "READY TO TEST",
            "testType": "Installation",
            "executionType": "Automated",
            "steps": [
                {
                    "index": 0,
                    "step": "Connect to database",
                    "data": "database credentials",
                    "expectedResult": "Connection to database is available and user is authenticated"
                },
                {
                    "index": 1,
                    "step": "List and verify databases",
                    "data": "database credentials; Sock Shop DB",
                    "expectedResult": "authenticated user sees all required databases"
                },
                {
                    "index": 2,
                    "step": "Use Sock Shop database",
                    "data": "SockShopDB",
                    "expectedResult": "Authenticated user can switch to Sock Shop DB and see tables"
                }
            ],
            "components": [
                "DEMO-3"
            ],
            "requirements": [
                "DEMO-6"
            ],
            "techSpecs": [
                "DEMO-15",
                "DEMO-26"
            ],
            "risks": [
                "DEMO-50"
            ],
            "bugs": []
        },
        "PLTFMDEV-549": {
            "name": "User interacts with the cart",
            "description": "User interacts with the cart",
            "key": "PLTFMDEV-549",
            "id": "26201",
            "version": "1.0",
            "status": "READY TO TEST",
            "testType": "Acceptance",
            "executionType": "Automated",
            "steps": [
                {
                    "index": 0,
                    "step": "User logs into web shop",
                    "data": "N/A",
                    "expectedResult": "Webshop Landing Page gets displayed"
                },
                {
                    "index": 1,
                    "step": "User adds item to shopping cart",
                    "data": "N/A",
                    "expectedResult": "One item added to shopping cart"
                },
                {
                    "index": 2,
                    "step": "User follows link to shopping cart",
                    "data": "N/A",
                    "expectedResult": "Shopping cart is displayed, containing one item."
                }
            ],
            "components": [
                "DEMO-2"
            ],
            "requirements": [
                "DEMO-6"
            ],
            "bugs": []
        },
        "PLTFMDEV-550": {
            "name": "User shows catalogue",
            "description": "User shows catalogue",
            "key": "PLTFMDEV-550",
            "id": "26202",
            "version": "1.0",
            "status": "READY TO TEST",
            "testType": "Acceptance",
            "executionType": "Automated",
            "steps": [
                {
                    "index": 0,
                    "step": "User logs into web shop",
                    "data": "N/A",
                    "expectedResult": "Webshop Landing Page gets displayed"
                },
                {
                    "index": 1,
                    "step": "User follows link to catalogue",
                    "data": "N/A",
                    "expectedResult": "Catalogue is displayed in web page."
                }
            ],
            "components": [
                "DEMO-2"
            ],
            "requirements": [
                "DEMO-6"
            ],
            "bugs": []
        },
        "PLTFMDEV-551": {
            "name": "User buys some socks",
            "description": "User buys some socks",
            "key": "PLTFMDEV-551",
            "id": "26203",
            "version": "1.0",
            "status": "READY TO TEST",
            "testType": "Acceptance",
            "executionType": "Automated",
            "steps": [
                {
                    "index": 0,
                    "step": "User logs into web shop",
                    "data": "N/A",
                    "expectedResult": "Webshop Landing Page gets displayed"
                },
                {
                    "index": 1,
                    "step": "User adds item to shopping cart",
                    "data": "N/A",
                    "expectedResult": "One item added to shopping cart"
                },
                {
                    "index": 2,
                    "step": "User follows link to shopping cart",
                    "data": "N/A",
                    "expectedResult": "Shopping cart is displayed, containing one item."
                },
                {
                    "index": 3,
                    "step": "User clicks 'buy now' button",
                    "data": "N/A",
                    "expectedResult": "Shipping details are displayed."
                }
            ],
            "components": [
                "DEMO-2"
            ],
            "requirements": [
                "DEMO-6"
            ],
            "bugs": []
        },
        "PLTFMDEV-552": {
            "name": "Home page looks sexy",
            "description": "Home page looks sexy",
            "key": "PLTFMDEV-552",
            "id": "26204",
            "version": "1.0",
            "status": "READY TO TEST",
            "testType": "Acceptance",
            "executionType": "Automated",
            "steps": [],
            "components": [
                "DEMO-2"
            ],
            "requirements": [
                "DEMO-6"
            ],
            "bugs": []
        },
        "PLTFMDEV-553": {
            "name": "User logs in",
            "description": "User logs in",
            "key": "PLTFMDEV-553",
            "id": "26205",
            "version": "1.0",
            "status": "READY TO TEST",
            "testType": "Acceptance",
            "executionType": "Automated",
            "steps": [],
            "components": [
                "DEMO-2"
            ],
            "requirements": [
                "DEMO-6"
            ],
            "bugs": []
        },
        "PLTFMDEV-554": {
            "name": "User exists in system",
            "description": "User exists in system",
            "key": "PLTFMDEV-554",
            "id": "26206",
            "version": "1.0",
            "status": "READY TO TEST",
            "testType": "Integration",
            "executionType": "Automated",
            "steps": [],
            "components": [
                "DEMO-2"
            ],
            "requirements": [
                "DEMO-6"
            ],
            "techSpecs": [
                "DEMO-15",
                "DEMO-26"
            ],
            "bugs": []
        },
        "PLTFMDEV-1045": {
            "name": "FirstResultOrDefault returns the default for an empty list",
            "description": "FirstResultOrDefault returns the default for an empty list",
            "key": "PLTFMDEV-1045",
            "id": "26800",
            "version": "1.0",
            "status": "READY TO TEST",
            "testType": "Unit",
            "executionType": "Automated",
            "steps": [],
            "components": [
                "DEMO-3"
            ],
            "requirements": [
                "DEMO-6"
            ],
            "techSpecs": [
                "DEMO-15"
            ],
            "risks": [
                "DEMO-7"
            ],
            "bugs": []
        },
        "PLTFMDEV-1046": {
            "name": "verify frontend is correctly installed",
            "description": "verify frontend is correctly installed.",
            "key": "PLTFMDEV-1046",
            "id": "26999",
            "version": "1.0",
            "status": "READY TO TEST",
            "testType": "Installation",
            "executionType": "Automated",
            "steps": [
                {
                    "index": 0,
                    "step": "Connect to the service on :80/health via HTTP",
                    "data": "N/A",
                    "expectedResult": "Connection to the service is established and the service returns 'OK'"
                }
            ],
            "components": [
                "DEMO-2"
            ],
            "requirements": [
                "DEMO-6"
            ],
            "techSpecs": [
                "DEMO-15",
                "DEMO-26"
            ],
            "risks": [
                "DEMO-50"
            ],
            "bugs": []
        },
        "PLTFMDEV-1060": {
            "name": "verify payment service is correctly installed",
            "description": "verify payment service is correctly setup.",
            "key": "PLTFMDEV-1060",
            "id": "27041",
            "version": "1.0",
            "status": "READY TO TEST",
            "testType": "Installation",
            "executionType": "Automated",
            "steps": [
                {
                    "index": 0,
                    "step": "Connect to the service on :80/health via HTTP",
                    "data": "N/A",
                    "expectedResult": "Connection to the service is established and the service returns 'OK'"
                }
            ],
            "components": [
                "DEMO-3"
            ],
            "requirements": [
                "DEMO-6"
            ],
            "techSpecs": [
                "DEMO-15",
                "DEMO-26"
            ],
            "bugs": []
        },
        "PLTFMDEV-1061": {
            "name": "verify order service is correctly installed",
            "description": "verify order service is correctly installed.",
            "key": "PLTFMDEV-1061",
            "id": "27042",
            "version": "1.0",
            "status": "READY TO TEST",
            "testType": "Installation",
            "executionType": "Automated",
            "steps": [
                {
                    "index": 0,
                    "step": "Connect to the service on :80/health via HTTP",
                    "data": "N/A",
                    "expectedResult": "Connection to the service is established and the service returns 'OK'"
                }
            ],
            "components": [
                "DEMO-3"
            ],
            "requirements": [
                "DEMO-6"
            ],
            "techSpecs": [
                "DEMO-15",
                "DEMO-26"
            ],
            "bugs": []
        },
        "PLTFMDEV-1062": {
            "name": "verify shipping service is correctly installed",
            "description": "verify shipping service is correctly installed.",
            "key": "PLTFMDEV-1062",
            "id": "27043",
            "version": "1.0",
            "status": "READY TO TEST",
            "testType": "Installation",
            "executionType": "Automated",
            "steps": [
                {
                    "index": 0,
                    "step": "Connect to the service on :80/health via HTTP",
                    "data": "N/A",
                    "expectedResult": "Connection to the service is established and the service returns 'OK'"
                }
            ],
            "components": [
                "DEMO-3"
            ],
            "requirements": [
                "DEMO-6"
            ],
            "techSpecs": [
                "DEMO-15",
                "DEMO-26"
            ],
            "bugs": []
        },
        "PLTFMDEV-1073": {
            "name": "Cart gets processed correctly",
            "description": "Cart gets processed correctly.",
            "key": "PLTFMDEV-1073",
            "id": "27105",
            "version": "1.0",
            "status": "READY TO TEST",
            "testType": "Integration",
            "executionType": "Automated",
            "steps": [
                {
                    "index": 0,
                    "step": "Connect to the service on :80/carts via HTTP",
                    "data": "N/A",
                    "expectedResult": "Connection to the service is established and the service returns correct cart data"
                }
            ],
            "components": [
                "DEMO-3"
            ],
            "requirements": [
                "DEMO-6"
            ],
            "techSpecs": [
                "DEMO-15",
                "DEMO-26"
            ],
            "bugs": []
        },
        "PLTFMDEV-1074": {
            "name": "Frontend retrieves cart data correctly",
            "description": "Frontend retrieves cart data correctly.",
            "key": "PLTFMDEV-1074",
            "id": "27106",
            "version": "1.0",
            "status": "READY TO TEST",
            "testType": "Integration",
            "executionType": "Automated",
            "steps": [
                {
                    "index": 0,
                    "step": "Connect to the service on :80/carts via HTTP",
                    "data": "N/A",
                    "expectedResult": "Connection to the service is established and the service returns correct cart data"
                }
            ],
            "components": [
                "DEMO-3"
            ],
            "requirements": [
                "DEMO-6"
            ],
            "techSpecs": [
                "DEMO-15",
                "DEMO-26"
            ],
            "bugs": []
        },
        "PLTFMDEV-1075": {
            "name": "Frontend retrieves payment data correctly",
            "description": "Frontend retrieves payment data correctly.",
            "key": "PLTFMDEV-1075",
            "id": "27107",
            "version": "1.0",
            "status": "READY TO TEST",
            "testType": "Integration",
            "executionType": "Automated",
            "steps": [
                {
                    "index": 0,
                    "step": "Connect to the service on :80/payment via HTTP",
                    "data": "N/A",
                    "expectedResult": "Connection to the service is established and the service returns correct payment data"
                }
            ],
            "components": [
                "DEMO-3"
            ],
            "requirements": [
                "DEMO-6"
            ],
            "techSpecs": [
                "DEMO-15",
                "DEMO-26"
            ],
            "bugs": []
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
            "systemDesignSpec": "Some system design specification.",
            "softwareDesignSpec": "Some software design specification.",
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
            "tests": [
                "PLTFMDEV-1045"
            ]
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
    "bugs": {
        "PLTFMDEV-658": {
            "key": "PLTFMDEV-658",
            "name": "org.spockframework.runtime. ConditionFailedWithExceptionError",
            "assignee": "Unassigned",
            "dueDate": "",
            "status": "TO DO",
            "tests": [
                "PLTFMDEV-551"
            ]
        },
        "PLTFMDEV-674": {
            "key": "PLTFMDEV-674",
            "name": "org.spockframework.runtime. ConditionFailedWithExceptionError",
            "assignee": "Unassigned",
            "dueDate": "",
            "status": "TO DO",
            "tests": [
                "PLTFMDEV-551"
            ]
        },
        "PLTFMDEV-690": {
            "key": "PLTFMDEV-690",
            "name": "org.spockframework.runtime. ConditionFailedWithExceptionError",
            "assignee": "Unassigned",
            "dueDate": "",
            "status": "TO DO",
            "tests": [
                "PLTFMDEV-551"
            ]
        },
        "PLTFMDEV-10658": {
            "key": "PLTFMDEV-10658",
            "name": "One org.spockframework.runtime. ConditionFailedWithExceptionError",
            "assignee": "Unassigned",
            "dueDate": "",
            "status": "TO DO",
            "tests": [
                "PLTFMDEV-554"
            ]
        },
        "PLTFMDEV-10674": {
            "key": "PLTFMDEV-10674",
            "name": "Two org.spockframework.runtime. ConditionFailedWithExceptionError",
            "assignee": "Unassigned",
            "dueDate": "",
            "status": "TO DO",
            "tests": [
                "PLTFMDEV-554"
            ]
        },
        "PLTFMDEV-10690": {
            "key": "PLTFMDEV-10690",
            "name": "Three org.spockframework.runtime. ConditionFailedWithExceptionError",
            "assignee": "Unassigned",
            "dueDate": "",
            "status": "TO DO",
            "tests": [
                "PLTFMDEV-554"
            ]
        }
    }
}
"""

    protected IPipelineSteps steps
    protected GitUtil git
    protected JiraService jira

    protected Map data = [:]

    Project(IPipelineSteps steps) {
        this.steps = steps

        this.data.build = [
            hasFailingTests       : false,
            hasUnexecutedJiraTests: false
        ]
    }

    Project init() {
        this.data.buildParams = this.loadBuildParams(steps)
        this.data.metadata = this.loadMetadata(METADATA_FILE_NAME)
        return this
    }

    Project load(GitUtil git, JiraService jira) {
        this.git = git
        this.jira = jira

        this.data.git = [commit: git.getCommit(), url: git.getURL()]
        this.data.jira = this.cleanJiraDataItems(this.convertJiraDataToJiraDataItems(this.loadJiraData(this.data.metadata.id)))
        this.data.jira.project.version = this.loadJiraDataProjectVersion()
        this.data.jiraResolved = this.resolveJiraDataItemReferences(this.data.jira)
        this.data.jira.docs = this.loadJiraDataDocs()
        return this
    }

    protected Map cleanJiraDataItems(Map data) {
        // Bump test steps indizes from 0-based to 1-based counting
        data.tests.each { test ->
            test.getValue().steps.each { step ->
                step.index++
            }
        }

        return data
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

    List<JiraDataItem> getAutomatedTests(String componentName = null, List<String> testTypes = []) {
        return this.data.jira.tests.findAll { key, testIssue ->
            def result = testIssue.status.toLowerCase() == "ready to test" && testIssue.executionType?.toLowerCase() == "automated"

            if (result && componentName) {
                result = testIssue.getResolvedComponents()
                    .collect { it.name.toLowerCase() }
                    .contains(componentName.toLowerCase())
            }

            if (result && testTypes) {
                result = testTypes.collect { it.toLowerCase() }.contains(testIssue.testType.toLowerCase())
            }

            return result
        }.values() as List
    }

    Map getEnumDictionary(String dictionaryName = null) {
        return this.data.jira.project.enumDictionary.find { d ->
            d.key.toLowerCase() == dictionaryName.toLowerCase()
        }.value
    }

    Map getProjectProperties() {
        return this.data.jira.project.projectProperties
    }

    List<JiraDataItem> getAutomatedTestsTypeAcceptance(String componentName = null) {
        return this.getAutomatedTests(componentName, [TestType.ACCEPTANCE])
    }

    List<JiraDataItem> getAutomatedTestsTypeInstallation(String componentName = null) {
        return this.getAutomatedTests(componentName, [TestType.INSTALLATION])
    }

    List<JiraDataItem> getAutomatedTestsTypeIntegration(String componentName = null) {
        return this.getAutomatedTests(componentName, [TestType.INTEGRATION])
    }

    List<JiraDataItem> getAutomatedTestsTypeUnit(String componentName = null) {
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

    List<JiraDataItem> getBugs() {
        return this.data.jira.bugs.values() as List
    }

    List<JiraDataItem> getComponents() {
        return this.data.jira.components.values() as List
    }

    String getDescription() {
        return this.data.metadata.description
    }

    List<Map> getDocumentTrackingIssues() {
        return this.data.jira.docs.values() as List
    }

    List<Map> getDocumentTrackingIssues(List<String> labels) {
        def result = []

        labels.each { label ->
            this.getDocumentTrackingIssues().each { issue ->
                if (issue.labels.collect { it.toLowerCase() }.contains(label.toLowerCase())) {
                    result << [key: issue.key, status: issue.status]
                }
            }
        }

        return result.unique()
    }

    List<Map> getDocumentTrackingIssuesNotDone(List<String> labels) {
        return this.getDocumentTrackingIssues(labels).findAll { !it.status.equalsIgnoreCase("done") }
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
                label: "Get Git URL for repository at path '${path}' and origin '${remote}'",
                script: "git config --get remote.${remote}.url",
                returnStdout: true
            ).trim()
        }

        return new URIBuilder(result).build()
    }

    List<JiraDataItem> getEpics() {
        return this.data.jira.epics.values() as List
    }

    String getId() {
        return this.data.jira.project.id
    }

    String getKey() {
        return this.data.metadata.id
    }

    List<JiraDataItem> getMitigations() {
        return this.data.jira.mitigations.values() as List
    }

    String getName() {
        return this.data.metadata.name
    }

    List<Map> getRepositories() {
        return this.data.metadata.repositories
    }

    List<JiraDataItem> getRisks() {
        return this.data.jira.risks.values() as List
    }

    Map getServices() {
        return this.data.metadata.services
    }

    List<JiraDataItem> getSystemRequirements(String componentName = null, List<String> gampTopics = []) {
        return this.data.jira.requirements.findAll { key, req ->
            def result = true

            if (result && componentName) {
                result = req.getResolvedComponents().collect { it.name.toLowerCase() }.contains(componentName.toLowerCase())
            }

            if (result && gampTopics) {
                result = gampTopics.collect { it.toLowerCase() }.contains(req.gampTopic.toLowerCase())
            }

            return result
        }.values() as List
    }

    List<JiraDataItem> getSystemRequirementsTypeAvailability(String componentName = null) {
        return this.getSystemRequirements(componentName, [GampTopic.AVAILABILITY_REQUIREMENT])
    }

    List<JiraDataItem> getSystemRequirementsTypeConstraints(String componentName = null) {
        return this.getSystemRequirements(componentName, [GampTopic.CONSTRAINT])
    }

    List<JiraDataItem> getSystemRequirementsTypeFunctional(String componentName = null) {
        return this.getSystemRequirements(componentName, [GampTopic.FUNCTIONAL_REQUIREMENT])
    }

    List<JiraDataItem> getSystemRequirementsTypeInterfaces(String componentName = null) {
        return this.getSystemRequirements(componentName, [GampTopic.INTERFACE_REQUIREMENT])
    }

    List<JiraDataItem> getTechnicalSpecifications(String componentName = null) {
        return this.data.jira.techSpecs.findAll { key, techSpec ->
            def result = true

            if (result && componentName) {
                result = techSpec.getResolvedComponents().collect { it.name.toLowerCase() }.contains(componentName.toLowerCase())
            }

            return result
        }.values() as List
    }

    List<JiraDataItem> getTests() {
        return this.data.jira.tests.values() as List
    }

    String getOpenShiftApiUrl() {
        return "N/A"
    }

    boolean hasCapability(String name) {
        def collector = {
            return (it instanceof Map) ? it.keySet().first().toLowerCase() : it.toLowerCase()
        }

        return this.capabilities.collect(collector).contains(name.toLowerCase())
    }

    boolean hasFailingTests() {
        return this.data.build.hasFailingTests
    }

    boolean hasUnexecutedJiraTests() {
        return this.data.build.hasUnexecutedJiraTests
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
            changeDescription            : changeDescription,
            changeId                     : changeId,
            configItem                   : configItem,
            sourceEnvironmentToClone     : sourceEnvironmentToClone,
            sourceEnvironmentToCloneToken: sourceEnvironmentToCloneToken,
            targetEnvironment            : targetEnvironment,
            targetEnvironmentToken       : targetEnvironmentToken,
            version                      : version
        ]
    }

    protected Map loadJiraData(String projectKey) {
        return new JsonSlurperClassic().parseText(TEMP_FAKE_JIRA_DATA)
    }

    protected Map loadJiraDataProjectVersion() {
        if (!this.jira) return [:]

        return this.jira.getVersionsForProject(this.data.jira.project.key).find { version ->
            this.buildParams.version == version.value
        }
    }

    protected Map loadJiraDataDocs() {
        if (!this.jira) return [:]

        def jqlQuery = [jql: "project = ${this.data.jira.project.key} AND issuetype = '${LeVADocumentUseCase.IssueTypes.LEVA_DOCUMENTATION}'"]

        def jiraIssues = this.jira.getIssuesForJQLQuery(jqlQuery)
        if (jiraIssues.isEmpty()) {
            throw new IllegalArgumentException("Error: Jira data does not include references to items of type '${JiraDataItem.TYPE_DOCS}'.")
        }

        return jiraIssues.collectEntries { jiraIssue ->
            [
                jiraIssue.key,
                [
                    key        : jiraIssue.key,
                    name       : jiraIssue.fields.summary,
                    description: jiraIssue.fields.description,
                    status     : jiraIssue.fields.status.name,
                    labels     : jiraIssue.fields.labels
                ]
            ]
        }
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

    @NonCPS
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

    void setHasUnexecutedJiraTests(boolean status) {
        this.data.build.hasUnexecutedJiraTests = status
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
