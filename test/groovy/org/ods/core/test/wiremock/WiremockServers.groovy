package org.ods.core.test.wiremock

/**
 * Add jiraURL in gradle.properties to change default jiraURL
 * Add nexusURL in gradle.properties to change default nexusURL
 * Add docGenURL in gradle.properties to change default docGenURL
 * Add jiraUser & jiraPassword in gradle.properties to change Jira user/password
 */
enum WiremockServers {
    SONAR_QU {
        WiremockManager build() {
            new WiremockManager("sonarQu", getBaseUrl())
        };
        String getUser(){
            return System.properties["sonar.username"]
        };
        String getPassword(){
            return System.properties["sonar.password"]
        }
        String getBaseUrl(){
            return System.properties["sonar.url"]
        }

    },
    JIRA {
        WiremockManager build() {
            new WiremockManager("jira", getBaseUrl())
        };
        String getUser(){
            return System.properties["jira.username"]
        };
        String getPassword(){
            return System.properties["jira.password"]
        }
        String getBaseUrl(){
            return System.properties["jira.url"]
        }

    },
    NEXUS {
        WiremockManager build() {
            new WiremockManager("nexus", getBaseUrl())
        };
        String getUser(){
            return System.properties["nexus.username"]
        };
        String getPassword(){
            return System.properties["nexus.password"]
        }
        String getBaseUrl(){
            return System.properties["nexus.url"]
        }

    },
    DOC_GEN {
        WiremockManager build() {
            new WiremockManager("docgen", getBaseUrl())
        };
        String getUser(){
            throw new RuntimeException("no user needed for docGen")
        };
        String getPassword(){
            throw new RuntimeException("no password needed for docGen")
        }
        String getBaseUrl(){
            return System.properties["docGen.url"]
        }

    },
    BITBUCKET {
        WiremockManager build() {
            new WiremockManager("bitbucket", getBaseUrl())
        };
        String getUser(){
            return System.properties["bitbucket.username"]
        };
        String getPassword(){
            return System.properties["bitbucket.password"]
        }
        String getBaseUrl(){
            return System.properties["bitbucket.url"]
        }
    }

    abstract WiremockManager build();
    abstract String getUser();
    abstract String getPassword();
    abstract String getBaseUrl();
}

