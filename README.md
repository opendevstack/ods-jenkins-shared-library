# Jenkins Shared Library

This library allows to have a minimal Jenkinsfile in each repository by providing all language-agnostic build aspects. The goal is to duplicate as little as possible between repositories and have an easy way to ship updates to all projects.

This shared library supports two different working modes: 
* trunk-based development mode (default) 
* multiple environments mode

# Usage

Load the shared library in your `Jenkinsfile` like this:
```groovy
def final projectId = "hugo"
def final componentId = "be-node-express"
def final credentialsId = "${projectId}-cd-cd-user-with-password"
node {
  sharedLibraryRepository = env.SHARED_LIBRARY_REPOSITORY
}

library identifier: 'ods@latest', retriever: modernSCM(
  [$class: 'GitSCMSource',
   remote: sharedLibraryRepository,
   credentialsId: credentialsId])

odsPipeline(
  image: 'docker-registry.default.svc:5000/cd/slave-maven',
  verbose: true,
  projectId: projectId,
  componentId: componentId
) { context ->
  stage('Build') {
      // your custom code
  }
  stageScanForSonarqube(context)
  stageCreateOpenshiftEnvironment(context)
  stageUpdateOpenshiftBuild(context)
  stageDeployToOpenshift(context)
  stageTriggerAllBuilds(context)
}
```

# Switching to multiple environment modes

The "multiple environment mode" enables the shared library to create new Openshift projects for new branches.

This will only happen if the branch name starts with the suffix `feature/`, `bugfix/`, `hotfix/` or `release/` and is followed by the Jira issue, e.g. `PSP-100`.

Note that there is no check in place that verifies that the jira item is a valid one.

This mode is disabled by default, to enable it, add `autoCreateEnvironment: true` like this:
```
odsPipeline(
  image: 'docker-registry.default.svc:5000/cd/slave-nodejs',
  verbose: true,
  projectId: projectId,
  componentId: componentId,
  notifyNotGreen: false,
  autoCreateEnvironment: true,
  environmentLimit: 10
)
```

In the example above the number of environments to be provisioned in openshift is limited by the configuration `environmentLimit: 10`.

When working with "multiple environment mode" we recommend to add the item BitBucket Team/Project in Jenkins to your project.  

# Customisation

Inside the closure passed to `odsPipeline`, you have full control. Write stages just like you would do in a normal `Jenkinsfile`. You have access to the `context`, which is assembled for you on the master node. The `context` can be influenced by changing the config map passed to `odsPipeline`. Please see `vars/odsPipeline.groovy` for possible options.


# Development
* Assume that repositories are tracking the `latest` tag. Therefore, be careful when you move this tag. Make sure to test your changes first in a repository that you own by pointing to your shared library branch.
* Try to write tests.
* See if you can split things up into classes.
* Keep in mind that you need to access e.g. `sh` via `script.sh`.

# Background
The implementation is largely based on https://www.relaxdiego.com/2018/02/jenkins-on-jenkins-shared-libraries.html. The scripted pipeline syntax was chosen because it is a better fit for a shared library. The declarative pipeline syntax is targeted for newcomers and/or simple pipelines (see https://jenkins.io/doc/book/pipeline/syntax/#scripted-pipeline). If you try to use it e.g. within a Groovy class you'll end up with lots of `script` blocks.
