import org.ods.build.Steps

def call(name, repos, buildScript = null, projectMetadata = null) {
  repos.each { repo ->
    phaseConfig = repo.pipelineConfig.phases ? repo.pipelineConfig.phases[name] : null
    if (phaseConfig) {
      def label = "${repo.name} (${repo.url})"
      def evaluationContext = [:]
      if (projectMetadata) {
        evaluationContext.projectMetadata = projectMetadata
      }
      if (buildScript) {
        evaluationContext.params = script.params
      }
      if (phaseConfig.type == 'Makefile') {
        def task
        if (!evaluationContext.isEmpty()) {
          task = Steps.evaluateArgument(phaseConfig.task, evaluationContext)
        } else {
          task = phaseConfig.task
        }
        dir("${WORKSPACE}/.tmp/${repo.name}") {
          def script = "make ${task}"
          sh script: script, label: label
        }
      } else if (phaseConfig.type == 'ShellScript') {
        dir("${WORKSPACE}/.tmp/${repo.name}") {
          def script = "./scripts/${phaseConfig.script}"
          sh script: script, label: label
        }
      }
    } else if (phaseConfig.type == 'maven') {
      def task
      if (!evaluationContext.isEmpty()) {
        task = Steps.evaluateArgument(phaseConfig.task, evaluationContext)
      } else {
        task = phaseConfig.task
      }
      dir("${WORKSPACE}/.tmp/${repo.name}") {
        def script = "mvn ${task}"
        sh script: script, label: label
      }
    } else {
      // Ignore undefined phases
    }
  }
}