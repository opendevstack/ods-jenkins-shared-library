package org.ods.orchestration

import org.ods.orchestration.scheduler.LeVADocumentScheduler
import org.ods.orchestration.usecase.JiraUseCase
import org.ods.orchestration.util.MROPipelineUtil
import org.ods.orchestration.util.Project
import org.ods.services.GitService
import org.ods.services.NexusService
import org.ods.services.ServiceRegistry
import org.ods.util.ILogger
import org.ods.util.IPipelineSteps
import org.ods.util.Logger
import util.PipelineSteps
import util.SpecHelper

import static util.FixtureHelper.createProject

class FinalizeStageSpec extends SpecHelper {
    Project project
    FinalizeStage finalStage
    IPipelineSteps script
    MROPipelineUtil util
    JiraUseCase jira
    GitService gitService
    LeVADocumentScheduler levaDocScheduler
    ILogger logger
    NexusService nexusService

    def setup() {
        script = new PipelineSteps()
        levaDocScheduler = Mock(LeVADocumentScheduler)
        project = Spy(createProject(["version":"1.0"]))
        util = Mock(MROPipelineUtil)
        gitService = Mock(GitService)
        jira = Mock(JiraUseCase)
        logger = new Logger(script, true)
        nexusService = Mock(NexusService)

        createService()
        for (repo in project.data.metadata.repositories) {
            repo.data.git = [:]
            repo.data.git.createdExecutionCommit = 'd240853866f20fc3e536cb3bca86c86c54b723ce'

        }
        project.gitData.createdExecutionCommit = 'd240853866f20fc3e536cb3bca86c86c54b723ce'
        finalStage = Spy(new FinalizeStage(script, project, project.data.metadata.repositories, nexusService, logger))
    }

    ServiceRegistry createService() {
        def registry = ServiceRegistry.instance

        registry.add(IPipelineSteps, script)
        registry.add(MROPipelineUtil, util)
        registry.add(JiraUseCase, jira)
        registry.add(Logger, logger)

        return registry
    }

    def "pushToMasterWhenWIPandNoReleaseBranch"() {
        given:
        Map buildParams = [:]
        buildParams.version = "WIP"
        buildParams.changeId = "1.0.0"
        buildParams.targetEnvironmentToken = "D"
        project.buildParams.version = 'WIP'
        project.setGitReleaseBranch('master')

        when:
        finalStage.recordAndPushEnvStateForReleaseManager(script, logger, gitService)

        then:
        1 * gitService.pushRef('master')
    }

    def "pushToReleaseAndMasterWhenWipAndReleaseBranch"() {
        given:
        Map buildParams = [:]
        buildParams.version = "WIP"
        buildParams.changeId = "1.0.0"
        buildParams.targetEnvironmentToken = "D"
        project.buildParams.version = "WIP"
        project.setGitReleaseBranch('release/1.0.0')

        when:
        finalStage.recordAndPushEnvStateForReleaseManager(script, logger, gitService)

        then:
        1 * gitService.pushRef('master')
        0 * gitService.createTag(project.targetTag)
        1 * gitService.pushForceBranchWithTags(project.gitReleaseBranch)
    }

    def "pushToReleaseAndTag"() {
        given:
        Map buildParams = [:]
        buildParams.version = "1.0.0"
        buildParams.changeId = "1.0.0"
        buildParams.targetEnvironmentToken = "D"
        project.gitData.targetTag = "1.0.0"
        gitService.remoteTagExists(project.targetTag) >> false

        when:
        finalStage.recordAndPushEnvStateForReleaseManager(script, logger, gitService)

        then:
        1 * gitService.pushRef('master')
        1 * gitService.createTag(project.targetTag)
        1 * gitService.pushForceBranchWithTags(project.gitReleaseBranch)
    }

    def "pushToReleaseWithExistingTag"() {
        given:
        Map buildParams = [:]
        buildParams.version = "1.0.0"
        buildParams.changeId = "1.0.0"
        buildParams.targetEnvironmentToken = "D"
        project.gitData.targetTag = "1.0.0"
        gitService.remoteTagExists(project.targetTag) >> true

        when:
        finalStage.recordAndPushEnvStateForReleaseManager(script, logger, gitService)

        then:
        1 * gitService.pushRef('master')
        1 * gitService.createTag(project.targetTag)
        1 * gitService.pushForceBranchWithTags(project.gitReleaseBranch)
    }

