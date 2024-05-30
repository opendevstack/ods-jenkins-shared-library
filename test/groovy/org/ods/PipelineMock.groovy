package org.ods

class PipelineMock {

    String commandCalled
    String username = "foo-cd-cd-user-with-password"
    String password = "the_password"

    Map env = [USERNAME: username, PASSWORD: password]

    def sh(String command) {
        commandCalled = command
    }

    def usernamePassword(Map inputs) {
        inputs
    }

    def withCredentials(List args, Closure closure) {
        def delegate = [:]
        for (arg in args) {
            delegate[arg.get('USERNAME')] = username
            delegate[arg.get('PASSWORD')] = password
        }
        closure.delegate = delegate
        closure()
    }
}
