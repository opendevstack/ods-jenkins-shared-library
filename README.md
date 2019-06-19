# pltf-shared-library

This is a shared library to be used in combination with the pltf-meta repository.

## Configure Jenkins

1. Create the credentials with ID: 
    - cd-user-with-password to authenticate with BI bitbucket 
    - jira to authenticate with jira (local Jira or BI Jira, if configured)
    - nexus to authenticate with nexus
2. Add the library Jenkins global configuration: <https://jenkins.io/doc/book/pipeline/shared-libraries/#global-shared-libraries>
3. Ensure the library is set to load implicitly, use the master branch and has the correct credentials.
