# Changelog

## Unreleased

### Added
- Merge ([ods-mro-shared-library](https://github.com/opendevstack/ods-mro-jenkins-shared-library)) into this library, and consolidate services ([#271](https://github.com/opendevstack/ods-jenkins-shared-library/issues/271))
- Introduce CodenArc for scanning of the shared lib ([#290](https://github.com/opendevstack/ods-jenkins-shared-library/issues/290))
- Central openshift namespace is NOT fixed to 'CD' anymore, it's now configurable ([#311](https://github.com/opendevstack/ods-jenkins-shared-library/issues/311))
- Support for mono-repos with multiple containers ([#229](https://github.com/opendevstack/ods-jenkins-shared-library/issues/229))
- Add harmonized stage names based on the new library layout ([#215](https://github.com/opendevstack/ods-jenkins-shared-library/issues/215))
- Allow configuration of docker context directory ([#181](https://github.com/opendevstack/ods-jenkins-shared-library/issues/181))

### Changed
- ciSkip check happens on slave node rather than master ([#199](https://github.com/opendevstack/ods-jenkins-shared-library/issues/199))

### Fixed
- Build is still "running" after it finishes ([#189](https://github.com/opendevstack/ods-jenkins-shared-library/issues/189))
- oc rollout output parser does not work always ([#184](https://github.com/opendevstack/ods-jenkins-shared-library/issues/184))

## [2.0] - 2019-12-13

### Added
- Optionally fail Jenkins if SonarQube scan fails ([#22](https://github.com/opendevstack/ods-jenkins-shared-library/issues/22))
- Improve deployment stage, e.g. rollout manually when no triggers are defined ([#144](https://github.com/opendevstack/ods-jenkins-shared-library/issues/144))

### Changed
- Method environmentExists check exists twice ([#138](https://github.com/opendevstack/ods-jenkins-shared-library/issues/138))

### Fixed
- Snyk scan may fail because Nexus is not configured properly ([#156](https://github.com/opendevstack/ods-jenkins-shared-library/issues/156))
- Seldom error in stageDeployToOpenshift (ArrayIndexOutOfBounds)- when checking for new deployment ([#142](https://github.com/opendevstack/ods-jenkins-shared-library/issues/142))
- Special characters in last commit message break build ([#158](https://github.com/opendevstack/ods-jenkins-shared-library/issues/158))
- Find last build in stageStartOpenshiftBuild fails on some clusters ([#159](https://github.com/opendevstack/ods-jenkins-shared-library/issues/159))

## [1.2.0] - 2019-10-10
### Added
- Make ODS pipeline configurable for MRO ([#97](https://github.com/opendevstack/ods-jenkins-shared-library/issues/97))
- Make jenkins ods shared lib ready for MRO ([#108](https://github.com/opendevstack/ods-jenkins-shared-library/pull/108))
### Fixed
- Job fails when using a custom test reports location ([#132](https://github.com/opendevstack/ods-jenkins-shared-library/issues/132)) 
- Seldom failure (OCP Build app-be-bonjour-451 was not successfull - status Running) - although build completed in same var / stage([#135](https://github.com/opendevstack/ods-jenkins-shared-library/issues/135))
- Fix build still running issue during build check ([#136](https://github.com/opendevstack/ods-jenkins-shared-library/pull/136))
- Auto Clone Environment fails during curl download ([#109](https://github.com/opendevstack/ods-jenkins-shared-library/pull/109))
- Set memory explicitly for Jenkins slave pods ([#114](https://github.com/opendevstack/ods-jenkins-shared-library/issues/114))
- Shared lib clone environment always stores @master in oc-config-artifacts ([#105](https://github.com/opendevstack/ods-jenkins-shared-library/issues/105))
- Build Openshift Image stage fails if the committer has an apostrophe in their name ([#130](https://github.com/opendevstack/ods-jenkins-shared-library/issues/130))


##  [1.1.0] - 2019-05-28
### Added
- Dump generated BC labels into file (release.json) during jenkins build ([#89](https://github.com/opendevstack/ods-jenkins-shared-library/issues/89))
- Allow to skip builds ([#45](https://github.com/opendevstack/ods-jenkins-shared-library/issues/45))
- Allow to configure build args ([#61](https://github.com/opendevstack/ods-jenkins-shared-library/issues/61))
- Add stage to produce / export CNES / SQ report ([#46](https://github.com/opendevstack/ods-jenkins-shared-library/issues/46))

### Fixed
- `'` in commit message breaks stage "Build Openshift Image" ([#86](https://github.com/opendevstack/ods-jenkins-shared-library/issues/86))
- `withCredentials` expands `$` sign - leading to bitbucket errors ([#87](https://github.com/opendevstack/ods-jenkins-shared-library/issues/87))
- Pod label is always unique ([#83](https://github.com/opendevstack/ods-jenkins-shared-library/issues/83))
- NullPointerException if error occurs during prepare stage ([#68](https://github.com/opendevstack/ods-jenkins-shared-library/issues/68))

### Changed
- Builder Pods should run with jenkins SA rather than default SA ([#64](https://github.com/opendevstack/ods-jenkins-shared-library/issues/64), [#78](https://github.com/opendevstack/ods-jenkins-shared-library/issues/78))

##  [1.0.2] - 2019-04-02
### Added
- Image author / commit empty in oc image built thru jenkins shared lib ([#47](https://github.com/opendevstack/ods-jenkins-shared-library/pull/47))

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
