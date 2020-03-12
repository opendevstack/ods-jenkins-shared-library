def call(Map config) {

    def project = [:]
    def repos   = []

    def debug = config.get('debug', false)

    node {

        checkout scm

        withEnv (mroEnvironment(debug)) {

            stage('Init') {
                def result = phaseInit()
                project = result.project
                repos = result.repos
            }

            stage('Build') {
                phaseBuild(project, repos)
            }

            stage('Deploy') {
                phaseDeploy(project, repos)
            }

            stage('Test') {
                phaseTest(project, repos)
            }

            stage('Release') {
                phaseRelease(project, repos)
            }

            stage('Finalize') {
                phaseFinalize(project, repos)
            }
        }
    }

}

return this
