# Jira Mock Data Documentation
This documentation shall help understanding the Jira data structure applied in the mock data and how the mock data
can be used to define unit tests cases and checks.

## Jira Test Cases
The table below lists the test cases provided by the Jira mock data.

The mock data is aligned with
- Jira Test Issues in the jira-dev project
- JUnit XML mock data as provided in FixtureHelper
- Unit tests in pltfmdev-demo-app-test

Note: if mock Jira test cases are changed, these changes also need to be reflected in all other systems listed above!

The test cases support different success scenarios:
- Succeeded
- Error
- Failed
- Unexecuted



| IDX | Test-ID | Name | Test Type | Execution Type | Status | Component | Outcome in Junit XML |
| --- | ------- | ---- | --------- | -------------- | ------ | --------- | -------------------- |
| 1 | PLTFMDEV-401 | verify database is correctly setup | Installation | Automated | Succeeded | DEMO-3 | sockshop-suite-1 |
| 2 | PLTFMDEV-549 | User interacts with the cart | Acceptance | Automated |  | DEMO-2 | unexecuted |
| 3 | PLTFMDEV-550 | User shows catalogue | Acceptance | Automated |  | DEMO-2 | unexecuted |
| 4 | PLTFMDEV-551 | User buys some socks | Acceptance | Automated |  | DEMO-2 | unexecuted |
| 5 | PLTFMDEV-552 | Home page looks sexy | Acceptance | Automated |  | DEMO-2 | unexecuted |
| 6 | PLTFMDEV-553 | User logs in | Acceptance | Automated | | DEMO-2 | unexecuted |
| 7 | PLTFMDEV-554 | user exists in system | Integration | Automated | Succeeded | DEMO-2 | sockshop-suite-4 |
| 8 | PLTFMDEV-1045 | FirstResultOrDefault returns the default for an empty list | Unit | Automated |  | DEMO-3 | unexecuted |
| 9 | PLTFMDEV-1046 | verify frontend is correctly setup | Installation | Automated | Succeeded | DEMO-2 | sockshop-suite-3 |
| 10 | PLTFMDEV-1060 | verify database is correctly installed | Installation | Automated | Error | DEMO-3 | sockshop-suite-1 |
| 11 | PLTFMDEV-1061 | verify database is operational | Installation | Automated | Failed | DEMO-3 | sockshop-suite-2 |
| 12 | PLTFMDEV-1062 | verify database is authentication is working | Installation | Automated | Missing | DEMO-3 | sockshop-suite-2 |
| 13 | PLTFMDEV-1073 | Cart gets processed correctly | IntegrationTest | Automated | Succeeded | Demo-3 | sockshop-suite-4 |
| 14 | PLTFMDEV-1074 | Frontend retrieves cart data correctly | IntegrationTest | Automated | Succeeded | Demo-3 | sockshop-suite-4 |
| 15 | PLTFMDEV-1075 | Frontend retrieves payment data correctly | IntegrationTest | Automated | Succeeded | Demo-3 | sockshop-suite-4 |

