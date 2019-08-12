# Multi-Repo Orchestration (MRO) Jenkins Shared Library

A Jenkins shared library to support the orchestration of multiple repositories into a live application by the MRO Jenkins Pipeline.

See the mro repository's `metadata.yml` for repositories included into the MRO pipeline and their configuration (e.g. w/o URL and branch)

## Usage and configuration

### Configuration & automated resolution of dependencies

The pipeline automatically resolves dependencies between repositories to be orchestrated so that they can be delivered in the correct order. Currently, repositories that want to be orchestrated need to be added to the `repositories` list inside the `metadata.yml`.

Example `metadata.yml` :

```
repositories:
  - name: my-repo-A
    url: https://github.com/my-org/my-repo-A.git
    branch: refs/heads/master
  - name: my-repo-B
    url: https://github.com/my-org/my-repo-B.git
    branch: refs/heads/master
  - name: my-repo-C
```

#### Repository location / URL resolution

If no `url` is provided for a repository (see `my-repo-C`) the library will construct it following the pattern `git remote config` of the current pipeline repository / `PROJECT-ID`-`repository name`.git. 

Example:
```
current repository url: https://cd_user@bitbucket/scm/PLTFMDEV/app-pipeline.git

calculated repo url: https://cd_user@bitbucket/scm/PLTFMDEV/<PROJECT_ID>-my-repo-C.git which is based on the standard pattern ODS creates repository names.
```
#### Branch resolution

If no `branch` is provided, `master` will be defaulted to

#### Dependency management / resolution

If a named repository wants to announce a dependency on another project to the pipeline, the dependency (equal to  
`metadata.yml` variable `repositories / - repo name`) needs to be listed in that repository's `.pipeline-config.yml`. Example (`.pipeline-config.yml`):

```
dependencies:
  - my-repo-A
```

### Collection of test results for generation of Development Test Reports

#### Default test (results) location

The MRO shared library expects unit test results (as XML files) in `build/test-results/test`. 
If a language outputs those xml unit results somewhere else, one can provide a new `config` attribute, named `testResults` in the project's `JenkinsFile`. Example (`JenkinsFile`):

```
odsPipeline(
  image: "${dockerRegistry}/cd/jenkins-slave-maven",
  ...
  testResults : 'build/test-results/test'
) { context ->
  ... 
}
```
### Automated Parallelization of Repositories

Instead of merely resolving repositories into a strictly sequential execution model, our algorithm automatically understands which repositories are independent and can run in parallel for best time-to-feedback and time-to-delivery.

