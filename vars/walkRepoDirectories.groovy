// Walk each repository directory and apply a visitor clojure
def call(repos, visitor) {
  repos.each { repo ->
    // Compute the path of the repo inside the workspace
    def path = new File("${WORKSPACE}/.tmp/${repo.name}").path
    dir(path) {
      // Apply the visitor to the repo at path
      visitor(path, repo)
    }
  }
}