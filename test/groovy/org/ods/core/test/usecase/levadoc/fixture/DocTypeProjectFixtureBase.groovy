package org.ods.core.test.usecase.levadoc.fixture


import org.yaml.snakeyaml.Yaml

class DocTypeProjectFixtureBase {

    private static final String FILENAME_WITH_PROJECTS = "leva-doc-functional-test-projects.yml"
    private static final String FILENAME_PATH = "test/resources"
    private static final String LEVA_DOC_FUNCTIONAL_TESTS_PROJECTS = "${FILENAME_PATH}/${FILENAME_WITH_PROJECTS}"

    private static final String ONLY_TEST_ONE_PROJECT = ""
    private static final List<String> SKIP_TEST_PROJECTS = ["trfdgp", "g3dgp", "g4dgp"]

    public static final DocTypeDetails [] DOC_TYPES_BASIC = [
        getDoc("CSD"), getDoc("DIL"), getDoc("DTP"), getDoc("RA"),
        getDoc("CFTP"), getDoc("IVP"), getDoc("SSDS"), getDoc("TCP"),
        getDoc("TIP"), getDoc("TRC")]

    public static final DocTypeDetails [] DOC_TYPES_WITH_TEST_DATA = [
        getDoc("TCR", true),
        getDoc("CFTR", true),
        getDoc("IVR", true) ]

    public static final DocTypeDetails [] DOC_TYPES_WITH_COMPONENT = [
        getDoc("DTR", false, true),
        getDoc("TIR", false, true)]

    public static final DocTypeDetails [] DOC_TYPES_OVERALL = [
        getDoc("DTR", false, false, true),
        getDoc("TIR", false, false, true)]

    private static DocTypeDetails getDoc(String name,
                                         boolean needsTestData = false,
                                         boolean needsComponentInfo = false,
                                         boolean isOverAll = false) {
        return new DocTypeDetails(name, needsTestData, needsComponentInfo, isOverAll)
    }

    List<ProjectFixture> getProjects(DocTypeDetails[] docTypes){
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
                addDocTypes(project as Map, projectsToTest, docTypes)
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

    def addDocTypes(Map project, List projects, DocTypeDetails[] docTypes) {
        docTypes.each { docType ->
            if (project.docsToTest.contains(docType.name)) {
                if (docType.isOverAll) {
                    projects.add(ProjectFixture.getProjectFixtureBuilder(project, docType.name).overall(true).build())
                } else {
                    projects.add(ProjectFixture.getProjectFixtureBuilder(project, docType.name).build())
                }
                if (docType.needsComponentInfo) {
                    addModules(project, docType.name, projects)
                }
            }
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
