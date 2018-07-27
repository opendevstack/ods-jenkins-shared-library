def call(def context) {
  if (context.testProjectBranch == context.gitBranch) {
    stage("OWASP Dependency Check") {
      sh "dependency-check " +
              "--project ${context.projectId}-${context.componentId} " +
              "--scan . " +
              "--data /mnt/owasp-dependency-check/dependency-check-data " +
              "--format ALL"
    }
  }
}

return this
