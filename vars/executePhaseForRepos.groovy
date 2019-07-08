// Execute a named pipeline phase for each repository
def call(String name, List<Set<Map>> repos) {
    new org.ods.util.MultiRepoOrchestrationPipelineUtil(this)
        .prepareExecutePhaseForReposNamedJob(name, repos)
        .each { group ->
            parallel(group)
        }
}

return this
