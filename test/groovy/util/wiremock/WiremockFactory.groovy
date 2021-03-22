package util.wiremock

enum WiremockFactory {
    BITBUCKET {
        WiremockManager  build() {
            String defaultURL =  "http://bitbucket.odsbox.lan:7990"
            new WiremockManager("bitbucket", defaultURL)
        }
    },
    JIRA {
        WiremockManager  build() {
            String defaultURL =   "http://jira.odsbox.lan:8080/"
            new WiremockManager("jira", defaultURL)
        }
    },
    DOC_GEN {
        WiremockManager  build() {
            String defaultURL =  "http://172.30.234.65:8080"
            new WiremockManager("docgen", defaultURL)
        }
    }

    abstract WiremockManager build();
}

