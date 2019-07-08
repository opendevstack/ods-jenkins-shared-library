// Archives a file as pipeline attachment from binary data
def call(byte[] data, String dirname, String prefix, String suffix) {
    def tmpFile = null

    try {
        // Create a directory in the current workspace
        def dir = new File("${WORKSPACE}/${dirname}")
        dir.mkdirs()

        // Create a temporary file containing data
        tmpFile = File.createTempFile("${prefix}-", "-${suffix}", dir) << data

        archiveArtifacts artifacts: "${dirname}/${tmpFile.name}"
    } finally {
        if (tmpFile) {
            tmpFile.delete()
        }
    }
}

return this
