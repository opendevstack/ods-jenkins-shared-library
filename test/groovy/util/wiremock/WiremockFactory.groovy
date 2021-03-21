package util.wiremock

enum WiremockFactory {
    BITBUCKET {
        WiremockFacade  build() {
            String defaultURL =  "http://172.30.234.65:8080"
            new WiremockFacade("${WIREMOCK_FILES}/bitbucket/${scenario}", null)
        }
    },
    JIRA {
        WiremockFacade  build() {
            String defaultURL =  "http://172.30.234.65:8080"
            new WiremockFacade("${WIREMOCK_FILES}/jira", defaultURL)
        }
    },
    DOC_GEN {
        WiremockFacade  build() {
            String defaultURL =  "http://172.30.234.65:8080"
            new WiremockFacade("${WIREMOCK_FILES}/docgen", defaultURL)
        }
    }

    private static final String WIREMOCK_FILES = "test/resources/wiremock/bitbucket"
    abstract WiremockFacade build();
}

