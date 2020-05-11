import java.nio.file.Paths

@Grab(group='com.konghq', module='unirest-java', version='2.4.03', classifier='standalone')
import kong.unirest.Unirest

import org.ods.orchestration.util.PipelineUtil
import org.ods.orchestration.util.MROPipelineUtil
import org.ods.util.PipelineSteps
import org.ods.orchestration.util.Project
import org.ods.orchestration.InitStage
import org.ods.orchestration.BuildStage
import org.ods.orchestration.DeployStage
import org.ods.orchestration.TestStage
import org.ods.orchestration.ReleaseStage
import org.ods.orchestration.FinalizeStage

def call(Map config) {
    Unirest.config()
        .socketTimeout(1200000)
        .connectTimeout(120000)

    Project project
    def repos = []

    def debug = config.get('debug', false)
    def odsImageTag = config.odsImageTag
    if (!odsImageTag) {
        error "You must set 'odsImageTag' in the config map"
    }
    def versionedDevEnvsEnabled = config.get('versionedDevEnvs', false)

    node {
        // Clean workspace from previous runs
        [
            PipelineUtil.ARTIFACTS_BASE_DIR,
            PipelineUtil.SONARQUBE_BASE_DIR,
            PipelineUtil.XUNIT_DOCUMENTS_BASE_DIR,
            MROPipelineUtil.REPOS_BASE_DIR,
        ].each { name ->
            steps.echo("Cleaning workspace directory '${name}' from previous runs")
            Paths.get(env.WORKSPACE, name).toFile().deleteDir()
        }

        def scmBranches = scm.branches
        def branch = scmBranches[0]?.name
        if (branch && !branch.startsWith('*/')) {
            scmBranches = [[name: "*/${branch}"]]
        }

        // checkout local branch
        checkout([
            $class: 'GitSCM',
            branches: scmBranches,
            doGenerateSubmoduleConfigurations: scm.doGenerateSubmoduleConfigurations,
            extensions: [[$class: 'LocalBranch', localBranch: '**']],
            userRemoteConfigs: scm.userRemoteConfigs,
        ])

        def steps = new PipelineSteps(this)
        def envs = Project.getBuildEnvironment(steps, debug, versionedDevEnvsEnabled)
        def stageStartTime = System.currentTimeMillis()

        withPodTemplate(odsImageTag) {
            echo "MRO main pod starttime: ${System.currentTimeMillis() - stageStartTime}ms"
            withEnv (envs) {
                def result = new InitStage(this, project, repos).execute()
                if (result) {
                    project = result.project
                    repos = result.repos
                } else {
                    echo 'Skip pipeline as no project/repos computed'
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

private withPodTemplate(String odsImageTag, Closure block) {
    def podLabel = "mro-jenkins-agent-${env.BUILD_NUMBER}"
    def odsNamespace = env.ODS_NAMESPACE ?: 'ods'
    podTemplate(
        label: podLabel,
        cloud: 'openshift',
        containers: [
            containerTemplate(
                name: 'jnlp',
                image: "${env.DOCKER_REGISTRY}/${odsNamespace}/jenkins-slave-base:${odsImageTag}",
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
