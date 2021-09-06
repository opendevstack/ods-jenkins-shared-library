package org.ods.core.test.jira

import groovy.util.logging.Slf4j
import org.ods.orchestration.service.JiraService

@Slf4j
class JiraServiceForWireMock extends JiraService {
    JiraServiceForWireMock(String baseURL, String username, String password) {
        super(baseURL, username, password)
    }

    @Override
    void addLabelsToIssue(String issueIdOrKey, List names) {
        log.warn("addLabelsToIssue - issueIdOrKey:$issueIdOrKey")
    }

    @Override
    void appendCommentToIssue(String issueIdOrKey, String comment) {
        log.warn("appendCommentToIssue - issueIdOrKey:$issueIdOrKey")
    }

    @Override
    void createIssueLinkTypeBlocks(Map inwardIssue, Map outwardIssue) {
        log.warn("createIssueLinkTypeBlocks - inwardIssue:$inwardIssue")
    }

    @Override
    Map createIssueType(String type, String projectKey, String summary, String description, String fixVersion = null) {
        log.warn("createIssueType - type:${type}")
    }

    @Override
    void removeLabelsFromIssue(String issueIdOrKey, List names) {
        log.warn("removeLabelsFromIssue - issueIdOrKey:$issueIdOrKey")
    }

    @Override
    void updateSelectListFieldsOnIssue(String issueIdOrKey, Map fields) {
        log.warn("updateSelectListFieldsOnIssue - issueIdOrKey:$issueIdOrKey")
    }

    @Override
    void updateTextFieldsOnIssue(String issueIdOrKey, Map fields) {
        log.warn("updateTextFieldsOnIssue - issueIdOrKey:$issueIdOrKey")
    }
}
