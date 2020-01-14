def call(def context) {
  stage('Upload to Nexus') {
    def distFile = "${context.componentId}-${context.tagversion}.tar.gz"
    sh "curl -u ${context.nexusUsername}:${context.nexusPassword} --upload-file ${distFile} ${context.nexusUrl}/repository/candidates/${context.groupId.replace('.', '/')}/${context.componentId}/${context.tagversion}/${distFile}"
  }
}

return this
