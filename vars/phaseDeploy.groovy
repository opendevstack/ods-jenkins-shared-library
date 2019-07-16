import org.ods.phase.PipelinePhases
import org.ods.util.MultiRepoOrchestrationPipelineUtil

def call(Map metadata, List<Set<Map>> repos) {
    // Execute phase for each repository
    def util = new MultiRepoOrchestrationPipelineUtil(this)
    util.prepareExecutePhaseForReposNamedJob(PipelinePhases.DEPLOY_PHASE, repos)
        .each { group ->
            parallel(group)
        }

    // Create and store a demo InstallationReport
    demoCreateInstallationReport(metadata)
}

return this
