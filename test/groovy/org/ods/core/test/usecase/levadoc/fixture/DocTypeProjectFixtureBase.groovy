package org.ods.core.test.usecase.levadoc.fixture


import org.yaml.snakeyaml.Yaml

abstract class DocTypeProjectFixtureBase {

    private static final String LEVA_DOC_FUNCTIONAL_TESTS_PROJECTS = "test/resources/leva-doc-functional-test-projects.yml"

    final List docTypes

    DocTypeProjectFixtureBase(docTypes){
        this.docTypes = docTypes
    }

    List<ProjectFixture> getProjects(){
        List projects = []
        def functionalTest = new Yaml().load(new File(LEVA_DOC_FUNCTIONAL_TESTS_PROJECTS).text)
        functionalTest.projects.each{ project ->
            addDocTypes(project as Map, projects)
        }
        return projects
    }

    abstract addDocTypes(Map project, List projects)
}
