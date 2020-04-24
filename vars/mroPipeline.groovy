import org.ods.util.PipelineSteps
import org.ods.util.Project

def call(Map config) {

    Project project
    def repos = []

    def debug = config.get('debug', false)
    def odsImageTag = config.odsImageTag
    if (!odsImageTag) {
        error "You must set 'odsImageTag' in the config map"
    }
    def versionedDevEnvsEnabled = config.get('versionedDevEnvs', false)

    node {

        def scmBranches = scm.branches
        def branch = scmBranches[0]?.getName()
        if (branch && !branch.startsWith("*/")) {
            scmBranches = [[name: "*/${branch}"]]
        }

        // checkout local branch
        checkout([
            $class: 'GitSCM',
            branches: scmBranches,
            doGenerateSubmoduleConfigurations: scm.doGenerateSubmoduleConfigurations,
            extensions: [[$class: 'LocalBranch', localBranch: "**"]],
            userRemoteConfigs: scm.userRemoteConfigs
        ])

        def steps = new PipelineSteps(this)
        def envs = Project.getBuildEnvironment(steps, debug, versionedDevEnvsEnabled)
        def stageStartTime = System.currentTimeMillis();

        withPodTemplate(odsImageTag) {
            echo "MRO main pod starttime: ${System.currentTimeMillis() - stageStartTime}ms"
            withEnv (envs) {
                def ciSkip = false
                stage('Init') {
                    echo "**** STARTING stage Init ****"
                    stageStartTime = System.currentTimeMillis();
                    def result = phaseInit()
                    if (result) {
                        project = result.project
                        repos = result.repos
                    } else {
                        ciSkip = true
                    }
                    echo "**** ENDED stage Init (time: ${System.currentTimeMillis() - stageStartTime}ms) ****"
                }

                if (ciSkip) {
                    return
                }

                stage('Build') {
                    echo "**** STARTING stage Build ****"
                    stageStartTime = System.currentTimeMillis();
                    phaseBuild(project, repos)
                    echo "**** ENDED stage Build (time: ${System.currentTimeMillis() - stageStartTime}ms) ****"
                }

                stage('Deploy') {
                    echo "**** STARTING stage Deploy ****"
                    stageStartTime = System.currentTimeMillis();
                    phaseDeploy(project, repos)
                    echo "**** ENDED stage Deploy (time: ${System.currentTimeMillis() - stageStartTime}ms)****"
                }

                stage('Test') {
                    echo "**** STARTING stage Test ****"
                    stageStartTime = System.currentTimeMillis();
                    phaseTest(project, repos)
                    echo "**** ENDED stage Test (time: ${System.currentTimeMillis() - stageStartTime}ms) ****"
                }

                stage('Release') {
                    echo "**** STARTING stage Release ****"
                    stageStartTime = System.currentTimeMillis();
                    phaseRelease(project, repos)
                    echo "**** ENDED stage Release (time: ${System.currentTimeMillis() - stageStartTime}ms) ****"
                }

                stage('Finalize') {
                    echo "**** STARTING stage Finalize ****"
                    stageStartTime = System.currentTimeMillis();
                    phaseFinalize(project, repos)
                    echo "**** ENDED stage Finalize (time: ${System.currentTimeMillis() - stageStartTime}ms) ****"
                }
            }
        }
    }
}

private def withPodTemplate(String odsImageTag, Closure block) {
    def podLabel = "mro-jenkins-agent-${env.BUILD_NUMBER}"
    podTemplate(
        label: podLabel,
        cloud: 'openshift',
        containers: [
            containerTemplate(
                name: 'jnlp',
                image: "${env.DOCKER_REGISTRY}/cd/jenkins-slave-base:${odsImageTag}",
                workingDir: '/tmp',
                resourceRequestMemory: '512Mi',
                resourceLimitMemory: '1Gi',
                resourceRequestCpu: '200m',
                resourceLimitCpu: '1',
                alwaysPullImage: true,
                args: '${computer.jnlpmac} ${computer.name}',
                envVars: []
            )
        ],
        volumes: [],
        serviceAccount: 'jenkins',
    ) {
        block()
    }
}

return this
