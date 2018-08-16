def call(def context) {
  stage('Create Openshift Environment') {
    if (!context.environment) {
      println("Skipping for empty environment ...")
      return
    }

    if (["test", "dev"].contains(context.environment)) {
      println("Skipping for test/dev environment ...")
      return
    }

    if (environmentExists(context.targetProject)) {
      println("Environment exists already ...")
      return
    }

    if (tooManyEnvironments(context.projectId, context.environmentLimit)) {
      error "Cannot create OC project " +
            "as there are already ${context.environmentLimit} OC projects! " +
            "Please clean up and run the pipeline again."
    }

    createEnvironment(context)
  }
}

private boolean tooManyEnvironments(String projectId, Integer limit) {
  sh(
    returnStdout: true, script: "oc projects | grep '^\\s*${projectId}-' | wc -l"
  ).trim().toInteger() >= limit
}

private boolean environmentExists(String name) {
  def statusCode = sh(
    script:"oc project ${name} &> /dev/null",
    returnStatus: true
  )
  return statusCode == 0
}

private void createEnvironment(def context) {
  println("Environment does not exist yet. Creating now ...")
  withCredentials([usernameColonPassword(credentialsId: context.credentialsId, variable: 'USERPASS')]) {
    sh(script: "mkdir -p oc_migration_scripts/migration_config")

    dir('oc_migration_scripts') {
      sh(script: "curl --fail -s --user ${USERPASS} -G 'https://${context.bitbucketHost}/projects/opendevstack/repos/ods-project-quickstarters/raw/ocp-templates/scripts/export_ocp_project_metadata.sh?at=refs%2Fheads%2Fproduction' -d raw -o export.sh")
      sh(script: "curl --fail -s --user ${USERPASS} -G 'https://${context.bitbucketHost}/projects/opendevstack/repos/ods-project-quickstarters/raw/ocp-templates/scripts/import_ocp_project_metadata.sh?at=refs%2Fheads%2Fproduction' -d raw -o import.sh")

      dir('migration_config') {
        sh(script: "curl --fail -s --user ${USERPASS} -G 'https://${context.bitbucketHost}/projects/opendevstack/repos/ods-configuration/raw/ods-project-quickstarters/ocp-templates/scripts/ocp_project_config_source' -d raw -o ocp_project_config_source")
        sh(script: "curl --fail -s --user ${USERPASS} -G 'https://${context.bitbucketHost}/projects/opendevstack/repos/ods-configuration/raw/ods-project-quickstarters/ocp-templates/scripts/ocp_project_config_target' -d raw -o ocp_project_config_target")
      }

      // Running the export and import scripts
      def admins = ""
      if (context.admins) {
        admins = "-a ${context.admins}"
      }
      def verbose = ""
      if (context.verbose) {
        verbose = "-v true"
      }
      def gitUrl = "https://${USERPASS}@${context.bitbucketHost}/scm/${context.projectId}/${context.projectId}-occonfig-artifacts.git"
      sh(script: "sh export.sh -p ${context.projectId} -h ${context.openshiftHost} -e test -g ${gitUrl} -cpj ${verbose}")
      sh(script: "sh import.sh -h ${context.openshiftHost} -p ${context.projectId} -e test -g ${gitUrl} -n ${context.targetProject} ${admins} ${verbose} --apply true")
    }
    print("House cleaning...")
    sh(script: "rm -r oc_migration_scripts")
    println("Environment created!")
    context.environmentCreated = true
  }
}

return this
