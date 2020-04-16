package org.ods.component

class Logger implements ILogger {

  private Object script
  private boolean debug

  Logger(script, debug) {
    this.debug = debug
    this.script = script
  }

  void debug(String message) {
    if (debug) {
      script.echo message
    }
  }

  void info(String message) {
    script.echo message
  }

  void error(String message) {
    script.error message
  }

}
