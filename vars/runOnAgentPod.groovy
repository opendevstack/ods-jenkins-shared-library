import org.ods.service.ServiceRegistry
import org.ods.util.GitUtil
import org.ods.util.Project

// TODO: How to reuse agent pod across phases?
def call(Project project, boolean condition, Closure block) {
    if (condition) {
        def git = ServiceRegistry.instance.get(GitUtil)
        dir(env.WORKSPACE) {
            stash(name: 'wholeWorkspace', includes: '**/*,**/.git', useDefaultExcludes: false)
        }

        def bitbucketHost = env.BITBUCKET_HOST
        def podLabel = "mro-jenkins-agent-${env.BUILD_NUMBER}"
        node(podLabel) {
            git.configureUser()
            unstash("wholeWorkspace")
            withCredentials([usernamePassword(credentialsId: project.services.bitbucket.credentials.id, usernameVariable: 'BITBUCKET_USER', passwordVariable: 'BITBUCKET_PW')]) {
                def urlWithCredentials = "https://${BITBUCKET_USER}:${BITBUCKET_PW}@${bitbucketHost}"
                writeFile(file: "${env.HOME}/.git-credentials", text: urlWithCredentials)
                sh(script: "git config --global credential.helper store", label : "setup credential helper")
            }
            block()
        }
    } else {
        block()
    }
}

return this
