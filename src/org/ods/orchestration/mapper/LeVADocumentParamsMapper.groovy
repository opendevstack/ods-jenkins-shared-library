package org.ods.orchestration.mapper

import org.ods.orchestration.util.Project

class LeVADocumentParamsMapper {

    private final Project project

    LeVADocumentParamsMapper(Project project) {
        this.project = project
    }

    Map build(Map data = [: ]) {
        return [
            build: mapBuildData(),
            git: mapGitData(),
            openshift: [
                targetApiUrl: this.project.getOpenShiftApiUrl() //TODO is different?
            ],
        ] << data
    }

    private Map<String, Object> mapGitData() {
        [
            commit: this.project.gitData.commit,
            releaseManagerRepo: this.project.gitData.releaseManagerRepo,
            releaseManagerBranch: this.project.data.gitReleaseManagerBranch,
            baseTag: this.project.gitData.baseTag,
            targetTag: this.project.gitData.targetTag,
            author: this.project.gitData.author,
            message: this.project.gitData.message,
            time: this.project.gitData.time,
        ]
    }

    private Map<String, Object> mapBuildData() {
        [
            targetEnvironment: this.project.buildParams.targetEnvironment,
            targetEnvironmentToken: this.project.buildParams.targetEnvironmentToken,
            version: this.project.buildParams.version,
            configItem: this.project.buildParams.configItem,
            changeDescription: this.project.buildParams.changeDescription,
            changeId: this.project.buildParams.changeId,
            rePromote: this.project.buildParams.rePromote,
            releaseStatusJiraIssueKey: this.project.buildParams.releaseStatusJiraIssueKey,
            runDisplayUrl: this.project.steps.env.RUN_DISPLAY_URL,
            releaseParamVersion: this.project.steps.env.RELEASE_PARAM_VERSION,
            buildId: this.project.steps.env.BUILD_ID, // TODO is different?
            buildURL: this.project.steps.env.BUILD_URL, // TODO is different?
            jobName: this.project.steps.env.JOB_NAME,
            testResultsURLs: this.project.data.build.testResultsURLs,
            jenkinLog: this.project.data.jenkinLog,
        ]
    }

}
