package org.ods.util

interface IPipelineSteps {

    void dir(String path, Closure block)

    void echo(String message)

    void stash(String name)

    void unstash(String name)
}
