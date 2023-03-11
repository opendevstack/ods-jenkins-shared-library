package org.ods.orchestration.phases

import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode

import org.ods.util.IPipelineSteps
import org.ods.util.ILogger
import org.ods.services.GitService
import org.ods.orchestration.util.Project

// Finalize Non ODS comnponents in 'dev' - largely a dummy commit.
@TypeChecked
class FinalizeNonOdsComponent {

    private Project project
    private IPipelineSteps steps
    private GitService git
    private ILogger logger

    FinalizeNonOdsComponent(Project project, IPipelineSteps steps, GitService git, ILogger logger) {
        this.project = project
        this.steps = steps
        this.git = git
        this.logger = logger
    }

    public void run(Map repo, String baseDir) {
        def commitMessage = 'ODS: Record configuration only' +
            "\r${commitBuildReference()}"
        def noFilesToStage = []
        logger.debugClocked("record-git-${repo.id}", (null as String))
        steps.dir(baseDir) {
            git.commit(noFilesToStage, "${commitMessage} [ci skip]")
        }
        logger.debugClocked("record-git-${repo.id}", (null as String))
    }

    @TypeChecked(TypeCheckingMode.SKIP)
    private String commitBuildReference() {
        "${steps.currentBuild.description}\r${steps.env.BUILD_URL}"
    }

}
