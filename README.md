# Multi-Repo Orchestration (MRO) Jenkins Shared Library

A Jenkins shared library to support the orchestration of multiple repositories into a live application by the *Release Manager* quickstarter.

## Usage

With *OpenDevStack*, the library comes pre-installed in your project's Jenkins instance and comes readily included inside your release manager component's `Jenkinsfile` via `@Library('ods-mro-jenkins-shared-library@production')`.

## Configuration

### Automated Resolution of Dependencies

The library automatically resolves dependencies between repositories to be orchestrated so that they can be delivered in the correct order. Currently, repositories that want to be orchestrated need to be added to the `repositories` list inside a release manager component's `metadata.yml`:

```
id: PHOENIX
name: Project Phoenix

repositories:
  - id: A
    url: https://github.com/my-org/my-repo-A.git
    branch: master
  - id: B
    name: my-repo-B
    branch: master
  - id: C
```

If a named repository wants to announce a dependency on another repo, the dependency needs to be listed in that repository's `release-manager.yml`, simply by referring to its `repo.id` as defined in `metadata.yml`:

```
dependencies:
  - A
```

### Automated Resolution of Repository Git URL

If no `url` parameter is provided for a repository configuration in a release manager component's `metadata.yml`, the library will attempt to resolve it based on the component's *origin remote URL* and one of the following:

1) If the `name` parameter is provided, and not empty, the last path part of the URL is resolved to `${repo-name}.git`.
2) If no `name` parameter is provided, the last path part of the URL is resolved to `${project-id}-${repo-id}.git` (which is the repository name pattern used with *OpenDevStack*). Here `${project-id}` refers to the lowercase value of the top-level `id` attribute in `metadata.yml`.

#### Example: Resolve Git URL for Repository 'B'

```
id: PHOENIX
name: Project Phoenix

repositories:
  - id: B
    name: my-repo-B
    branch: master
```

Assuming your release manager component's origin at `https://github.com/my-org/my-pipeline.git` in this example, the Git URL for repository `B` will resolve to `https://github.com/my-org/my-repo-B.git`, based on the value in `repositories[0].name`.

#### Example: Resolve Git URL for Repository 'C'

```
id: PHOENIX
name: Project Phoenix

repositories:
  - id: C
```

Assuming your release manager component's origin at `https://github.com/my-org/my-pipeline.git` in this example, the Git URL for repository `C` will resolve to `https://github.com/my-org/phoenix-C.git`, based on the values in `id` and `repositories[0].name`.

### Automated Resolution of Repository Branch

If no `branch` parameter is provided for a repository, `master` will be assumed.

### Automated Parallelization of Repositories

Instead of merely resolving repositories into a strictly sequential execution model, our library automatically understands which repositories form independent groups and can run in parallel for best time-to-feedback and time-to-delivery.

### Automated Generation of Compliance Documents

The library automatically generates Lean Validation (LeVA) compliance reports based on data in your Jira project, as well as data generated along the automated build, deploy, test, and release process by the release manager component.

*Note:* when you configure a Jira service in the release manager component's `metadata.yml`, our library expects your Jira project (identified by `id`) to follow a specific structure. If your Jira project has not been set up by *OpenDevStack* lately, your structure will most likely be different. While we plan to support custom Jira setups in the future, you may disable the dependency on the Jira service entirely, as shown in the following example:

```
services:
  bitbucket:
    credentials:
      id: my-bitbucket-credentials
#  jira:
#    credentials:
#      id: my-jira-credentials
  nexus:
    repository:
      name: leva-documentation
```

In this case, the library will fall back to the document chapter templates located in your release manager component's `docs` folder. Therein, you can provide chapter data to be loaded into the supported compliance documents.

### Automated Cloning of Environments

If you want your *target environment* to be created from an existing *source environment* such as `dev` or `test` on the fly, you need to provide the `environment` and `sourceEnvironmentToClone` environment variables to your Jenkins run, respectively. Their values will be combined with your project ID in the form `${project-id}-${environment}` to create the project (namespace) name in your OpenShift cluster.

## Requirements

- `git` - Git CLI
- `oc` - OpenShift CLI
