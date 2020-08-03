# Jenkins Shared Library

![](https://github.com/opendevstack/ods-jenkins-shared-library/workflows/ODS%20Library%20Build/badge.svg?branch=master)

## Documentation
See [Jenkins Shared Library](https://www.opendevstack.org/ods-documentation/) for details.
 
The source of this documentation is located in the antora folder at  https://github.com/opendevstack/ods-jenkins-shared-library/tree/master/docs/modules/jenkins-shared-library/pages.

## Development

Use `./gradlew build` to run the code formatting checks and tests. The code style checks are done via [CodeNarc](https://codenarc.github.io/CodeNarc/). Its ruleset is located in [codenarc.groovy](https://github.com/opendevstack/ods-jenkins-shared-library/blob/master/codenarc.groovy). The Gradle `build` target runs both `codenarcMain` and `codenarcTest`. `codeNarcMain` is not allowed to fail (only the configured amount of violations of priority 3 are allowed), see the configuration in [build.gradle](https://github.com/opendevstack/ods-jenkins-shared-library/blob/master/build.gradle). The found violations are saved to an HTML report under `build/reports/codenarc`.
