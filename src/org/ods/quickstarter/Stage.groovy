package org.ods.quickstarter

class Stage {
  protected def script
  protected def context
  protected Map config

  protected String STAGE_NAME = 'NOT SET'

  Stage(def script, IContext context, Map config) {
    this.script = script
    this.context = context
    this.config = config
  }

  def execute() {
    script.echo "**** STARTING stage '${STAGE_NAME}' ****"
    this.run()
    script.echo "**** ENDED stage '${STAGE_NAME}' ****"
  }
}
