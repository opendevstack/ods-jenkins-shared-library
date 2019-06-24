# pltf-shared-library

This is a shared library to be used in combination with the pltf-meta repository.

## Configure Jenkins

1. Create the credentials with ID: 
    - 'bitbucket' to authenticate with BitBucket 
    - 'jira' to authenticate with Jira
    - 'nexus' to authenticate with Nexus

2. Add the library to Jenkins' global configuration: https://jenkins.io/doc/book/pipeline/shared-libraries/#global-shared-libraries

3. Ensure the library is set to load implicitly, use the master branch and has the correct credentials.
