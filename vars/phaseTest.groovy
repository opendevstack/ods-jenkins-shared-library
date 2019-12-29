import org.ods.service.JenkinsService
import org.ods.service.ServiceRegistry
import org.ods.usecase.LeVaDocumentUseCase
import org.ods.usecase.SonarQubeUseCase
import org.ods.util.MROPipelineUtil
import org.ods.util.PipelineUtil

def call(Map project, List<Set<Map>> repos) {
    def jenkins = ServiceRegistry.instance.get(JenkinsService.class.name)
    def levaDoc = ServiceRegistry.instance.get(LeVaDocumentUseCase.class.name)
    def sq      = ServiceRegistry.instance.get(SonarQubeUseCase.class.name)
    def util    = ServiceRegistry.instance.get(PipelineUtil.class.name)

    def phase = MROPipelineUtil.PipelinePhases.TEST

    def postExecute = { steps, repo ->
        // Software Development (Coding and Code Review) Report
        if (LeVaDocumentUseCase.appliesToRepo(repo, LeVaDocumentUseCase.DocumentTypes.SCR, phase)) {
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
    }

    // Execute phase for each repository
    util.prepareExecutePhaseForReposNamedJob(phase, repos, null, postExecute)
        .each { group ->
            parallel(group)
        }

    if (LeVaDocumentUseCase.appliesToProject(project, LeVaDocumentUseCase.DocumentTypes.SCR, phase)) {
        echo "Creating and archiving an overall Software Development (Coding and Code Review) Report for project '${project.id}'"
        levaDoc.createOverallSCR(project)
    }
}

return this
