package org.ods.core.test.usecase.levadoc.fixture


import org.yaml.snakeyaml.Yaml

class DocTypeProjectFixtureWithComponent extends DocTypeProjectFixtureBase {

    DocTypeProjectFixtureWithComponent() {
        super( [ "TIR", "DTR"])
    }

    @Override
    List addDocTypes(Map project, List projects) {
        docTypes.each { docType ->
           addModules(project, docType, projects)
        }
    }

    private void addModules(Map project, String docType, List projects) {
        def meta = new Yaml().load(new File("test/resources/workspace/${project.id}/metadata.yml").text)
        meta.repositories.each { repo ->
            if (notIsReleaseModule(repo)) {
                projects.add(ProjectFixture.getProjectFixtureBuilder(project, docType).component(repo.id as String).build())
            }
        }
    }

    static boolean notIsReleaseModule(repo) {
        !repo.id.contains("release") &&  !repo.type.contains("ods-test")
    }

}
