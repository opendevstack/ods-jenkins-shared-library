package org.ods.util

interface IPipelineSteps {

    void archiveArtifacts(String artifacts)

    void dir(String path, Closure block)

    void echo(String message)

    Map env()

    void junit(String path)

    def load(String path)

    def sh(def args)

    void stash(String name)

    void unstash(String name)
}
