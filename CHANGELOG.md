# Changelog

## [Unreleased]


## [1.0.1] - 2019-01-25

### Changed
- Retry setting build status twice ([#57](https://github.com/opendevstack/ods-jenkins-shared-library/pull/57))

### Fixed
- Serialization error when branch prefix is used in environment mapping ([#58](https://github.com/opendevstack/ods-jenkins-shared-library/pull/58))


## [1.0.0] - 2018-12-03

### Added
- Allow to take full control over the pod containers of the build slave (#35).
- Allow to start build in OpenShift directly from artifacts produced in the Jenkins pipeline. This removes the need to upload the artifacts to Nexus and then download them again in the `Dockerfile`. To use, replace `stageUpdateOpenShiftBuild` with `stageStartOpenShiftBuild` and adapt the `Dockerfile` accordingly. See [#8](https://github.com/opendevstack/ods-jenkins-shared-library/pull/8).
- Set build status for each commit in BitBucket. This allows to require successful builds before PRs can be merged. See [#14](https://github.com/opendevstack/ods-jenkins-shared-library/pull/14).
- Pulling of images can be disabled by setting `podAlwaysPullImage: false`. See [#6](https://github.com/opendevstack/ods-jenkins-shared-library/pull/6).
- Debug mode (#30, #38)

### Changed
- Rework the mapping between branches, pipelines and OpenShift environments. Please see the readme for details of the new semantics.
- Ensure environment variables are present in prepare phase. See [#4](https://github.com/opendevstack/ods-jenkins-shared-library/pull/4).
- Move OCP environment cloning / build triggering to master node (#43)

### Fixed
- Fix and simplify checkout logic in pipeline (#44)

### Removed
- Verbose mode (#30)
- `stageUpdateOpenshiftBuild` - Use `stageStartOpenShiftBuild` instead (#8)


## [0.1.0] - 2018-07-27

Initial release.
