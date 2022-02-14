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
            new WiremockManager("sonarQu", System.properties["sonar.url"])
        };
        String getUser(){
            return System.properties["sonar.username"]
        };
        String getPassword(){
            return System.properties["sonar.password"]
        }
    },
    JIRA {
        WiremockManager build() {
            new WiremockManager("jira", System.properties["jira.url"])
        };
        String getUser(){
            return System.properties["jira.username"]
        };
        String getPassword(){
            return System.properties["jira.password"]
        }
    },
    NEXUS {
        WiremockManager build() {
            new WiremockManager("nexus", System.properties["nexus.url"])
        };
        String getUser(){
            return System.properties["nexus.username"]
        };
        String getPassword(){
            return System.properties["nexus.password"]
        }
    },
    DOC_GEN {
        WiremockManager build() {
            new WiremockManager("docgen", System.properties["docGen.url"])
        };
        String getUser(){
            return "docGen.username"
        };
        String getPassword(){
            return "docGen.password"
        }
    },
    BITBUCKET {
        WiremockManager build() {
            new WiremockManager("bitbucket", System.properties["bitbucket.url"])
        };
        String getUser(){
            return "bitbucket.username"
        };
        String getPassword(){
            return "bitbucket.password"
        }
    }

    abstract WiremockManager build();
    abstract String getUser();
    abstract String getPassword();
}

