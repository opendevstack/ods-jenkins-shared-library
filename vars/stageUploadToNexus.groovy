def call(def context) {

  withStage('Upload to Nexus', context) {
    def distFile = "${context.componentId}-${context.tagversion}.tar.gz"
    sh "curl -u ${context.nexusUsername}:${context.nexusPassword} --upload-file ${distFile} ${context.nexusHost}/repository/candidates/${context.groupId.replace('.', '/')}/${context.componentId}/${context.tagversion}/${distFile}"
  }
}

return this
