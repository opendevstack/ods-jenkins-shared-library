# Changelog

## Unreleased

### Breaking Changes

* [Task] Build all branches apart from `testProjectBranch` in the `-dev` pipeline. See [#10](https://github.com/opendevstack/ods-jenkins-shared-library/pull/10).

### Non-Breaking Changes

* [Feature] Allow to start build in OpenShift directly from artifacts produced in the Jenkins pipeline. This removes the need to upload the artifacts to Nexus and then download them again in the `Dockerfile`. To use, replace `stageUpdateOpenShiftBuild` with `stageStartOpenShiftBuild` and adapt the `Dockerfile` accordingly. See [#8](https://github.com/opendevstack/ods-jenkins-shared-library/pull/8).
* [Feature] Pulling of images can be disabled by setting `podAlwaysPullImage: false`. See [#6](https://github.com/opendevstack/ods-jenkins-shared-library/pull/6).
* [Feature] Allow to deploy to custom env when it exists, regardless of whether `autoCreateEnvironment` is turned on. See [#10](https://github.com/opendevstack/ods-jenkins-shared-library/pull/10).
* [Bugfix] Use `testProjectBranch` instead of `master` in all places, allowing to set a different main branch. See [#10](https://github.com/opendevstack/ods-jenkins-shared-library/pull/10).
* [Task] Ensure environment variables are present in prepare phase. See [#4](https://github.com/opendevstack/ods-jenkins-shared-library/pull/4).

## 0.1.0 (2018-07-27)

Initial release.
