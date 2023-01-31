package org.ods.core.test.usecase.levadoc.fixture

class DocTypeProjectFixturesOverall extends DocTypeProjectFixtureBase {

    DocTypeProjectFixturesOverall() {
        super( ["DTR", "TIR"])
    }

    def addDocTypes(Map project, List projects) {
        docTypes.each { docType ->
            projects.add(ProjectFixture.getProjectFixtureBuilder(project, docType).overall(true).build())
        }
    }

}
