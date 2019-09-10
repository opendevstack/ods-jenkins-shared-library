package org.ods.service

import hudson.model.Action
import hudson.model.Result

import java.io.StringWriter

import org.jenkinsci.plugins.workflow.actions.LabelAction
import org.jenkinsci.plugins.workflow.actions.WarningAction
import org.jenkinsci.plugins.workflow.cps.nodes.StepStartNode
import org.jenkinsci.plugins.workflow.graph.FlowNode

// courtesy https://stackoverflow.com/questions/36502945/access-stage-name-during-the-build-in-jenkins-pipeline
class JenkinsService {

    private def script

    JenkinsService(def script) {
        this.script = script
    }

    String getCurrentBuildLogAsHtml () {
        StringWriter writer = new StringWriter()
        this.script.currentBuild.getRawBuild().getLogText().writeHtmlTo(0, writer)
        return writer.getBuffer().toString()
    }

    String getCurrentBuildLogAsText () {
        StringWriter writer = new StringWriter()
        this.script.currentBuild.getRawBuild().getLogText().writeLogTo(0, writer)
        return writer.getBuffer().toString()
    }

    boolean unstashFilesIntoPath(String name, String path, String type) {
        this.script.dir(path) {
            try {
                script.unstash(name: name)
            } catch (e) {
                this.script.echo "Could not find any files of type '${type}' to unstash for name '${name}'"
                return false
            }
        }

        return true
    }
}
