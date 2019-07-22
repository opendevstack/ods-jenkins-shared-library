import org.ods.service.NexusService

// Store a document in Nexus and return a URL to the document
def call(Map metadata, String repository, String directory, String id, String version, String jenkinsDirectory) {
	
	
	def testfile = zip(zipFile: jenkinsDirectory + "RawData.zip", dir: jenkinsDirectory)
	
//	def zipFileName = "RawData.zip"
//	
//			ZipOutputStream zipFile = new ZipOutputStream(new FileOutputStream(zipFileName))
//			files.each { file ->
//				//check if file
//				if (file.getClass().equals(File.class)){
//					zipFile.putNextEntry(new ZipEntry(file.name))
//					def buffer = new byte[file.size()]
//					file.withInputStream {
//						zipFile.write(buffer, 0, it.read(buffer))
//					}
//					zipFile.closeEntry()
//				}
//			}
//			zipFile.close()
	
    return new NexusService(env.NEXUS_URL, env.NEXUS_USERNAME, env.NEXUS_PASSWORD)
        .storeArtifact(repository, directory, "${id}-${version}.zip", new File(jenkinsDirectory+"RawData.zip").getBytes(), "document/zip")
        .toString()
}

return this
