import java.nio.file.Paths

// Execute a named pipeline phase for each repository
def call(String name, List<List<Map>> repoGroups) {
    repoGroups.each { group ->
        def steps = group.collectEntries { repo ->
            [
                repo.name,
                {
                    phaseConfig = repo.pipelineConfig.phases ? repo.pipelineConfig.phases[name] : null
                    if (phaseConfig) {
                        def label = "${repo.name} (${repo.url})"

                        if (phaseConfig.type == 'Makefile') {
                            dir(Paths.get(WORKSPACE, ".tmp", "repositories", repo.name).toString()) {
                                def script = "make ${phaseConfig.task}"
                                sh script: script, label: label
                            }
                        } else if (phaseConfig.type == 'ShellScript') {
                            dir(Paths.get(WORKSPACE, ".tmp", "repositories", repo.name).toString()) {
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
