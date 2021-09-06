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
            new WiremockManager("sonarQu", System.properties["sonarQuURL"])
        };
        String getUser(){
            return System.properties["domainUser"]
        };
        String getPassword(){
            return System.properties["domainPassword"]
        }
    },
    JIRA {
        WiremockManager build() {
            new WiremockManager("jira", System.properties["jiraURL"])
        };
        String getUser(){
            return System.properties["domainUser"]
        };
        String getPassword(){
            return System.properties["domainPassword"]
        }
    },
    NEXUS {
        WiremockManager build() {
            new WiremockManager("nexus", System.properties["nexusURL"])
        };
        String getUser(){
            return System.properties["nexusUser"]
        };
        String getPassword(){
            return System.properties["nexusPassword"]
        }
    },
    DOC_GEN {
        WiremockManager build() {
            new WiremockManager("docgen", System.properties["docGenURL"])
        };
        String getUser(){
            return ""
        };
        String getPassword(){
            return ""
        }
    }

    abstract WiremockManager build();
    abstract String getUser();
    abstract String getPassword();
}

