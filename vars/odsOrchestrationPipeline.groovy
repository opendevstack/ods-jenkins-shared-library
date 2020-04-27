import org.ods.orchestration.util.PipelineSteps
import org.ods.orchestration.util.Project

import org.ods.orchestration.InitStage
import org.ods.orchestration.BuildStage
import org.ods.orchestration.DeployStage
import org.ods.orchestration.TestStage
import org.ods.orchestration.ReleaseStage
import org.ods.orchestration.FinalizeStage

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
                def result = new InitStage(this, project, repos).execute()
                if (result) {
                    project = result.project
                    repos = result.repos
                } else {
                    // skip pipeline
                    return
                }

                new BuildStage(this, project, repos).execute()

                new DeployStage(this, project, repos).execute()

                new TestStage(this, project, repos).execute()

                new ReleaseStage(this, project, repos).execute()

                new FinalizeStage(this, project, repos).execute()
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
