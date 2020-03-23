import org.ods.service.ServiceRegistry
import org.ods.util.GitUtil

// TODO: How to reuse agent pod across phases?
def call(boolean condition, Closure block) {
    if (condition) {
        def git = ServiceRegistry.instance.get(GitUtil)
        dir(env.WORKSPACE) {
            sh "cp ~/.git-credentials ."
            stash(name: 'wholeWorkspace', includes: '**/*,**/.git', useDefaultExcludes: false)
            sh "rm .git-credentials"
        }

        def podLabel = "mro-jenkins-agent-${env.BUILD_NUMBER}"
        node(podLabel) {
            git.configureUser()
            unstash("wholeWorkspace")
            sh "mv .git-credentials ~/.git-credentials"
            sh(script: "git config --global credential.helper store", label : "setup credential helper")
            block()
        }
    } else {
        block()
    }
}

return this
