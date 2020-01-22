boolean enabled() {
  return fileExists('openshift')
}

String getVersion() {
  return sh(
    script: "tailor version",
    label: "Print Tailor version",
    returnStdout: true
  ).trim()
}

boolean privateKeyExists(def privateKeyCredentialsId) {
  try {
    withCredentials([
      sshUserPrivateKey(
        credentialsId: privateKeyCredentialsId,
        keyFileVariable: 'irrelevant'
      )
    ]) {
      true
    }
  } catch (_) {
    false
  }
}

void execBuildUpdate(def context, String selector = '') {
  def argsAndFlags = assembleUpdateArgsAndFlags(
    context,
    ['buildconfig', 'imagestream'], // included
    [], // excluded
    selector
  )
  execUpdate(context, argsAndFlags)
}

void execRunUpdate(def context, String selector = '') {
  def argsAndFlags = assembleUpdateArgsAndFlags(
    context,
    [], // included
    ['buildconfig', 'imagestream'], // excluded
    selector
  )
  execUpdate(context, argsAndFlags)
}

void execUpdate(def context, String argsAndFlags) {
  dir('openshift') {
    // Point to private key if one exists
    def privateKeyCredentialsId = "${context.projectId}-cd-tailor-private-key"
    if (privateKeyExists(privateKeyCredentialsId)) {
      withCredentials([
        sshUserPrivateKey(
          credentialsId: privateKeyCredentialsId,
          keyFileVariable: 'keyfile'
        )
      ]) {
        sh(
          script: "tailor ${argsAndFlags} --private-key ${keyfile}",
          label: "Execute Tailor update using private key"
        )
      }
    } else {
      sh(
        script: "tailor ${argsAndFlags}",
        label: "Execute Tailor update"
      )
    }
  }
}

String assembleUpdateArgsAndFlags(def context, ArrayList<String> include, ArrayList<String> exclude, String selector) {
  // Detect any <targetProject>.env or .env files.
  def paramFile = ''
  if (fileExists("openshift/${context.targetProject}.env")) {
    paramFile = "--param-file ${context.targetProject}.env"
  } else if (fileExists("openshift/.env")) {
    paramFile = "--param-file .env"
  }
  // Use a default selector to prevent accidentally targeting the whole project.
  if (!selector) {
    selector = "app=${context.projectId}-${context.componentId}"
  }
  // Do not touch BuildConfig paths that have been touched in stageStartOpenshiftBuild.
  def ignorePaths = ''
  if (include.contains('buildconfig') || !exclude.contains('buildconfig')) {
    ignorePaths = '--ignore-path bc:/spec/output/imageLabels --ignore-path bc:/spec/output/to/name --ignore-path bc:/spec/strategy/dockerStrategy/buildArgs'
  }
  // Handle included and excluded resource kinds.
  def kindsArg = ''
  if (include) {
    kindsArg = include.join(',')
  }
  def excludeFlag = ''
  if (exclude) {
    excludeFlag = "--exclude ${exclude.join(',')}"
  }
  // Pass tagversion to set the correct container image.
  def tailorFlagsAndArgs = "--non-interactive --namespace ${context.targetProject} --selector ${selector} ${excludeFlag} update ${kindsArg} ${ignorePaths} ${paramFile} --param TAGVERSION=${context.tagversion}"
  // Detect any Tailorfile.<targetProject> or Tailorfile files.
  if (fileExists("openshift/Tailorfile.${context.targetProject}")) {
    tailorFlagsAndArgs = "--file Tailorfile.${context.targetProject} ${tailorFlagsAndArgs}"
  } else if (fileExists("openshift/Tailorfile")) {
    tailorFlagsAndArgs = "--file Tailorfile ${tailorFlagsAndArgs}"
  }
  // Enable debug mode if required.
  if (context.debug) {
    tailorFlagsAndArgs = "--debug ${tailorFlagsAndArgs}"
  }
  return tailorFlagsAndArgs
}
