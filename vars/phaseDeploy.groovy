import org.ods.util.MultiRepoOrchestrationPipelineUtil

def call(Map metadata, List<Set<Map>> repos) {
    def util = new MultiRepoOrchestrationPipelineUtil(this)
    util.prepareExecutePhaseForReposNamedJob('Deploy', repos)
        .each { group ->
            parallel(group)
        }

    // Create and store a demo InstallationReport
    demoCreateInstallationReport(metadata)
}

return this
