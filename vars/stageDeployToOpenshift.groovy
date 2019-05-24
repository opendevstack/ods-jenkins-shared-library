def call(def context, def selector) {
  stage('Deploy to Openshift') {
    if (!context.environment) {
      println("Skipping for empty environment ...")
      return
    }

    if (fileExists('openshift')) {
      // Output Tailor version to make it easier to diagnose issues.
      sh "tailor version"
      // Ensure that no automatic triggers exist as we rollout manually later on.
      sh "oc --namespace ${context.targetProject} set triggers dc/${context.componentId} --manual"
      // Detect any <project>.env, .env, Tailorfile.<project> or Tailorfile files.
      def paramFile = ''
      if (fileExists("openshift/${context.targetProject}.env")) {
        paramFile = "--param-file ${context.targetProject}.env"
      } else if (fileExists("openshift/.env")) {
        paramFile = "--param-file .env"
      }
      // Ensure default selector to prevent accidentally targeting the whole project.
      if (!selector) {
        selector = "app=${context.projectId}-${context.componentId}"
      }
      // Do not touch BuildConfig paths that have been touched in stageStartOpenshiftBuild.
      def ignorePaths = '--ignore-path bc:/spec/output/imageLabels --ignore-path bc:/spec/strategy/dockerStrategy/buildArgs'
      // Pass tagversion to set the correct container image.
      def tailorFlagsAndArgs = "--non-interactive --namespace ${context.targetProject} --selector ${selector} update ${ignorePaths} ${paramFile} --param TAGVERSION=${context.tagversion}"
      if (fileExists("openshift/Tailorfile.${context.targetProject}")) {
        tailorFlagsAndArgs = "--file Tailorfile.${context.targetProject} " + tailorFlagsAndArgs
      } else if (fileExists("openshift/Tailorfile")) {
        tailorFlagsAndArgs = "--file Tailorfile " + tailorFlagsAndArgs
      }
      // Update config via Tailor.
      sh "cd openshift && tailor ${tailorFlagsAndArgs}"
      // Rollout latest deployment, and wait until rollout is complete to gather the status code.
      sh "oc --namespace ${context.targetProject} rollout latest dc/${context.componentId} && oc --namespace ${context.targetProject} rollout status dc/${context.componentId} --watch=true"
    } else {
      openshiftTag(
        srcStream: context.componentId,
        srcTag: context.tagversion,
        destStream: context.componentId,
        destTag: "latest",
        namespace: context.targetProject
      )
    }
  }
}

return this
