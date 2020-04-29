def call(def context) {

    withStage('OWASP Dependency Check', context) {

        if (context.dependencyCheckBranch != '*' && context.dependencyCheckBranch != context.gitBranch) {
            echo "Skipping as branch is not specified in 'dependencyCheckBranch'."
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
