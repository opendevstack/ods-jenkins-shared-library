package org.ods.service

@Grab(group="com.konghq", module="unirest-java", version="2.4.03", classifier="standalone")

import com.cloudbees.groovy.cps.NonCPS

import groovy.json.JsonOutput
import groovy.json.JsonSlurperClassic

import kong.unirest.Unirest

class JiraZephyrService extends JiraService {

    class ExecutionStatus {
        static final String PASS = "1"
        static final String FAIL = "2"
        static final String WIP = "3"
        static final String BLOCKED = "4"
    }

    JiraZephyrService(String baseURL, String username, String password) {
        super(baseURL, username, password)
    }

    @NonCPS
    Map createTestExecutionForIssue(String issueId, String projectId) {
        if (!issueId?.trim()) {
            throw new IllegalArgumentException("Error: unable to create test execution for Jira issue. 'issueId' is undefined.")
        }

        if (!projectId?.trim()) {
            throw new IllegalArgumentException("Error: unable to create test execution for Jira issue. 'projectId' is undefined.")
        }

        def response = Unirest.post("${this.baseURL}/rest/zapi/latest/execution/")
            .basicAuth(this.username, this.password)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .body(JsonOutput.toJson(
                [
                    issueId: issueId,
                    projectId: projectId
                ]
            ))
            .asString()

        response.ifFailure {
            def message = "Error: unable to create test execution for Jira issue. Jira responded with code: '${response.getStatus()}' and message: '${response.getBody()}'."

            if (response.getStatus() == 404) {
                message = "Error: unable to create test execution for Jira issue. Jira could not be found at: '${this.baseURL}'."
            }

            throw new RuntimeException(message)
        }

        return new JsonSlurperClassic().parseText(response.getBody())
    }

    @NonCPS
    Map getProject(String projectKey) {
        if (!projectKey?.trim()) {
            throw new IllegalArgumentException("Error: unable to get project from Jira. 'projectKey' is undefined.")
        }

        def response = Unirest.get("${this.baseURL}/rest/api/2/project/{projectKey}")
            .routeParam("projectKey", projectKey.toUpperCase())
            .basicAuth(this.username, this.password)
            .header("Accept", "application/json")
            .asString()

        response.ifFailure {
            def message = "Error: unable to get project. Jira responded with code: '${response.getStatus()}' and message: '${response.getBody()}'."

            if (response.getStatus() == 404) {
                message = "Error: unable to get project. Jira could not be found at: '${this.baseURL}'."
            }

            throw new RuntimeException(message)
        }

        return new JsonSlurperClassic().parseText(response.getBody())
    }

    @NonCPS
    List getTestDetailsForIssue(String issueId) {
        if (!issueId?.trim()) {
            throw new IllegalArgumentException("Error: unable to get test details for Jira issue. 'issueId' is undefined.")
        }

        def response = Unirest.get("${this.baseURL}/rest/zapi/latest/teststep/{issueId}")
            .routeParam("issueId", issueId)
            .basicAuth(this.username, this.password)
            .header("Accept", "application/json")
            .asString()
        
        response.ifFailure {
            def message = "Error: unable to get test details for Jira issue. Jira responded with code: '${response.getStatus()}' and message: '${response.getBody()}'."

            if (response.getStatus() == 404) {
                message = "Error: unable to get test details for Jira issue. Jira could not be found at: '${this.baseURL}'."
            }

            throw new RuntimeException(message)
        }

        return new JsonSlurperClassic().parseText(response.getBody()).stepBeanCollection ?: []
    }

    @NonCPS
    void updateExecutionForIssue(String executionId, String status) {
        if (!executionId?.trim()) {
            throw new IllegalArgumentException("Error: unable to update test execution for Jira issue. 'executionId' is undefined.")
        }
        if (!status?.trim()) {
            throw new IllegalArgumentException("Error: unable to update test execution for Jira issue. 'status' is undefined.")
        }


        def response = Unirest.put("${this.baseURL}/rest/zapi/latest/execution/{executionId}/execute")
            .routeParam("executionId", executionId)
            .basicAuth(this.username, this.password)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .body(JsonOutput.toJson(
                [
                      status: status
                ]
            ))
            .asString()


        response.ifFailure {
            def message = "Error: unable to update test execution for Jira issue. Jira responded with code: '${response.getStatus()}' and message: '${response.getBody()}'."

            if (response.getStatus() == 404) {
                message = "Error: unable to update test execution for Jira issue. Jira could not be found at: '${this.baseURL}'."
            }

            throw new RuntimeException(message)
        }
    }

    void updateExecutionForIssuePass(String executionId) {
        this.updateExecutionForIssue(executionId, ExecutionStatus.PASS)
    }

    void updateExecutionForIssueFail(String executionId) {
        this.updateExecutionForIssue(executionId, ExecutionStatus.FAIL)
    }

    void updateExecutionForIssueWip(String executionId) {
        this.updateExecutionForIssue(executionId, ExecutionStatus.WIP)
    }

    void updateExecutionForIssueBlocked(String executionId) {
        this.updateExecutionForIssue(executionId, ExecutionStatus.BLOCKED)
    }
}
