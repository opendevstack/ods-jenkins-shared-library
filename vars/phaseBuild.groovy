import org.ods.service.DocGenService
import org.ods.service.JenkinsService
import org.ods.service.JiraService
import org.ods.service.NexusService
import org.ods.service.ServiceRegistry
import org.ods.usecase.JUnitTestReportsUseCase
import org.ods.usecase.JiraUseCase
import org.ods.usecase.LeVaDocumentUseCase
import org.ods.usecase.SonarQubeUseCase
import org.ods.util.MROPipelineUtil
import org.ods.util.PipelineUtil

def call(Map project, List<Set<Map>> repos) {
    def docGen  = ServiceRegistry.instance.get(DocGenService.class.name)
    def jenkins = ServiceRegistry.instance.get(JenkinsService.class.name)
    def jira    = ServiceRegistry.instance.get(JiraUseCase.class.name)
    def junit   = ServiceRegistry.instance.get(JUnitTestReportsUseCase.class.name)
    def levaDoc = ServiceRegistry.instance.get(LeVaDocumentUseCase.class.name)
    def nexus   = ServiceRegistry.instance.get(NexusService.class.name)
    def sq      = ServiceRegistry.instance.get(SonarQubeUseCase.class.name)
    def util    = ServiceRegistry.instance.get(PipelineUtil.class.name)

    if (LeVaDocumentUseCase.appliesToProject(LeVaDocumentUseCase.DocumentTypes.SCP, project)) {
        echo "Creating and archiving a Software Development (Coding and Code Review) Plan for project '${project.id}'"
        levaDoc.createSCP(project)
    }

    if (LeVaDocumentUseCase.appliesToProject(LeVaDocumentUseCase.DocumentTypes.DTP, project)) {
        echo "Creating and archiving a Software Development Testing Plan for project '${project.id}'"
        levaDoc.createDTP(project)
    }

    def groups = util.prepareExecutePhaseForReposNamedJob(MROPipelineUtil.PipelinePhases.BUILD, repos, null) { steps, repo ->
        // Software Development (Coding and Code Review) Report
        if (LeVaDocumentUseCase.appliesToRepo(LeVaDocumentUseCase.DocumentTypes.SCR, repo)) {
            def sqReportsPath = "sonarqube/${repo.id}"

            echo "Collecting SonarQube Report for repo '${repo.id}'"
            def sqReportsStashName = "scrr-report-${repo.id}-${steps.env.BUILD_ID}"
            def hasStashedSonarQubeReports = jenkins.unstashFilesIntoPath(sqReportsStashName, "${steps.env.WORKSPACE}/${sqReportsPath}", "SonarQube Report")
            if (!hasStashedSonarQubeReports) {
                throw new RuntimeException("Error: unable to unstash SonarQube reports for repo '${repo.id}' from stash '${sqReportsStashName}'.")
            }

            // Load SonarQube report files from path
            def sqReportFiles = sq.loadReportsFromPath("${steps.env.WORKSPACE}/${sqReportsPath}")
            if (sqReportFiles.isEmpty()) {
                throw new RuntimeException("Error: unable to load SonarQube reports for repo '${repo.id}' from path '${steps.env.WORKSPACE}/${sqReportsPath}'.")
            }

            echo "Creating and archiving a Software Development (Coding and Code Review) Report for repo '${repo.id}'"
            levaDoc.createSCR(project, repo, sqReportFiles.first())
        }

        // Software Development Testing Report
        if (LeVaDocumentUseCase.appliesToRepo(LeVaDocumentUseCase.DocumentTypes.DTR, repo)) {
            def testReportsPath = "junit/${repo.id}"

            echo "Collecting JUnit XML Reports for ${repo.id}"
            def testReportsStashName = "test-reports-junit-xml-${repo.id}-${steps.env.BUILD_ID}"
            def testReportsUnstashPath = "${steps.env.WORKSPACE}/${testReportsPath}"
            def hasStashedTestReports = jenkins.unstashFilesIntoPath(testReportsStashName, testReportsUnstashPath, "JUnit XML Report")
            if (!hasStashedTestReports) {
                throw new RuntimeException("Error: unable to unstash JUnit XML reports for repo '${repo.id}' from stash '${testReportsStashName}'.")
            }

            // Load JUnit test report files from path
            def testReportFiles = junit.loadTestReportsFromPath("${steps.env.WORKSPACE}/${testReportsPath}")

            // Parse JUnit test report files into a report
            def testResults = junit.parseTestReportFiles(testReportFiles)

            echo "Creating and archiving a Software Development Testing Report for repo '${repo.id}'"
            levaDoc.createDTR(project, repo, testResults, testReportFiles)

            // Report test results to corresponding test cases in Jira
            jira.reportTestResultsForComponent(project.id, "Technology_${repo.id}", testResults)            
        }
    }

    // Execute phase for groups of independent repos
    groups.each { group ->
        parallel(group)
    }

    if (LeVaDocumentUseCase.appliesToProject(LeVaDocumentUseCase.DocumentTypes.SCR, project)) {
        echo "Creating and archiving an overall Software Development (Coding and Code Review) Report for project '${project.id}'"
        levaDoc.createOverallSCR(project)
    }

    if (LeVaDocumentUseCase.appliesToProject(LeVaDocumentUseCase.DocumentTypes.DTR, project)) {
        echo "Creating and archiving an overall Software Development Testing Report for project '${project.id}'"
        levaDoc.createOverallDTR(project)
    }
}

return this
