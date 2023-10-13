# Changelog

## Unreleased
* Add better documentation for Helm ([#1027](https://github.com/opendevstack/ods-jenkins-shared-library/issues/1027))
### Added

### Changed

### Fixed
- Generate TRC document in D environment instead of Q ([#1029](https://github.com/opendevstack/ods-jenkins-shared-library/pull/1029))

## [4.3.2] - 2023-10-02

### Fixed
- Fix slow helm component deployments sometimes fail at the finalize step ([1031](https://github.com/opendevstack/ods-jenkins-shared-library/issues/1031))
- Fix blurred images retrieved from Jira in the document chapters ([1022](https://github.com/opendevstack/ods-jenkins-shared-library/pull/1022))
- Align tailor drift check for D, Q envs ([#1024](https://github.com/opendevstack/ods-jenkins-shared-library/pull/1024))

## [4.3.1] - 2023-07-12

### Fixed
- Fix SonarQube quality gate report to use the pull request or branch in scope ([#1016](https://github.com/opendevstack/ods-jenkins-shared-library/issues/1016))
- CURL command fix for the sonarqube quality gate report retrieval ([#1020](https://github.com/opendevstack/ods-jenkins-shared-library/pull/1020))
- Fix typos in `build.gradle` ([#1012](https://github.com/opendevstack/ods-jenkins-shared-library/pull/1012))

## [4.3.0] - 2023-07-03
### Added
- Make IS_GXP property available for CFTP documents ([#996](https://github.com/opendevstack/ods-jenkins-shared-library/issues/996))
- Add Trivy stage for security scanning ([#1008](https://github.com/opendevstack/ods-jenkins-shared-library/issues/1008))

### Changed
- Use a consistent notion of document version and remove header ([#987](https://github.com/opendevstack/ods-jenkins-shared-library/issues/987))
- Improve Document Generation Experience ([#991](https://github.com/opendevstack/ods-jenkins-shared-library/issues/991))
- Update Reference Documents & Base Documents sections ([#994](https://github.com/opendevstack/ods-jenkins-shared-library/issues/994))
- Updated functional tests after fix SSDS appendix per gamp and RA chapter 5 ([#103](https://github.com/opendevstack/ods-document-generation-templates/issues/103))
- Change initial project validation to take into account non GxP requirement ([#1007](https://github.com/opendevstack/ods-jenkins-shared-library/pull/1007))
- Show warn in Jira comment only if WIP issues exist ([#1010](https://github.com/opendevstack/ods-jenkins-shared-library/pull/1010))
- Change default registry to match Openshift 4 ([#983] (https://github.com/opendevstack/ods-jenkins-shared-library/issues/983))

### Fixed
- Fix helm release - Check pod status after deployment ([#1005](https://github.com/opendevstack/ods-jenkins-shared-library/pull/1005))
- Moving from rollout pause/resume to patching resources to avoid errors in case of inconsistent state ([#1013](https://github.com/opendevstack/ods-jenkins-shared-library/pull/1013))
- Memory leak fixes for component pipeline ([#857](https://github.com/opendevstack/ods-jenkins-shared-library/issues/857))
- Fix Component fails to deploy with more than one DeploymentConfig([#981](https://github.com/opendevstack/ods-jenkins-shared-library/issues/981))
- Fix errors when the templatesVersion is not set or set as number ([#1000](https://github.com/opendevstack/ods-jenkins-shared-library/pull/1000))

## [4.2.0] - 2023-02-21

### Added
- Add Helm Support to Release Manager ([#866](https://github.com/opendevstack/ods-jenkins-shared-library/issues/866))
- Add project property PROJECT.IS_GXP ([#963](https://github.com/opendevstack/ods-jenkins-shared-library/pull/963))
- Support Trivy premium CLI (`scannercli`) in Aqua server with new Trivy Enterprise engine ([#964](https://github.com/opendevstack/ods-jenkins-shared-library/issues/964))

### Changed

### Fixed
- Fix tests that failed when running on Windows ([#966](https://github.com/opendevstack/ods-jenkins-shared-library/pull/966))
- Deploy into Q without git tags fails ([#967](https://github.com/opendevstack/ods-jenkins-shared-library/issues/967))
- Fix Deployment rollout doesn't support replicas=0 ([#969](https://github.com/opendevstack/ods-jenkins-shared-library/issues/969))
- Fix Aqua scan support for mono repo ([#916](https://github.com/opendevstack/ods-jenkins-shared-library/pull/916))

## [4.1.0] - 2022-11-17

### Added
- Add new Infrastructure Service ([#740](https://github.com/opendevstack/ods-jenkins-shared-library/pull/740))
- Add annotation to agents by default ([#879](https://github.com/opendevstack/ods-jenkins-shared-library/issues/879))
- Added default timeout to Aqua stage ([#899](https://github.com/opendevstack/ods-jenkins-shared-library/issues/899))
- Add retry for Openshift image build status ([#901](https://github.com/opendevstack/ods-jenkins-shared-library/issues/901))

### Changed
- Better handling of Unit tests in D and DevPreview ([827](https://github.com/opendevstack/ods-jenkins-shared-library/pull/827))
- Remove call to a cps method from a non_cps method ([852](https://github.com/opendevstack/ods-jenkins-shared-library/pull/852))
- Improve error message when two coded tests are linked to the same test issue ([#835](https://github.com/opendevstack/ods-jenkins-shared-library/pull/835))
- Set test summary for empty description in TCR ([#837](https://github.com/opendevstack/ods-jenkins-shared-library/pull/837))
- Closes ([#857](https://github.com/opendevstack/ods-jenkins-shared-library/issues/857))
- Increase default timeout for rollout ([#903](https://github.com/opendevstack/ods-jenkins-shared-library/issues/903))
- Removes DIL from the set of docs generated for enviroment P ([#914](https://github.com/opendevstack/ods-jenkins-shared-library/pull/914))
- Developer preview uses the release branch if exists, the branch in Release Manager s metadata.yml cfg if not ([#920](https://github.com/opendevstack/ods-jenkins-shared-library/pull/920/))
- Allow to redeploy to D, Q and P, by setting repromote to true by default and creating tags only if they do not exist ([#926](https://github.com/opendevstack/ods-jenkins-shared-library/pull/926))


### Fixed
- Fix RM: *found unexecuted Jira tests* error during promote2Production when functional test only runs on D and QA ([#832](https://github.com/opendevstack/ods-jenkins-shared-library/pull/832))
- Fixed redeploy to P ([#947](https://github.com/opendevstack/ods-jenkins-shared-library/pull/947))
- Fixed RPN and RP Risk Assessment fields to display proper values when not null ([#956](https://github.com/opendevstack/ods-jenkins-shared-library/pull/956))
- Fixed errors in library (Null pointers and Serialization) ([#941](https://github.com/opendevstack/ods-jenkins-shared-library/pull/941))
- Fixed document history path in IVP and IVR ([#944](https://github.com/opendevstack/ods-jenkins-shared-library/pull/944))
- Fix resolveJiraDataItemReferences create list with null  ([#864](https://github.com/opendevstack/ods-jenkins-shared-library/pull/864))
- JiraService.createIssueType(JiraService.groovy:194) fails when description is missing ([#894](https://github.com/opendevstack/ods-jenkins-shared-library/issues/849))

## [4.0] - 2022-11-10

### Added
- Add functional test to LevaDoc  ([#697](https://github.com/opendevstack/ods-jenkins-shared-library/pull/697))
- Add labels to generated OpenShift resources ([#646](https://github.com/opendevstack/ods-jenkins-shared-library/pull/646))
- Added builder (agent and master) labels into image building process  ([#644](https://github.com/opendevstack/ods-jenkins-shared-library/pull/644))
- Added regexp compatibility with new Bitbucket merge commit messages ([#655](https://github.com/opendevstack/ods-jenkins-shared-library/pull/656))
- Add recommended labels to pods ([#686](https://github.com/opendevstack/ods-jenkins-shared-library/pull/686))
- Added data for Risk Assesment document ([687](https://github.com/opendevstack/ods-jenkins-shared-library/pull/687))
- Modified SSDS document to use Pull Request info not SonarQube ([#614](https://github.com/opendevstack/ods-jenkins-shared-library/pull/614))
- Added Aqua Stage ([#661](https://github.com/opendevstack/ods-jenkins-shared-library/pull/661), [#617](https://github.com/opendevstack/ods-jenkins-shared-library/pull/617))
- Add Technical Specifications and risks related to technical specifications to the TRC document ([#690](https://github.com/opendevstack/ods-jenkins-shared-library/pull/690))
- Enable the co-existence of multiple E2E test components ([#377](https://github.com/opendevstack/ods-jenkins-shared-library/issues/377))
- Integrate SonarQube scan results with Bitbucket ([#698](https://github.com/opendevstack/ods-jenkins-shared-library/pull/698))
- Provide init hook for test quickstarters to better plug into RM ([#699](https://github.com/opendevstack/ods-jenkins-shared-library/pull/699))
- Documented the labeling functionality ([#715](https://github.com/opendevstack/ods-jenkins-shared-library/pull/715))
- Add support for ods-saas-service components ([#607](https://github.com/opendevstack/ods-jenkins-shared-library/pull/607))
- Add breakable characters in SSDS Bitbucket data to let the render split the words ([#774])(https://github.com/opendevstack/ods-jenkins-shared-library/pull/774))
- Update Aqua doc ([#803](https://github.com/opendevstack/ods-jenkins-shared-library/pull/803))
- Introduce ods-library component type ([#603](https://github.com/opendevstack/ods-jenkins-shared-library/issues/603))
- RM: deploy phase verifies target project and login, even if there are only non-installable components([#810](https://github.com/opendevstack/ods-jenkins-shared-library/issues/810))
- RM: test-stage fails /with unstash exception/ if no installation/integration/acceptance test results are provided([#811](https://github.com/opendevstack/ods-jenkins-shared-library/issues/811))
- Release Manager references v1.1 SLC Docs templates ([#798](https://github.com/opendevstack/ods-jenkins-shared-library/issues/798))
- Improve Wiremock logs ([#809](https://github.com/opendevstack/ods-jenkins-shared-library/pull/809)
- Improve error message when two coded tests are linked to the same test issue ([#826](https://github.com/opendevstack/ods-jenkins-shared-library/pull/826))
- Set test summary for empty description in TCR for acceptance tests ([#844](https://github.com/opendevstack/ods-jenkins-shared-library/pull/844))
- Bump Antora page version in master (https://github.com/opendevstack/ods-documentation/issues/66)
- Improve memory cleanUp  ([#902](https://github.com/opendevstack/ods-jenkins-shared-library/pull/902))

### Changed
- Throw exception when two coded tests are linked to the same test issue (https://github.com/opendevstack/ods-jenkins-shared-library/pull/737)
- Throw controlled exception when testIssue has no component assigned  ([#693](https://github.com/opendevstack/ods-jenkins-shared-library/pull/693))
- Early stop on opened Jira issues before deploy to dev environment ([#626](https://github.com/opendevstack/ods-jenkins-shared-library/pull/626))
- Change association with the current release in the test cycles of previewed execution ([#638](https://github.com/opendevstack/ods-jenkins-shared-library/pull/638))
- Set jenkins job to unstable in case of open issues ([#664](https://github.com/opendevstack/ods-jenkins-shared-library/pull/664))
- Automatically change the date_created by Wiremock with a Wiremock wildcard ([#743](https://github.com/opendevstack/ods-jenkins-shared-library/pull/743))
- Refactor and create unit test for LeVADocumentUseCase.getReferencedDocumentsVersion ([#744](https://github.com/opendevstack/ods-jenkins-shared-library/pull/744))
- Rollback changes filtering components for TIP ([#762](https://github.com/opendevstack/ods-jenkins-shared-library/pull/762))
- Set test summary for empty description in TCP ([816](https://github.com/opendevstack/ods-jenkins-shared-library/pull/816))
- Change some Exception messages for clarity, consistency and typos ([780](https://github.com/opendevstack/ods-jenkins-shared-library/pull/780))
- Change document generation order ([#773](https://github.com/opendevstack/ods-jenkins-shared-library/pull/773))
- Rerunning of master pipeline after dev release via release-manager ([#793](https://github.com/opendevstack/ods-jenkins-shared-library/pull/793))
- Increase Socket Timeout on Pipeline Orchestrator ([#781](https://github.com/opendevstack/ods-jenkins-shared-library/pull/781)
- Increase Socket Timeout on Pipeline Orchestrator in NonCPS ([#782](https://github.com/opendevstack/ods-jenkins-shared-library/pull/782)

### Fixed
- Fixing an error when reimplementing from Deploy to D to WIP, it was using a release branch but trying to commit master branch ([#951](https://github.com/opendevstack/ods-jenkins-shared-library/pull/951))
- Prevent Jenkins nonCPS error after reporting bug  ([#776](https://github.com/opendevstack/ods-jenkins-shared-library/pull/776))
- Fixed the Developer Preview fails because "Duplicated Tests"
- Fix sort tests steps in TCP and TCR documents ([#652](https://github.com/opendevstack/ods-jenkins-shared-library/pull/652))
- Fixed environment value of SonarQube edition ([#618](https://github.com/opendevstack/ods-jenkins-shared-library/issues/618))
- Fixed the Document History of the IVP & IVR is not being generated ([#627](https://github.com/opendevstack/ods-jenkins-shared-library/pull/627))
- Fixed Test name/summary in DTP/DTR documents ([#625](https://github.com/opendevstack/ods-jenkins-shared-library/pull/625)).
- Fix A "null" string is added in the Section 3.1 of the CFTR when the Description of the Zephyr Unit Test doesn't has a Description ([#634](https://github.com/opendevstack/ods-jenkins-shared-library/pull/634))
- Orchestration pipeline throws a non serializable exeption issue 645 ([#649](https://github.com/opendevstack/ods-jenkins-shared-library/pull/649))
- Fixed bug that disallowed empty label values ([#655](https://github.com/opendevstack/ods-jenkins-shared-library/pull/655))
- Fixed regression on project dump in release manager ([#666](https://github.com/opendevstack/ods-jenkins-shared-library/issues/666))
- Fixed use of full image in the creation of the documents ([682](https://github.com/opendevstack/ods-jenkins-shared-library/pull/682))
- Fix epic Issues not correctly ordered on the CSD ([#671](https://github.com/opendevstack/ods-jenkins-shared-library/pull/671))
- Fix Reference Documents not displaying the correct version on the SLC Documents ([#672](https://github.com/opendevstack/ods-jenkins-shared-library/pull/672))
- Jenkins nonCPS prevents project.dump from work ([#673](https://github.com/opendevstack/ods-jenkins-shared-library/issues/673))
- Regression from NonCPS refactoring leads to method not found ([#675](https://github.com/opendevstack/ods-jenkins-shared-library/issues/675), ([#678](https://github.com/opendevstack/ods-jenkins-shared-library/issues/678))
- Fix reference Document Version for the DTR and TIR are not correct ([#681](https://github.com/opendevstack/ods-jenkins-shared-library/pull/681))
- Fix the referenced documents are not displaying the correct version if they are generated after the SLC Document ([#685](https://github.com/opendevstack/ods-jenkins-shared-library/pull/685))
- Fix Document History in TIR and IVR is not correct after de deploy to P ([#695](https://github.com/opendevstack/ods-jenkins-shared-library/pull/695))
- Fix NonCPS error in thumbnail replacement ([#704](https://github.com/opendevstack/ods-jenkins-shared-library/pull/704))
- Fix wrong entry in the document history when a new component is added ([#702](https://github.com/opendevstack/ods-jenkins-shared-library/pull/702))
- Fix referenced document versions after PR [#691] ([#709](https://github.com/opendevstack/ods-jenkins-shared-library/pull/709))
- Fix missing grapes import in BitbucketService [#717] ([#717](https://github.com/opendevstack/ods-jenkins-shared-library/issues/717))
- Fix by default sonarQu should analyzePullRequests:false ([#663](https://github.com/opendevstack/ods-jenkins-shared-library/issues/663))
- Fix RA table overflow replacing unicode character in jira keys ([#735](https://github.com/opendevstack/ods-jenkins-shared-library/pull/735))
- Fix CNES report is executed too soon in the Sonar Scanner stage ([#732](https://github.com/opendevstack/ods-jenkins-shared-library/issues/732))
- Fix SerializationException, Fix NULL in SSDS generation and Fix SaaS bug in FinalizeStage ([#756](https://github.com/opendevstack/ods-jenkins-shared-library/pull/756))
- Fix null in RA ([#772](https://github.com/opendevstack/ods-jenkins-shared-library/pull/772))
- Fix null in SSDS when no reviewer in a PR ([#779](https://github.com/opendevstack/ods-jenkins-shared-library/pull/779))
- While deploying on Qa with 1 bug, 3 bugs are reported instead of 1 ([#757](https://github.com/opendevstack/ods-jenkins-shared-library/pull/757)
- Fix Traceability matrix to not show Unit Tests ([#799](https://github.com/opendevstack/ods-jenkins-shared-library/pull/799)
- Fix Traceability matrix null error ([#817](https://github.com/opendevstack/ods-jenkins-shared-library/pull/817))
- Problems with temporal folder in tests ([#819](https://github.com/opendevstack/ods-jenkins-shared-library/pull/819)
- RM: test component causes new jenkins (run) instance after branch is created for release([#823](https://github.com/opendevstack/ods-jenkins-shared-library/issues/823))
- Fix the SCR table in SSDS (chapter 2.3) appears cut off ([#843](https://github.com/opendevstack/ods-jenkins-shared-library/pull/843))
- JiraService.createIssueType(JiraService.groovy:194) fails when description is missing ([#894](https://github.com/opendevstack/ods-jenkins-shared-library/issues/849))
- Fix resolveJiraDataItemReferences create list with null  ([#864](https://github.com/opendevstack/ods-jenkins-shared-library/pull/864))
- Fixed uploading of artifacts to 'pypi' repositories ([#785](https://github.com/opendevstack/ods-jenkins-shared-library/issues/785))
- Fix Aqua integration fails for future versions ([#875](https://github.com/opendevstack/ods-jenkins-shared-library/issues/875))
- Fix nexus pypi upload ([#812](https://github.com/opendevstack/ods-jenkins-shared-library/issues/812))
- Fix return image build info in odsComponentStageBuildOpenShiftImage ([#909](https://github.com/opendevstack/ods-jenkins-shared-library/issues/909))
- Fixed errors in library (Null pointers and Serialization) ([#941](https://github.com/opendevstack/ods-jenkins-shared-library/pull/941))
- Fix when the git checkout raise an Exception, the RM is not informed because the Registry is not initialized. ([#938]https://github.com/opendevstack/ods-jenkins-shared-library/issues/938)
- Fixed document history path in IVP and IVR ([#944](https://github.com/opendevstack/ods-jenkins-shared-library/pull/944))
- Fixed redeploy to P ([#947](https://github.com/opendevstack/ods-jenkins-shared-library/pull/947))
- Fixed RPN and RP Risk Assessment fields to display proper values when not null ([#956](https://github.com/opendevstack/ods-jenkins-shared-library/pull/956))
- Redeployments to QA cause detached Tags (https://github.com/opendevstack/ods-jenkins-shared-library/pull/960)

### Removed
- Remove the workaround for NPE resolving issues ([#660](https://github.com/opendevstack/ods-jenkins-shared-library/pull/660))
- Remove not needed entries in the CFTP document history ([#707](https://github.com/opendevstack/ods-jenkins-shared-library/pull/707))
- Remove non-breakable white space from Json response of JiraService ([760](https://github.com/opendevstack/ods-jenkins-shared-library/pull/760))
- Remove support for the url repository field in metadata.yml ([#954](https://github.com/opendevstack/ods-jenkins-shared-library/pull/954))

## [3.0] - 2020-08-11

### Added
- Allow execution of e2e in the same Jenkins of the ODS-CI ([#716](https://github.com/opendevstack/ods-jenkins-shared-library/pull/716))
- Merge ([ods-mro-shared-library](https://github.com/opendevstack/ods-mro-jenkins-shared-library)) into this library, and consolidate services ([#271](https://github.com/opendevstack/ods-jenkins-shared-library/issues/271))
- Allow configuration of docker context directory ([#181](https://github.com/opendevstack/ods-jenkins-shared-library/issues/181))
- Add harmonized stage names based on the new library layout ([#215](https://github.com/opendevstack/ods-jenkins-shared-library/issues/215))
- Introduce CodenArc for scanning of the shared lib ([#290](https://github.com/opendevstack/ods-jenkins-shared-library/issues/290))
- Add global var withOpenShiftCluster ([#412](https://github.com/opendevstack/ods-jenkins-shared-library/pull/412))
- Add stage to import image and automatically import if possible ([#400](https://github.com/opendevstack/ods-jenkins-shared-library/pull/400))
- For agents image configuration - check availability of image & set timeout to connect ([#391](https://github.com/opendevstack/ods-jenkins-shared-library/issues/391))
- add withStage for quickstarters ([#378](https://github.com/opendevstack/ods-jenkins-shared-library/pull/378))
- (Optionally) Deploy via Tailor ([#96](https://github.com/opendevstack/ods-jenkins-shared-library/issues/96))
- Allow configuration of BB project ([#317](https://github.com/opendevstack/ods-jenkins-shared-library/pull/317))
- Make ODS namespace configurable ([#312](https://github.com/opendevstack/ods-jenkins-shared-library/pull/312))
- Document params to odsPipeline ([#126](https://github.com/opendevstack/ods-jenkins-shared-library/issues/126))
- quickstarters/pushToRemoteStage should support existing local git repo ([#265](https://github.com/opendevstack/ods-jenkins-shared-library/issues/265))
- New quickstarter stage initRepoFromODS ([#268](https://github.com/opendevstack/ods-jenkins-shared-library/issues/268))
- Jenkins shared library should support monorepo - multi build / deploy ([#222](https://github.com/opendevstack/ods-jenkins-shared-library/issues/222))
- Get last successful build ([#155](https://github.com/opendevstack/ods-jenkins-shared-library/issues/155))
- Integrate SonarQube with pull requests ([#174](https://github.com/opendevstack/ods-jenkins-shared-library/issues/174))
- Add quickstarter pipeline ([#230](https://github.com/opendevstack/ods-jenkins-shared-library/pull/230))
- Support for dynamic injection of /docs folder into Dockerfile and MRO label support ([#229](https://github.com/opendevstack/ods-jenkins-shared-library/issues/229))
- ods-pipeline should have 2 more stages (start / finish) ([#201](https://github.com/opendevstack/ods-jenkins-shared-library/issues/201))
- verbosity of standard shared libary stages ([#196](https://github.com/opendevstack/ods-jenkins-shared-library/issues/196))
- Allow easy configuration of compute resources ([#173](https://github.com/opendevstack/ods-jenkins-shared-library/issues/173))
- Fix rollout race condition and display events in Jenkins log ([#385](https://github.com/opendevstack/ods-jenkins-shared-library/pull/385))
- Fix image not shown in documents like TIR and DTR from Document Chapters ([#789](https://github.com/opendevstack/ods-jenkins-shared-library/pull/789))

### Changed
- Lazily set OpenShift app domain ([#396](https://github.com/opendevstack/ods-jenkins-shared-library/pull/396))
- bitbucket status setting in MRO on fail ([#370](https://github.com/opendevstack/ods-jenkins-shared-library/pull/370))
- Skip shared lib build / deploy if commits did not change ([#103](https://github.com/opendevstack/ods-jenkins-shared-library/issues/103))
- Ease and document Tailor-based preview deploy ([#340](https://github.com/opendevstack/ods-jenkins-shared-library/pull/340))
- consistently use logger throughout all the shared lib ([#326](https://github.com/opendevstack/ods-jenkins-shared-library/issues/326))
- Orchestration Pipeline: Merge released code back into main branch ([#367](https://github.com/opendevstack/ods-jenkins-shared-library/issues/367))
- Use URLs consistently ([#366](https://github.com/opendevstack/ods-jenkins-shared-library/pull/366))
- MRO performance on large application ([#348](https://github.com/opendevstack/ods-jenkins-shared-library/issues/348))
- Merge OpenShiftService ([#273](https://github.com/opendevstack/ods-jenkins-shared-library/issues/273))
- Unify script/steps approach ([#276](https://github.com/opendevstack/ods-jenkins-shared-library/issues/276))
- Merge GitUtil into GitService ([#285](https://github.com/opendevstack/ods-jenkins-shared-library/issues/285))
- Move JenkinsService ([#275](https://github.com/opendevstack/ods-jenkins-shared-library/issues/275))
- Move NexusService ([#274](https://github.com/opendevstack/ods-jenkins-shared-library/issues/274))
- MRO should update commits of repos (incl self) it builds with Jenkins build job status ([#278](https://github.com/opendevstack/ods-jenkins-shared-library/issues/278))
- Set default branch to master instead of production ([#321](https://github.com/opendevstack/ods-jenkins-shared-library/pull/321))
- DTR should also work with non jira configuration ([#277](https://github.com/opendevstack/ods-jenkins-shared-library/issues/277))
- MRO - rework overall report creation (and single store) logic ([#310](https://github.com/opendevstack/ods-jenkins-shared-library/issues/310))
- Use ods namespace instead of cd ([#316](https://github.com/opendevstack/ods-jenkins-shared-library/pull/316))
- Remove DevelopersRecipientProvider from email recipients ([#309](https://github.com/opendevstack/ods-jenkins-shared-library/pull/309))
- PROJECT_ID parameter should be transformed to all lowercase ([#314](https://github.com/opendevstack/ods-jenkins-shared-library/issues/314))
- Ensure context.projectId is lowercase ([#313](https://github.com/opendevstack/ods-jenkins-shared-library/pull/313))
- Misleading Documentation In Customizing Slaves ([#304](https://github.com/opendevstack/ods-jenkins-shared-library/issues/304))
- Merge ServiceRegistry ([#272](https://github.com/opendevstack/ods-jenkins-shared-library/issues/272))
- infer Project id and component from the git url of the repo instead of coding them into the jenkinsfile ([#266](https://github.com/opendevstack/ods-jenkins-shared-library/issues/266))
- Refactor component/uploadToNexus ([#269](https://github.com/opendevstack/ods-jenkins-shared-library/issues/269))
- Refactor Pipeline to use services ([#249](https://github.com/opendevstack/ods-jenkins-shared-library/issues/249))
- Reduce surface of component context ([#252](https://github.com/opendevstack/ods-jenkins-shared-library/issues/252))
- Allow to configure imageStreamTag instead of image ([#233](https://github.com/opendevstack/ods-jenkins-shared-library/issues/233))
- Convert Snyk stage to new style ([#228](https://github.com/opendevstack/ods-jenkins-shared-library/pull/228))
- ScanForSonarqube stage should pass author to cnes report creator ([#197](https://github.com/opendevstack/ods-jenkins-shared-library/issues/197))
- Convert SonarQube stage to new style ([#226](https://github.com/opendevstack/ods-jenkins-shared-library/pull/226))
- Extract stages into classes ([#221](https://github.com/opendevstack/ods-jenkins-shared-library/pull/221))
- Print out assembled information only in debug mode ([#171](https://github.com/opendevstack/ods-jenkins-shared-library/issues/171))
- Make docker dir configurable ([#188](https://github.com/opendevstack/ods-jenkins-shared-library/pull/188))
- Expose bitbucketUrl, taken from BITBUCKET_URL if present ([#182](https://github.com/opendevstack/ods-jenkins-shared-library/pull/182))
- tedious to test and make changes in auto clone scripts ([#167](https://github.com/opendevstack/ods-jenkins-shared-library/issues/167))

### Fixed
- Quickstarter PushToRemoteStage - cannot configure branch to be pushed ([#424](https://github.com/opendevstack/ods-jenkins-shared-library/issues/424))
- Concurrency - grape load fails during shared lib compile ([#422](https://github.com/opendevstack/ods-jenkins-shared-library/issues/422))
- Resolve only JiraDataTypes in Project (#416) ([#418](https://github.com/opendevstack/ods-jenkins-shared-library/pull/418))
- Rollout should not fail when containers point to images from Docker Hub directly ([#407](https://github.com/opendevstack/ods-jenkins-shared-library/issues/407))
- Environment mapping broken when using multiple odsComponentPipelines ([#394](https://github.com/opendevstack/ods-jenkins-shared-library/issues/394))
- Snyk monitor command for Gradle based projects fails ([#398](https://github.com/opendevstack/ods-jenkins-shared-library/issues/398))
- Run Snyk monitor command within env block ([#399](https://github.com/opendevstack/ods-jenkins-shared-library/pull/399))
- race condition: deployment logic & tagging sometimes makes MRO fail ([#382](https://github.com/opendevstack/ods-jenkins-shared-library/issues/382))
- ods quickstarter pipeline does not show any stages in stageview ([#376](https://github.com/opendevstack/ods-jenkins-shared-library/issues/376))
- MRO Test / Build stage duplicate lots of code ([#359](https://github.com/opendevstack/ods-jenkins-shared-library/issues/359))
- autocloning no longer works - route creation ahead fails ([#350](https://github.com/opendevstack/ods-jenkins-shared-library/issues/350))
- Fix various smaller issues ([#352](https://github.com/opendevstack/ods-jenkins-shared-library/pull/352))
- ensure all credentials are masked in output logs ([#212](https://github.com/opendevstack/ods-jenkins-shared-library/issues/212))
- Image sha cannot be resolved on promotion to other cluster ([#343](https://github.com/opendevstack/ods-jenkins-shared-library/issues/343))
- Allow repo name which does not follow <project>-<component> pattern ([#327](https://github.com/opendevstack/ods-jenkins-shared-library/issues/327))
- Checking odsComponentPipeline argument input types ([#315](https://github.com/opendevstack/ods-jenkins-shared-library/issues/315))
- Nexus password in plain text at the $JenkinsLog on a quickstarter build ([#288](https://github.com/opendevstack/ods-jenkins-shared-library/issues/288))
- SnykService calls to snyk cli always return true ([#258](https://github.com/opendevstack/ods-jenkins-shared-library/issues/258))
- Bitbucket not recognizing last successful build after a failed one ([#300](https://github.com/opendevstack/ods-jenkins-shared-library/issues/300))
- Remove Logger#error ([#291](https://github.com/opendevstack/ods-jenkins-shared-library/issues/291))
- component/scanWithSonarStage should use the projectId/componentId from context instead of hard coding projectKey ([#267](https://github.com/opendevstack/ods-jenkins-shared-library/issues/267))
- Refactor pipeline - MRO changes / cleanups ([#257](https://github.com/opendevstack/ods-jenkins-shared-library/issues/257))
- Using base image from other namespace did not work ([#179](https://github.com/opendevstack/ods-jenkins-shared-library/issues/179))
- Fix build args patches ([#236](https://github.com/opendevstack/ods-jenkins-shared-library/pull/236))
- stageScanForSonarqube lacks verbosity ([#193](https://github.com/opendevstack/ods-jenkins-shared-library/issues/193))
- ciSkip check happens on slave node rather than master ([#199](https://github.com/opendevstack/ods-jenkins-shared-library/issues/199))
- Hard coded protocol in OdsPipeline.groovy breaks Jenkins Build ([#74](https://github.com/opendevstack/ods-jenkins-shared-library/issues/74))
- Build is still "running" after it finishes ([#189](https://github.com/opendevstack/ods-jenkins-shared-library/issues/189))
- oc rollout output parser does not work always ([#184](https://github.com/opendevstack/ods-jenkins-shared-library/issues/184))

### Removed
- Remove OWASP support ([#225](https://github.com/opendevstack/ods-jenkins-shared-library/issues/225))

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
