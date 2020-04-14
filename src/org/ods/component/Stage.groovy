package org.ods.component

class Stage {
  protected def script
  protected def context
  protected Map config

  public final String STAGE_NAME = 'NOT SET'

  Stage(def script, IContext context, Map config) {
    this.script = script
    this.context = context
    this.config = config
  }

  def execute() {
    // TODO: Replace withStage with simple echo calls once all stages use this class.
    script.withStage(STAGE_NAME, context) {
      this.run()
    }
  }
}
