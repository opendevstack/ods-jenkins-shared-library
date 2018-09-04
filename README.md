# Jenkins Shared Library

This library allows to have a minimal Jenkinsfile in each repository by providing all language-agnostic build aspects. The goal is to duplicate as little as possible between repositories and have an easy way to ship updates to all projects.


## Usage

Load the shared library in your `Jenkinsfile` like this:
```groovy
def final projectId = "hugo"
def final componentId = "be-node-express"
def final credentialsId = "${projectId}-cd-cd-user-with-password"
def sharedLibraryRepository
def dockerRegistry
node {
  sharedLibraryRepository = env.SHARED_LIBRARY_REPOSITORY
  dockerRegistry = env.DOCKER_REGISTRY
}

library identifier: 'ods-library@1-latest', retriever: modernSCM(
  [$class: 'GitSCMSource',
   remote: sharedLibraryRepository,
   credentialsId: credentialsId])

odsPipeline(
  image: "${dockerRegistry}/cd/jenkins-slave-maven",
  projectId: projectId,
  componentId: componentId,
  verbose: true,
) { context ->
  stage('Build') {
      // custom stage
  }
  stageScanForSonarqube(context) // using a provided stage
}
```


## Workflow

When using the shared library, you have the choice between two different workflows:

### [GitHub Flow](https://guides.github.com/introduction/flow/): Focus on continuous deployment (default)

`master` is the main branch and feature/bugfix branches target this branch. Each pull request is promoted to production immediately, with no integration branch in between. This style is useful for fast development, and/or when a good review system is in place.

The following mapping between branches and OpenShift environments applies (top to bottom):

| Branch | Environment |
| --- | --- |
| `master` | `prod` |
| `*`, e.g. `feature/foo-123-bar` | If branch contains a ticket ID (`.*-([0-9]*)-.*`), attempt to deploy to `review-123`. If branch does not contain a ticket ID, or `review-123` does not exist and auto-creation is not enabled, attempt to deploy to `review` instead. If `review` does not exist either, no deployment happens. |

Please note that GitHub Flow recommends to deploy the PR to production before merging it into master. This step is out of scope of the shared library, and has to be performed via different means if desired.

### [git-flow](https://jeffkreeftmeijer.com/git-flow/): Focus on continuous integration

`develop` is the integration branch, and feature/bugfix branches target this branch. `master` is the production branch, typically updated at the end of every sprint. Additionally, there are `release-x.x.x` branches (forked from `develop`) to ensure quality before the release is promoted to production, and `hotfix/x` branches (forked from `master`) for small urgent fixes.

The following mapping between branches and OpenShift environments applies (top to bottom):

| Branch | Environment |
| --- | --- |
| `master` | `prod` |
| `develop` | `dev` |
| `hotfix/*`, e.g. `hotfix/foo-123-bar` | If branch contains a ticket ID (`.*-([0-9]*)-.*`), attempt to deploy to `hotfix-123`. If branch does not contain a ticket ID, or `hotfix-123` does not exist and auto-creation is not enabled, attempt to deploy to `hotfix` instead. If `hotfix` does not exist either, no deployment happens. |
| `release/*`, e.g. `release/1.0.0` | Attempt to deploy to `release-1.0.0`. If that does not exist and auto-creation is not enabled, attempt to deploy to `release` instead. If `release` does not exist either, no deployment happens. |
| `.*`, e.g. `feature/foo-123-bar` | If branch contains a ticket ID (`.*-([0-9]*)-.*`), attempt to deploy to `review-123`. If branch does not contain a ticket ID, or `review-123` does not exist and auto-creation is not enabled, attempt to deploy to `review` instead. If `review` does not exist either, no deployment happens. |

### Customization

Both workflows can be customized with the following options:

* `autoCreateReviewEnvironment`: Creates `review-x` environments on the fly (off by default).
* `defaultReviewEnvironment`: Defaults to `review` (not created by Jenkins)
* `productionBranch`: Defaults to `master`
* `productionEnvironment`: Defaults to `prod`

Additionally, `workflow: "git-flow"` can be customized further:

* `autoCreateReleaseEnvironment`: Creates `release-x` environments on the fly (off by default).
* `autoCreateHotfixEnvironment`: Creates `hotfix-x` environments on the fly (off by default).
* `defaultHotfixEnvironment`: Defaults to `hotfix` (not created by Jenkins)
* `defaultReleaseEnvironment`: Defaults to `release` (not created by Jenkins)
* `developmentBranch`: Defaults to `develop`
* `developmentEnvironment`: Defaults to `dev`

Note that auto-creation of environments works by cloning a previous environment. For GitHub Flow, review environments are cloned from `prod`. For git-flow, review and release environments are cloned from `dev`, hotfix is cloned from `prod`.


## Writing stages

Inside the closure passed to `odsPipeline`, you have full control. Write stages just like you would do in a normal `Jenkinsfile`. You have access to the `context`, which is assembled for you on the master node. The `context` can be influenced by changing the config map passed to `odsPipeline`. Please see `vars/odsPipeline.groovy` for possible options.


## Development
* Assume that repositories are tracking `x-latest` tags. Therefore, be careful when you move those tags. Make sure to test your changes first in a repository that you own by pointing to your shared library branch.
* Try to write tests.
* See if you can split things up into classes.
* Keep in mind that you need to access e.g. `sh` via `script.sh`.


## Background
The implementation is largely based on https://www.relaxdiego.com/2018/02/jenkins-on-jenkins-shared-libraries.html. The scripted pipeline syntax was chosen because it is a better fit for a shared library. The declarative pipeline syntax is targeted for newcomers and/or simple pipelines (see https://jenkins.io/doc/book/pipeline/syntax/#scripted-pipeline). If you try to use it e.g. within a Groovy class you'll end up with lots of `script` blocks.
