import org.ods.service.NexusService

// Store a artifact in Nexus and return a URL to the artifact
def call(Map metadata, String repository, String directory, String name, byte[] document, String contentType) {
    return new NexusService(env.NEXUS_URL, env.NEXUS_USERNAME, env.NEXUS_PASSWORD)
        .storeArtifact(repository, directory, name, document, contentType)
        .toString()
}

return this
