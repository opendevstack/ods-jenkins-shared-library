package org.ods

class OdsLogger implements Logger {

  private Object script
  private boolean verbose

  OdsLogger(script, verbose) {
    this.verbose = verbose
    this.script = script
  }

  void verbose(String message) {
    if (verbose) {
      echo message
    }
  }

  void echo(String message) {
    script.echo message
  }

  void error(String message) {
    script.error message
  }

}
