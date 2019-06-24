@Grab('org.yaml:snakeyaml:1.24')

import java.nio.file.Paths

import org.yaml.snakeyaml.Yaml

// Load metadata from metadata.yml
def call() {
    def file = Paths.get(WORKSPACE, "metadata.yml").toFile()
    return file.exists() ? new Yaml().load(file.text) : [:]
}
