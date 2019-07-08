import org.ods.phase.PipelinePhases

// Execute a named pipeline phase for each repository
def call(String name, List<Set<Map>> repoSets) {
    // In some phases, we can run all repos in parallel
    if (PipelinePhases.ALWAYS_PARALLEL_PHASES.contains(name)) {
        repoSets = [repoSets.flatten() as Set<Map>]
    }

    repoSets.each { group ->
        def steps = group.collectEntries { repo ->
            [
                repo.name,
                {
                    def phaseConfig = repo.pipelineConfig.phases ? repo.pipelineConfig.phases[name] : null
                    if (phaseConfig) {
                        def label = "${repo.name} (${repo.url})"

                        if (phaseConfig.type == 'Makefile') {
                            dir("${WORKSPACE}/.tmp/repositories/${repo.name}") {
                                def script = "make ${phaseConfig.task}"
                                sh script: script, label: label
                            }
                        } else if (phaseConfig.type == 'ShellScript') {
                            dir("${WORKSPACE}/.tmp/repositories/${repo.name}") {
                                def script = "./scripts/${phaseConfig.script}"
                                sh script: script, label: label
                            }
                        }
                    } else {
                        // Ignore undefined phases
                    }
                }
            ]
        }

        parallel steps
    }
}

return this
