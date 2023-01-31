package org.ods.core.test.usecase.levadoc.fixture

import groovy.transform.ToString
import groovy.transform.builder.Builder

@ToString(includePackage = false, includeNames=true, ignoreNulls = true)
@Builder
class ProjectFixture {
    String project
    String releaseKey
    String version
    String docType
    Boolean overall
    String component
    String validation

    static getProjectFixtureBuilder(Map project, String docType) {
        return ProjectFixture.builder()
            .project(project.id as String)
            .releaseKey(project.releaseId as String)
            .version(project.version as String)
            .validation(project.validation as String)
            .docType(docType)
    }
}
