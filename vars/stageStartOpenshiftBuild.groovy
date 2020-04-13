import org.ods.component.IContext

def call(IContext context, Map buildArgs = [:], Map imageLabels = [:]) {
  echo "'stageStartOpenShiftbuild' has been replaced with 'odsComponentStageBuildOpenShiftImage', please use that instead."
  return odsComponentStageBuildOpenShiftImage(
    context,
    [buildArgs: buildArgs, imageLabels: imageLabels]
  )
}

return this
