package org.ods.quickstarter

class CopyFilesStage extends Stage {
  protected String STAGE_NAME = 'Copy files from quickstarter'

  CopyFilesStage(def script, IContext context, Map config = [:]) {
    super(script, context, config)
  }

  def run() {
    script.sh(
      script: "cp -rv ${context.quickstarterId}/files/. ${context.outputDir}",
      label: "Copy files from '${context.quickstarterId}/files' to '${context.outputDir}'"
    )
  }
}
