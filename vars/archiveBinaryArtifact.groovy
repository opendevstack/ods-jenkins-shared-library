import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

// Archives a file as pipeline attachment from binary data 
def call(byte[] data, Path dir, String prefix, String suffix) {
    def tmpFile = null

    try {
        tmpFile = Files.createTempFile(
            Files.createDirectories(Paths.get(WORKSPACE, ".tmp", dir.toString())), "${prefix}-", "-${suffix}"
        ) << data
        
        archiveArtifacts artifacts: Paths.get(".tmp", dir.toString(), tmpFile.fileName.toString().toString()).toString()
    } catch (java.io.NotSerializableException e) {
        // FIXME: need to shut up Jenkins' NotSerializableException when using a Unix Path in this context
        // See: https://stackoverflow.com/questions/42670856/notserializableexception-when-using-path-in-jenkins-dsl
    } finally {
        if (tmpFile) {
            Files.delete(tmpFile)
        }
    }
}

return this
