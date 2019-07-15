import org.ods.service.NexusService

// Store a document in Nexus and return a URI to the document
def call(Map metadata, String repository, String directory, String id, String version, byte[] document, String contentType) {
    def result = new NexusService(env.NEXUS_URL, env.NEXUS_USERNAME, env.NEXUS_PASSWORD)
        .storeArtifact(repository, directory, "${id}-${version}.pdf", document, contentType)

    return result.toString()
}

return this
