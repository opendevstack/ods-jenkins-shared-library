package org.ods.core.test.usecase.levadoc.fixture

class DocTypeProjectFixture extends DocTypeProjectFixtureBase {

    DocTypeProjectFixture() {
        super(["CSD", "DIL", "DTP", "RA", "CFTP", "IVP", "SSDS", "TCP", "TIP", "TRC"])
    }

    DocTypeProjectFixture(docTypes) {
        super(docTypes)
    }

    def addDocTypes(Map project, List projects) {
        docTypes.each { docType ->
            projects.add(ProjectFixture.getProjectFixtureBuilder(project, docType).build())
        }
    }

}
