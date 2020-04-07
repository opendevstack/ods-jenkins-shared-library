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

    def fileExists(String file)

    def readFile(String file, String encoding)

    def readFile(Map args)

    def writeFile(String file, String text, String encoding)

    def writeFile(Map args)

    def readJSON(Map args)

    def writeJSON(Map args)

    def timeout(Map args, Closure block)

    def deleteDir()
    
    def withEnv(List<String> env, Closure block)
}
