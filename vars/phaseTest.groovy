import org.ods.util.MultiRepoOrchestrationPipelineUtil

def call(Map metadata, List<Set<Map>> repos) {
    def util = new MultiRepoOrchestrationPipelineUtil(this)
    util.prepareExecutePhaseForReposNamedJob('Test', repos)
        .each { group ->
            parallel(group)
        }
}

return this
