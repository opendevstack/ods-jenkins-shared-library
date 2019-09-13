import org.ods.service.JenkinsService
import org.ods.service.ServiceRegistry
import org.ods.usecase.SonarQubeUseCase
import org.ods.util.MROPipelineUtil
import org.ods.util.PipelineUtil

def call(Map project, List<Set<Map>> repos) {
    def jenkins = ServiceRegistry.instance.get(JenkinsService.class.name)
    def util = ServiceRegistry.instance.get(PipelineUtil.class.name)
    def sq = ServiceRegistry.instance.get(SonarQubeUseCase.class.name)

    def groups = util.prepareExecutePhaseForReposNamedJob(MROPipelineUtil.PipelinePhases.BUILD, repos, null) { script, repo ->
        // TODO: ODS error handling
        def scrrReportsPath = "sonarqube/${repo.id}"

        // Unstash SCRR reports into path
        echo "Collecting SCRR Reports for repo '${repo.id}'"
        def hasStashedSCRRReports = jenkins.unstashFilesIntoPath("scrr-report-${repo.id}-${script.env.BUILD_ID}", "${script.WORKSPACE}/${scrrReportsPath}", "SCRR Report")

        if (hasStashedSCRRReports) {
            // Load SCRR report files from path
            def scrrReportFiles = sq.loadSCRRReportsFromPath("${script.WORKSPACE}/${scrrReportsPath}")

            if (!scrrReportFiles.isEmpty()) {
                // Store the SCRR Report in Nexus
                echo "Uploading an SCRR Report for repo '${repo.id}'"
                repo.SCRR = sq.uploadSCRRReportToNexus(script.env.RELEASE_PARAM_VERSION, project, repo, "SCRR", scrrReportFiles.first())
            }
        }
    }
    
    // Execute phase for groups of independent repos
    groups.each { group ->
        parallel(group)
    }
}

return this
