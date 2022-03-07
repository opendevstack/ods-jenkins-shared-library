package org.ods.core.test.usecase.levadoc.fixture

class DocTypeProjectFixtureWithComponent extends DocTypeProjectFixtureBase {

    DocTypeProjectFixtureWithComponent() {
        super( [ "DTR", "TIR"])
    }

    @Override
    List addDocTypes(Map project, List projects) {
        docTypes.each { docType ->
            if (project.docsToTest.contains(docType))
                addModules(project, docType as String, projects)
        }
    }

    private void addModules(Map project, String docType, List projects) {
        List<String> components = project.components.split("\\s*,\\s*")
        components.each { repo ->
            projects.add(ProjectFixture.getProjectFixtureBuilder(project, docType).component(repo).build())
        }
    }

    static boolean notIsReleaseModule(repo) {
        !repo.id.contains("release") &&  !repo.type.contains("ods-test")
    }

}
