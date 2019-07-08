@Grab('org.yaml:snakeyaml:1.24')

import org.yaml.snakeyaml.Yaml

// Load pipeline configurations from the projects' .pipeline-config.yml
def call(List<Map> repos) {
    visitor = { String path, Map repo ->
        def file = new File("${path}/.pipeline-config.yml")
        def data = file.exists() ? new Yaml().load(file.text) : [:]
        repo.pipelineConfig = data
    }

    walkRepoDirectories(repos, visitor)
}

private def walkRepoDirectories(List<Map> repos, Closure visitor) {
    repos.each { repo ->
        // Compute the path of the repo inside the workspace
        def path = "${WORKSPACE}/.tmp/repositories/${repo.name}"
        dir(path) {
            // Apply the visitor to the repo at path
             visitor(path, repo)
        }
    }
}

return this
