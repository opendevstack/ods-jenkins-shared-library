package org.ods.core.test.wiremock

enum WiremockFactory {
    BITBUCKET {
        WiremockManager  build() {
            String defaultURL =  "http://bitbucket.odsbox.lan:7990"
            new WiremockManager("bitbucket", defaultURL)
        }
    },
    JIRA {
        WiremockManager  build() {
            // Add jiraURL in gradle.properties to change default jiraURL
            String defaultURL =   System.properties["jiraURL"]
            new WiremockManager("jira", defaultURL)
        }
    },
    DOC_GEN {
        WiremockManager  build() {
            String defaultURL =   "http://docgen.odsbox.lan:8080"
            new WiremockManager("docgen", defaultURL)
        }
    }

    abstract WiremockManager build();
}

