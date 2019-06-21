def call(name, repos) {
  repos.each { repo ->
    phaseConfig = repo.pipelineConfig.phases ? repo.pipelineConfig.phases[name] : null
    if (phaseConfig) {
      def label = "${repo.name} (${repo.url})"

      if (phaseConfig.type == 'Makefile') {
        dir("${WORKSPACE}/.tmp/${repo.name}") {
          def script = "make ${phaseConfig.task}"
          sh script: script, label: label
        }
      } else if (phaseConfig.type == 'ShellScript') {
        dir("${WORKSPACE}/.tmp/${repo.name}") {
          def script = "./scripts/${phaseConfig.script}"
          sh script: script, label: label
        }
      }
    } else {
      // Ignore undefined phases
    }
  }
}