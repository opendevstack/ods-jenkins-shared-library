package org.ods.util

class GitCredentialStore {

    static void configureAndStore(
        def script, String gitServerUrl, String username, String password) {
        String[] lines = AuthUtil.gitCredentialLines(gitServerUrl, username, password)
        String inputFile = "${script.env.HOME}/.git-credentials-input"
        script.writeFile(
            file: "${inputFile}",
            text: lines.join('\n')
        )
        script.echo "wrote $inputFile with credentials for $username on $gitServerUrl"
        script.sh(
            script: 'git config --global credential.helper store',
            label: 'setup git credential helper'
        )
        // this way we do not need to deal with encodings or how to update an entry
        script.sh(
            script: "cat ${inputFile} | git credential-store store",
            label: 'setup git credential store'
        )
    }

}
