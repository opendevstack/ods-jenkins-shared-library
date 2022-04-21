package org.ods.core.test.usecase.levadoc.fixture

import groovy.transform.ToString
import groovy.transform.builder.Builder
import org.ods.orchestration.usecase.LeVADocumentUseCase

@ToString(includePackage = false, includeNames=true, ignoreNulls = true)
@Builder
class ProjectFixture {

    String project
    String description
    String buildNumber
    String releaseKey
    String version
    List<String> docsToTest
    String templatesVersion
    String docType
    Boolean overall
    List<String> components
    String component
    String validation
    String releaseManagerRepo
    String releaseManagerBranch
    Map<String, String> stepsEnv

    static getProjectFixtureBuilder(Map project, String docType) {
        List<String> docsToTest = project.docsToTest?.split("\\s*,\\s*")
        List<String> components = project.components?.split("\\s*,\\s*")
        return ProjectFixture.builder()
            .project(project.id as String)
            .description(project.description as String)
            .buildNumber(project.buildNumber as String)
            .version(project.version as String)
            .docsToTest(docsToTest)
            .templatesVersion(project.templatesVersion as String)
            .validation(project.validation as String)
            .releaseManagerRepo(project.releaseManagerRepo as String)
            .releaseManagerBranch(project.releaseManagerBranch as String)
            .components(components)
            .docType(docType)
            .stepsEnv(project.stepsEnvironment)
    }

    String getJobName() {
        return "${project}-cd/${project}-releasemanager"
    }

    String getProjectWithBuildNumber() {
        return "${project}/${buildNumber}"
    }

    String getJenkinsLogUrl() {
        String projectWithBuild = getProjectWithBuildNumber()
        "repository/leva-documentation/${projectWithBuild}/jenkins-job-log.zip"
    }

    Map<String, String> getTestResultsUrls() {
        String projectWithBuild = getProjectWithBuildNumber()
        return [
            "unit-backend": "repository/leva-documentation/${projectWithBuild}/unit-backend.zip",
            "unit-frontend": "repository/leva-documentation/${projectWithBuild}/unit-frontend.zip",
            "acceptance" : "repository/leva-documentation/${projectWithBuild}/acceptance.zip",
            'installation' : "repository/leva-documentation/${projectWithBuild}/installation.zip",
            'integration' : "repository/leva-documentation/${projectWithBuild}/integration.zip",
        ]
    }

    Map<String, String> getOpenshiftData() {
        return [
            targetApiUrl: "https://openshiftTargetUrl.local"
        ]
    }

}
