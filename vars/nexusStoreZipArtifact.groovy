import org.ods.service.NexusService

// Store a zip archive in Nexus and return a URL to the archive
def call(Map metadata, String repository, String directory, String id, String version, String jenkinsWorkSpaceDirectory) {
		
	zip(zipFile: jenkinsWorkSpaceDirectory + "RawData.zip", dir: jenkinsWorkSpaceDirectory)
	
    return new NexusService(env.NEXUS_URL, env.NEXUS_USERNAME, env.NEXUS_PASSWORD)
        .storeArtifact(repository, directory, "${id}-${version}.zip", new File(jenkinsWorkSpaceDirectory+"RawData.zip").getBytes(), "document/zip")
        .toString()
}

return this
