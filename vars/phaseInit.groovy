import org.ods.util.MultiRepoOrchestrationPipelineUtil

def call() {
    def util = new MultiRepoOrchestrationPipelineUtil(this)

    def project = util.readProjectMetadata()
    def repos = project.repositories

    // Checkout repositories into the workspace
    parallel(util.prepareCheckoutReposNamedJobs(repos))

    // Load pipeline configs from each repo's .pipeline-config.yml
    util.readPipelineConfigs(repos)

    // Compute groups of repository configs for convenient parallelization
    repos = util.computeRepoGroups(repos)

    return [
        project: project,
        repos: repos
    ]
}

return this
