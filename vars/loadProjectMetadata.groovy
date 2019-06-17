@Grab('org.yaml:snakeyaml:1.24')
import org.yaml.snakeyaml.Yaml

// Load project metadata
def call() {
  def file = new File("${WORKSPACE}/metadata.yml")
  return file.exists() ? new Yaml().load(file.text) : [:]
}
