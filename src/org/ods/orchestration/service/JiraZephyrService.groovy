package org.ods.orchestration.service

@Grab(group="com.konghq", module="unirest-java", version="2.4.03", classifier="standalone")

import com.cloudbees.groovy.cps.NonCPS

import groovy.json.JsonOutput
import groovy.json.JsonSlurperClassic

import kong.unirest.Unirest
import org.ods.util.ILogger

@SuppressWarnings('LineLength')
class JiraZephyrService extends JiraService {

    class ExecutionStatus {
        static final String PASS = "1"
        static final String FAIL = "2"
        static final String WIP = "3"
        static final String BLOCKED = "4"
    }

    JiraZephyrService(String baseURL, String username, String password, ILogger logger) {
        super(baseURL, username, password, logger)
    }

    @NonCPS
    Map createTestCycle(String projectId, String versionId, String name, String build, String environment) {
        if (!projectId?.trim()) {
            throw new IllegalArgumentException("Error: unable to create test cycle for Jira issues. 'projectId' is undefined.")
        }

        if (!versionId?.trim()) {
            throw new IllegalArgumentException("Error: unable to create test cycle for Jira issues. 'versionId' is undefined.")
        }

        if (!name?.trim()) {
            throw new IllegalArgumentException("Error: unable to create test cycle for Jira issues. 'name' is undefined.")
        }

        if (!build?.trim()) {
            throw new IllegalArgumentException("Error: unable to create test cycle for Jira issues. 'build' is undefined.")
        }

        if (!environment?.trim()) {
            throw new IllegalArgumentException("Error: unable to create test cycle for Jira issues. 'environment' is undefined.")
        }

        def response = Unirest.post("${this.baseURL}/rest/zapi/latest/cycle/")
            .basicAuth(this.username, this.password)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .body(JsonOutput.toJson(
                [
                    projectId: projectId,
                    versionId: versionId,
                    name: name,
                    build: build,
                    environment: environment
                ]
            ))
            .asString()

        response.ifFailure {
            def message = "Error: unable to create test cycle for Jira issues. Jira responded with code: '${response.getStatus()}' and message: '${response.getBody()}'."

            if (response.getStatus() == 404) {
                message = "Error: unable to create test cycle for Jira issues. Jira could not be found at: '${this.baseURL}'."
            }

            throw new RuntimeException(message)
        }

        return new JsonSlurperClassic().parseText(response.getBody())
    }

    @NonCPS
    Map createTestExecutionForIssue(String issueId, String projectId, String testCycleId) {
        if (!issueId?.trim()) {
            throw new IllegalArgumentException("Error: unable to create test execution for Jira issue. 'issueId' is undefined.")
        }

        if (!projectId?.trim()) {
            throw new IllegalArgumentException("Error: unable to create test execution for Jira issue. 'projectId' is undefined.")
        }

        if (!testCycleId?.trim()) {
            throw new IllegalArgumentException("Error: unable to create test execution for Jira issue. 'testCycleId' is undefined.")
        }

        def response = Unirest.post("${this.baseURL}/rest/zapi/latest/execution/")
            .basicAuth(this.username, this.password)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .body(JsonOutput.toJson(
                [
                    issueId: issueId,
                    projectId: projectId,
                    cycleId: testCycleId
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
    Map getTestCycles(String projectId, String versionId) {
        if (!projectId?.trim()) {
            throw new IllegalArgumentException("Error: unable to get project cycles from Jira. 'projectId' is undefined.")
        }
        if (!versionId?.trim()) {
            throw new IllegalArgumentException("Error: unable to get project cycles from Jira. 'versionId' is undefined.")
        }

        def response = Unirest.get("${this.baseURL}/rest/zapi/latest/cycle")
            .queryString("projectId", projectId)
            .queryString("versionId", versionId)
            .basicAuth(this.username, this.password)
            .header("Accept", "application/json")
            .asString()

        response.ifFailure {
            def message = "Error: unable to get project cycles. Jira responded with code: '${response.getStatus()}' and message: '${response.getBody()}'."

            if (response.getStatus() == 404) {
                message = "Error: unable to get project cycles. Jira could not be found at: '${this.baseURL}'."
            }

            throw new RuntimeException(message)
        }

        return new JsonSlurperClassic().parseText(response.getBody())
    }

    @NonCPS
    void updateTestExecutionForIssue(String testExecutionId, String status) {
        if (!testExecutionId?.trim()) {
            throw new IllegalArgumentException("Error: unable to update test execution for Jira issue. 'testExecutionId' is undefined.")
        }

        if (!status?.trim()) {
            throw new IllegalArgumentException("Error: unable to update test execution for Jira issue. 'status' is undefined.")
        }

        def response = Unirest.put("${this.baseURL}/rest/zapi/latest/execution/{executionId}/execute")
            .routeParam("executionId", testExecutionId)
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

    void updateTestExecutionForIssueBlocked(String executionId) {
        this.updateTestExecutionForIssue(executionId, ExecutionStatus.BLOCKED)
    }

    void updateTestExecutionForIssueFail(String executionId) {
        this.updateTestExecutionForIssue(executionId, ExecutionStatus.FAIL)
    }

    void updateTestExecutionForIssuePass(String executionId) {
        this.updateTestExecutionForIssue(executionId, ExecutionStatus.PASS)
    }

    void updateTestExecutionForIssueWip(String executionId) {
        this.updateTestExecutionForIssue(executionId, ExecutionStatus.WIP)
    }
}
