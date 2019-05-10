def call(def context) {
  stage('Deploy to Openshift') {
    if (!context.environment) {
      println("Skipping for empty environment ...")
      return
    }

    if (fileExists('openshift')) {
      // Output version to make it easier to diagnose issues.
      sh "tailor version"
      // Ensure that no automatic triggers exist as we rollout manually later on.
      sh "oc --namespace ${context.targetProject} set triggers dc/${context.componentId} --manual"
      // Detect any <project>.env, Tailorfile.<project> or Tailorfile files.
      def paramFile = ''
      if (fileExists("openshift/${context.targetProject}.env")) {
        paramFile = "--param-file ${context.targetProject}.env"
      }
      def tailorFlagsAndArgs = "--non-interactive --namespace ${context.targetProject} --selector app=${context.projectId}-${context.componentId} update --ignore-path bc:/spec/output/imageLabels --ignore-path bc:/spec/strategy/dockerStrategy/buildArgs ${paramFile} --param TAGVERSION=${context.tagversion}"
      if (fileExists("openshift/Tailorfile.${context.targetProject}")) {
        tailorFlagsAndArgs = "--file Tailorfile.${context.targetProject} " + tailorFlagsAndArgs
      } else if (fileExists("openshift/Tailorfile")) {
        tailorFlagsAndArgs = "--file Tailorfile " + tailorFlagsAndArgs
      }
      // Update config via Tailor and rollout deployment.
      sh "cd openshift && tailor ${tailorFlagsAndArgs}"
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
