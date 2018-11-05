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

library identifier: 'ods-library@production', retriever: modernSCM(
  [$class: 'GitSCMSource',
   remote: sharedLibraryRepository,
   credentialsId: credentialsId])

odsPipeline(
  image: "${dockerRegistry}/cd/jenkins-slave-maven",
  projectId: projectId,
  componentId: componentId
) { context ->
  stage('Build') {
      // custom stage
  }
  stageScanForSonarqube(context) // using a provided stage
}
```


## Workflow

The shared library does not impose which Git workflow you use. Whether you use git-flow, GitHub flow or a custom workflow, it is possible to configure the shared library according to your needs. There are just two settings to control everything: `branchToEnvironmentMapping` and `autoCloneEnvironmentsFromSourceMapping`.

### branchToEnvironmentMapping

Example:
```
branchToEnvironmentMapping: [
  "master": "prod",
  "develop": "dev",
  "hotfix/": "hotfix",
  "*": "review"
]
```

Maps a branch to an environment. There are three ways to reference branches:

* Fixed name (e.g. `master`)
* Prefix (ending with a slash, e.g. `hotfix/`)
* Any branch (`*`)

Matches are made top-to-bottom. For prefixes / any branch, a more specific environment might be selected if:

* the branch contains a ticket ID and a corresponding env exists in OCP. E.g. for mapping `"feature/": "dev"` and branch `feature/foo-123-bar`, the env `dev-123` is selected instead of `dev` if it exists.
* the branch name corresponds to an existing env in OCP. E.g. for mapping `"release/": "rel"` and branch `release/1.0.0`, the env `rel-1.0.0` is selected instead of `rel` if it exists.

### autoCloneEnvironmentsFromSourceMapping

Caution! Cloning environments on-the-fly is an advanced feature and should only be used if you understand OCP well, as there are many moving parts and things can go wrong in multiple places.

Example:
```
autoCloneEnvironmentsFromSourceMapping: [
  "hotfix": "prod",
  "review": "dev"
]
```

Instead of deploying multiple branches to the same environment, individual environments can be created on-the-fly. For example, the mapping `"*": "review"` deploys all branches to the `review` environment. To have one environment per branch / ticket ID, you can add the `review` environment to `autoCloneEnvironmentsFromSourceMapping`, e.g. like this: `"review": "dev"`. This will create individual environments (named e.g. `review-123` or `review-foobar`), each cloned from the `dev` environment.

### Examples

If you use [git-flow](https://jeffkreeftmeijer.com/git-flow/), the following config fits well:
```
branchToEnvironmentMapping: [
  'master': 'prod',
  'develop': 'dev',
  'release/': 'rel',
  'hotfix/': 'hotfix',
  '*': 'preview'
]
// Optionally, configure environments on-the-fly:
autoCloneEnvironmentsFromSourceMapping: [
  'rel': 'dev',
  'hotfix': 'prod',
  'preview': 'dev'
]
```

If you use [GitHub Flow](https://guides.github.com/introduction/flow/), the following config fits well:
```
branchToEnvironmentMapping: [
  'master': 'prod',
  '*': 'preview'
]
// Optionally, configure environments on-the-fly:
autoCloneEnvironmentsFromSourceMapping: [
  'preview': 'prod'
]
```

If you use a custom workflow, the config could look like this:
```
branchToEnvironmentMapping: [
  'production': 'prod',
  'master': 'dev',
  'staging': 'uat'
]
// Optionally, configure environments on-the-fly:
autoCloneEnvironmentsFromSourceMapping: [
  'uat': 'prod'
]
```


## Writing stages

Inside the closure passed to `odsPipeline`, you have full control. Write stages just like you would do in a normal `Jenkinsfile`. You have access to the `context`, which is assembled for you on the master node. The `context` can be influenced by changing the config map passed to `odsPipeline`. Please see `vars/odsPipeline.groovy` for possible options.


## Slave customization

The slave used to build your code can be customized by specifying the image to
use. Further, `podAlwaysPullImage` (defaulting to `true`) can be used to
determine whether this image should be refreshed on each build. The setting
`podVolumes` allows to mount persistent volume claims to the pod (the value is
passed to the `podTemplate` call as `volumes`). To control the container pods
completely, set `podContainers` (which is passed to the `podTemplate` call
as `containers`). See the
[kubernetes-plugin](https://github.com/jenkinsci/kubernetes-plugin)
documentation for possible configuration.


## Versioning

Each `Jenkinsfile` references a Git revsison of this library, e.g.
`library identifier: 'ods-library@production'`. The Git revsison can be a
branch (e.g. `production` or `0.1.x`), a tag (e.g.`0.1.1`) or a specific commit.

By default, each `Jenkinsfile` in `ods-project-quickstarters` on the `master`
branch references the `production` branch of this library. Quickstarters on a
branch point to the corresponding branch of the shared library - for example
a `Jenkinsfile` on branch `0.1.x` points to `0.1.x` of the shared library.


## Development
* Try to write tests.
* See if you can split things up into classes.
* Keep in mind that you need to access e.g. `sh` via `script.sh`.


## Background
The implementation is largely based on https://www.relaxdiego.com/2018/02/jenkins-on-jenkins-shared-libraries.html. The scripted pipeline syntax was chosen because it is a better fit for a shared library. The declarative pipeline syntax is targeted for newcomers and/or simple pipelines (see https://jenkins.io/doc/book/pipeline/syntax/#scripted-pipeline). If you try to use it e.g. within a Groovy class you'll end up with lots of `script` blocks.
