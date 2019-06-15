// Load project metadata
def call() {
  def file = new File("${WORKSPACE}/metadata.yml")
  return file.exists() ? new Yaml().load(file.text) : [:]
}
