package org.ods.service

import hudson.model.Action
import hudson.model.Result

import java.io.StringWriter

import org.jenkinsci.plugins.workflow.actions.LabelAction
import org.jenkinsci.plugins.workflow.actions.WarningAction
import org.jenkinsci.plugins.workflow.cps.nodes.StepStartNode
import org.jenkinsci.plugins.workflow.graph.FlowNode
import org.ods.util.IPipelineSteps

// courtesy https://stackoverflow.com/questions/36502945/access-stage-name-during-the-build-in-jenkins-pipeline
class JenkinsService {

    private IPipelineSteps steps

    JenkinsService(IPipelineSteps steps) {
        this.steps = steps
    }

    String getCurrentBuildLogAsHtml () {
        StringWriter writer = new StringWriter()
        this.steps.currentBuild.getRawBuild().getLogText().writeHtmlTo(0, writer)
        return writer.getBuffer().toString()
    }

    String getCurrentBuildLogAsText () {
        StringWriter writer = new StringWriter()
        this.steps.currentBuild.getRawBuild().getLogText().writeLogTo(0, writer)
        return writer.getBuffer().toString()
    }

    boolean unstashFilesIntoPath(String name, String path, String type) {
        def result = true

        this.steps.dir(path) {
            try {
                this.steps.unstash(name)
            } catch (e) {
                this.steps.echo "Could not find any files of type '${type}' to unstash for name '${name}'"
                result = false
            }
        }

        return result
    }
}
