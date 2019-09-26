package org.ods.util

interface IPipelineSteps {

    void archiveArtifacts(String artifacts)

    def checkout(Map config)

    void dir(String path, Closure block)

    void echo(String message)

    def getCurrentBuild()

    Map getEnv()

    void junit(String path)

    def load(String path)

    def sh(def args)

    void stage(String name, Closure block)

    void stash(String name)

    void unstash(String name)
}
