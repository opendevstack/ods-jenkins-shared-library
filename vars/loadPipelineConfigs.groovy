// Load pipeline configurations from each repository
@Grab('org.yaml:snakeyaml:1.24')
import org.yaml.snakeyaml.Yaml

private def walkRepoDirectories(repos, visitor) {
  repos.each { repo ->
    // Compute the path of the repo inside the workspace
    def path = new File("${WORKSPACE}/.tmp/${repo.name}").path
    dir(path) {
      // Apply the visitor to the repo at path
      visitor(path, repo)
    }
  }
}

def call(repos) {
  visitor = { path, repo ->
    def file = new File("${path}/.pipeline-config.yml")
    def data = file.exists() ? new Yaml().load(file.text) : [:]
    repo.pipelineConfig = data
  }

  walkRepoDirectories(repos, visitor) 
}