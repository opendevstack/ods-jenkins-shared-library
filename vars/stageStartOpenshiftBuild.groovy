import org.ods.component.IContext

def call(IContext context, Map buildArgs = [:], Map imageLabels = [:]) {
    echo "'stageStartOpenShiftbuild' is deprecated, please use " +
        "'odsComponentStageBuildOpenShiftImage' instead."
    odsComponentStageBuildOpenShiftImage(
        context,
        [buildArgs: buildArgs, imageLabels: imageLabels,]
    )
}

return this
