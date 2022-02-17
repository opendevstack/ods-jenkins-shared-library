package org.ods.orchestration.mapper

import org.ods.orchestration.util.Project
import org.ods.util.IPipelineSteps

class DefaultLeVADocumentParamsMapper {
    private final Project project
    private final IPipelineSteps steps
    private final Map data

    DefaultLeVADocumentParamsMapper(Project project, IPipelineSteps steps, Map data = [:]) {
        this.project = project
        this.steps = steps
        this.data = data
    }

    Map build() {
        return [
            build    : [
                targetEnvironment        : this.project.buildParams.targetEnvironment,
                targetEnvironmentToken   : this.project.buildParams.targetEnvironmentToken,
                version                  : this.project.buildParams.version,
                configItem               : this.project.buildParams.configItem,
                changeDescription        : this.project.buildParams.changeDescription,
                changeId                 : this.project.buildParams.changeId,
                rePromote                : this.project.buildParams.rePromote,
                releaseStatusJiraIssueKey: this.project.buildParams.releaseStatusJiraIssueKey,
                runDisplayUrl            : this.steps.env.RUN_DISPLAY_URL,
                releaseParamVersion      : this.steps.env.RELEASE_PARAM_VERSION,
                buildId                  : this.steps.env.BUILD_ID, // TODO is different?
                buildURL                 : this.steps.env.BUILD_URL, // TODO is different?
                jobName                  : this.steps.env.JOB_NAME
            ],
            git      : [
                commit              : this.project.gitData.commit,
                repoURL             : this.project.gitData.url, // TODO is different?
                releaseManagerBranch: this.project.data.gitReleaseManagerBranch,
                baseTag             : this.project.gitData.baseTag,
                targetTag           : this.project.gitData.targetTag,
                author              : this.project.gitData.author,
                message             : this.project.gitData.message,
                commitTime          : this.project.gitData.time, // TODO is different?
            ],
            openshift: [
                targetApiUrl: this.project.getOpenShiftApiUrl() //TODO is different?
            ]
        ] << data
    }
}
