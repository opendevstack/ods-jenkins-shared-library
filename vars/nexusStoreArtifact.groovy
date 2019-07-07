// Store a document in Nexus and return a URI to the document
def call(String repository, String directory, String id, String version, byte[] document, String contentType) {
    if (!env.NEXUS_SCHEME) {
        error "Error: unable to connect to Nexus: env.NEXUS_SCHEME is undefined"
    }

    if (!env.NEXUS_HOST) {
        error "Error: unable to connect to Nexus: env.NEXUS_HOST is undefined"
    }

    if (!env.NEXUS_PORT) {
        error "Error: unable to connect to Nexus: env.NEXUS_PORT is undefined"
    }

    def result = null
    withCredentials([ usernamePassword(credentialsId: "nexus", usernameVariable: "NEXUS_USERNAME", passwordVariable: "NEXUS_PASSWORD") ]) {
        result = new org.ods.service.NexusService(
            [
                scheme: env.NEXUS_SCHEME,
                host: env.NEXUS_HOST,
                port: Integer.parseInt(env.NEXUS_PORT),
                username: env.NEXUS_USERNAME,
                password: env.NEXUS_PASSWORD
            ]
        ).storeArtifact(repository, directory, "${id}-${version}.pdf", document, contentType)
    }

    return result.toString()
}

return this