    def "integrateIntoMainBranchRepos if repo of type is #type"() {
        given:
        def repos = project.data.metadata.repositories
        repos.each { repo ->
            repo.type = type
        }

        def finalStageNotInstallable = Spy(new FinalizeStage(script, project, repos))

        when:
        finalStageNotInstallable.integrateIntoMainBranchRepos(script, gitService)

        then:
        0 * finalStageNotInstallable.doIntegrateIntoMainBranches(_)

        where:
        type               | _
        'ods-test'         | _
        'ods-library'      | _
        'ods-infra'        | _
        'ods-saas-service' | _
    }

    def "uploadTestReportToNexus should throw exception if name is null"() {
        given:
        def fileMock = Mock(File)
        fileMock.exists() >> false

        when:
        finalStage.uploadTestReportToNexus(null, fileMock)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to upload test report. 'name' is undefined."
    }

    def "uploadTestReportToNexus should throw exception if file is null"() {
        given:
        def name = "test.zip"

        when:
        finalStage.uploadTestReportToNexus(name, null)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to upload test report. 'file' is undefined."
    }

    def "uploadTestReportToNexus should throw exception if file does not exist"() {
        given:
        def name = "test.zip"
        def fileMock = Mock(File)
        fileMock.exists() >> false

        when:
        finalStage.uploadTestReportToNexus(name, fileMock)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to upload test report. 'file' is undefined."
    }

    def "uploadTestReportToNexus should call nexus.storeArtifact and logger methods"() {
        given:
        def name = "test.zip"
        def tempFile = File.createTempFile("test", ".zip")
        tempFile.deleteOnExit()
        tempFile.bytes = [1, 2, 3]

        when:
        finalStage.uploadTestReportToNexus(name, tempFile)

        then:
        project.key >> "testProject"

        and:
        1 * nexusService.storeArtifact(
            'leva-documentation',
            'testproject-1.0/xunit',
            'test.zip',
            [1, 2, 3],
            "application/zip"
        )
    }

    def "uploadTestReportToNexus should log error and rethrow if nexus throws"() {
        given:
        def name = "test.zip"
        def tempFile = File.createTempFile("test", ".zip")
        tempFile.deleteOnExit()
        tempFile.bytes = [1, 2, 3]

        when:
        finalStage.uploadTestReportToNexus(name, tempFile)

        then:
        1 * nexusService.storeArtifact(_, _, _, _, _) >> { throw new RuntimeException("Nexus error") }

        and:
        thrown(RuntimeException)
    }

    def "buildXunitZipFile throws exception if testDir is null"() {
        given:
        def stepsMock = Mock(IPipelineSteps)
        def testDir = null
        def zipFileName = "xunit.zip"

        when:
        finalStage.buildXunitZipFile(stepsMock, testDir, zipFileName)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: The test directory 'null' does not exist."
    }

    def "buildXunitZipFile throws exception if testDir not exists"() {
        given:
        def stepsMock = Mock(IPipelineSteps)
        def testDir = "/ruta/falsa"
        def zipFileName = "xunit.zip"
        stepsMock.fileExists(testDir) >> false

        when:
        finalStage.buildXunitZipFile(stepsMock, testDir, zipFileName)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: The test directory '/ruta/falsa' does not exist."
    }

    def "buildXunitZipFile throws exception if zip file was not created"() {
        given:
        def stepsMock = Mock(IPipelineSteps)
        def testDir = File.createTempDir().absolutePath
        def zipFileName = "xunit.zip"
        stepsMock.fileExists(testDir) >> true
        stepsMock.sh(_) >> null

        // Simula que el archivo zip no existe
        def zipFilePath = java.nio.file.Paths.get(testDir, zipFileName)
        def zipFile = zipFilePath.toFile()
        zipFile.delete() // Asegura que no existe

        when:
        finalStage.buildXunitZipFile(stepsMock, testDir, zipFileName)

        then:
        def e = thrown(RuntimeException)
        e.message.contains("Error: The ZIP file was not created correctly")
    }

    def "buildXunitZipFile returns file"() {
        given:
        def stepsMock = Mock(IPipelineSteps)
        def testDir = File.createTempDir().absolutePath
        def zipFileName = "xunit.zip"
        stepsMock.fileExists(testDir) >> true
        stepsMock.sh(_) >> null

        // Crea el archivo zip simulado
        def zipFilePath = java.nio.file.Paths.get(testDir, zipFileName)
        def zipFile = zipFilePath.toFile()
        zipFile.bytes = [1,2,3]

        when:
        def result = finalStage.buildXunitZipFile(stepsMock, testDir, zipFileName)

        then:
        result.exists()
        result.length() > 0
        result == zipFile
    }
}
