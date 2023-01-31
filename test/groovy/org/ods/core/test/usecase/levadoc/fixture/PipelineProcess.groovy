package org.ods.core.test.usecase.levadoc.fixture

import org.apache.commons.io.FileUtils
import org.junit.rules.TemporaryFolder
import org.ods.core.test.pdf.PdfCompare
import org.ods.core.test.usecase.RepoDataBuilder
import org.ods.core.test.workspace.TestsReports
import org.ods.orchestration.usecase.JUnitTestReportsUseCase
import org.ods.orchestration.usecase.LeVADocumentUseCase
import org.ods.orchestration.util.Project
import org.ods.util.IPipelineSteps

class PipelineProcess {

    public static final String OVERALL = 'OverAll'

    private final ProjectFixture projectFixture
    private final boolean generateExpectedFiles
    private final String savedDocuments
    private final TemporaryFolder tempFolder
    private final String simpleClassName

    PipelineProcess(String simpleClassName,
                    ProjectFixture projectFixture,
                    boolean generateExpectedFiles,
                    String savedDocuments,
                    TemporaryFolder tempFolder){
        this.simpleClassName = simpleClassName
        this.projectFixture = projectFixture
        this.generateExpectedFiles = generateExpectedFiles
        this.savedDocuments = savedDocuments
        this.tempFolder = tempFolder
    }

    Map buildParams(){
        Map buildParams = [:]
        buildParams.projectKey = projectFixture.project
        buildParams.targetEnvironment = "dev"
        buildParams.targetEnvironmentToken = "D"
        buildParams.version = "${projectFixture.version}"
        buildParams.configItem = "BI-IT-DEVSTACK"
        buildParams.releaseStatusJiraIssueKey = projectFixture.releaseKey
        return buildParams
    }

    boolean validatePDF() {
        unzipGeneratedArtifact()
        if (generateExpectedFiles) {
            copyDocWhenRecording()
            return true
        } else {
            return new PdfCompare(savedDocuments).compareAreEqual(
                actualDoc().absolutePath,
                expectedDoc(projectFixture.component).absolutePath
            )
        }
    }

    Map getInputParamsModule(ProjectFixture projectFixture, LeVADocumentUseCase useCase) {
        Map input = RepoDataBuilder.getRepoForComponent(projectFixture.component)
        input.data.tests << [unit: testReports(useCase.project, useCase.steps).getResults(projectFixture.component, "unit")]
        return input
    }

    Map getAllResults(LeVADocumentUseCase useCase) {
        return testReports(useCase.project, useCase.steps).getAllResults(useCase.project.repositories)
    }

    void useExpectedComponentDocs(LeVADocumentUseCase useCase) {
        useCase.project.repositories.each {repo ->
            if (!repo.data.documents){
                repo.data.documents = [:]
            }
            if (DocTypeProjectFixtureWithComponent.notIsReleaseModule(repo)){
                // see @org.ods.orchestration.usecase.DocGenUseCase#createOverallDocument -> unstashFilesIntoPath
                repo.data.documents[projectFixture.docType] =  copyPdfToTemp(useCase, repo)
            }
        }
    }

    private String copyPdfToTemp(LeVADocumentUseCase useCase, Map repo) {
        def destPath = "${useCase.steps.env.WORKSPACE}/reports/${repo.id}"
        new File(destPath).mkdirs()
        File expected = expectedDoc(repo.id)
        FileUtils.copyFile(expectedDoc(repo.id), new File("${destPath}/${expected.name}"))
        return expected.name
    }

    private TestsReports testReports(Project project, IPipelineSteps steps) {
        def junitReportsUseCase = new JUnitTestReportsUseCase(project, steps)
        return new TestsReports(steps, junitReportsUseCase)
    }

    private File actualDoc() {
        return new File("${tempFolder.getRoot()}/${getArtifactName()}.pdf")
    }

    private void unzipGeneratedArtifact() {
        new AntBuilder().unzip(
            src: "${tempFolder.getRoot()}/workspace/artifacts/${getArtifactName()}.zip",
            dest: "${tempFolder.getRoot()}",
            overwrite: "true")
    }

    private String getArtifactName() {
        def comp =  (projectFixture.component) ? "${projectFixture.component}-" : ''
        return "${projectFixture.docType}-${projectFixture.project}-${comp}${projectFixture.version}-1"
    }

    private void copyDocWhenRecording() {
        FileUtils.copyFile(actualDoc(), expectedDoc(projectFixture.component))
    }

    private File expectedDoc(componentId) {
        def comp =  (componentId) ? "${componentId}/" : ''
        def filePath = "test/resources/expected/${simpleClassName}/${projectFixture.project}/${comp}"
        new File(filePath).mkdirs()
        return new File("${filePath}/${projectFixture.docType}-${projectFixture.version}-1.pdf")
    }
}
