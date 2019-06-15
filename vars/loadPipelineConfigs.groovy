// Load pipeline configurations from each repository
@Grab('org.yaml:snakeyaml:1.24')
import org.yaml.snakeyaml.Yaml

def call(repos) {
  visitor = { path, repo ->
    def file = new File("${path}/.pipeline-config.yml")
    def data = file.exists() ? new Yaml().load(file.text) : [:]
    repo.pipelineConfig = data
  }

  walkRepoDirectories(repos, visitor) 
}