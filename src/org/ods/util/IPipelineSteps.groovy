package org.ods.util

@SuppressWarnings('MethodCount')
interface IPipelineSteps {

    String libraryResource(String path)

    void archiveArtifacts(String artifacts)

    void archiveArtifacts(Map args)

    def checkout(Map config)

    void dir(String path, Closure block)

    void echo(String message)

    void error(String message)

    def getCurrentBuild()

    Map getEnv()

    void junit(String path)

    void junit(Map config)

    def load(String path)

    def node(String name, Closure block)

    def sh(def args)

    void stage(String name, Closure block)

    void stash(String name)

    void stash(Map config)

    void unstash(String name)

    def fileExists(String file)

    def readFile(String file, String encoding)

    def readFile(Map args)

    def writeFile(String file, String text, String encoding)

    def writeFile(Map args)

    def readJSON(Map args)

    def writeJSON(Map args)

    def readYaml(Map args)

    def writeYaml(Map args)

    def timeout(Map args, Closure block)

    def deleteDir()

    def sleep(int seconds)

    def withEnv(List<String> env, Closure block)

    def unstable(String message)

    def usernamePassword(Map credentialsData)

    def sshUserPrivateKey(Map credentialsData)

    def string(Map credentialsData)

    def withCredentials(List credentialsList, Closure block)

    def unwrap()

    def emailext(Map args)

    def findFiles (Map args)

}
