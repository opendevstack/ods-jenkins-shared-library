package org.ods.quickstarter

class CreateOutputDirectoryStage extends Stage {
  protected String STAGE_NAME = 'Initialize output directory'

  CreateOutputDirectoryStage(def script, IContext context, Map config = [:]) {
    super(script, context, config)
  }

  def run() {
    script.sh(
      script: "mkdir -p ${context.outputDir}",
      label: "Create directory '${context.outputDir}'"
    )
  }
}
