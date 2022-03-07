package org.ods.core.test.usecase.levadoc.fixture

import org.yaml.snakeyaml.Yaml

abstract class DocTypeProjectFixtureBase {

    private static final String FILENAME_WITH_PROJECTS = "leva-doc-functional-test-projects.yml"
    private static final String FILENAME_PATH = "test/resources"
    private static final String LEVA_DOC_FUNCTIONAL_TESTS_PROJECTS = "${FILENAME_PATH}/${FILENAME_WITH_PROJECTS}"

    private static final String ONLY_TEST_ONE_PROJECT = ""
    private static final List<String> SKIP_TEST_PROJECTS = ["trfdgp", "g3dgp", "g4dgp"]

    protected final List docTypes

    DocTypeProjectFixtureBase(docTypes){
        this.docTypes = docTypes
    }

    List<ProjectFixture> getProjects(){
        List projectsToTest = []
        try {
            def functionalTest = new Yaml().load(new File(LEVA_DOC_FUNCTIONAL_TESTS_PROJECTS).text)
            List projects = functionalTest.projects.findAll { project ->
                if (ONLY_TEST_ONE_PROJECT == project.id)
                    return true
                if (ONLY_TEST_ONE_PROJECT.length()>0)
                    return false
                if (SKIP_TEST_PROJECTS.size()>0 && SKIP_TEST_PROJECTS.contains(project.id))
                    return false
                return true
            }
            projects.each{ project ->
                addDocTypes(project as Map, projectsToTest)
            }
        } catch(RuntimeException runtimeException){
            // If there's an error here, the log.error doesn't work
            System.out.print("Error loading project metadata from LEVA_DOC_FUNCTIONAL_TESTS_PROJECTS")
            System.out.print("Error:${runtimeException.message}")
            runtimeException.printStackTrace()
            throw runtimeException
        }

        return projectsToTest
    }

    abstract addDocTypes(Map project, List projects)
}
