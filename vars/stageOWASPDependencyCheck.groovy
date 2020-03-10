def call(def context) {

  withStage(context, 'OWASP Dependency Check', context) {

    if (context.dependencyCheckBranch != '*' && context.dependencyCheckBranch != context.gitBranch) {
      echo "Stage is skipped since this build is for a branch that was not specified in the dependencyCheckBranch property."
    } else {

      sh "dependency-check " +
          "--project ${context.projectId}-${context.componentId} " +
          "--scan . " +
          "--data /mnt/owasp-dependency-check/dependency-check-data " +
          "--format ALL"
    }
  }
}

return this
