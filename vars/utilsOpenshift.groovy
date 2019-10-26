import org.ods.OpenshiftDeployment
import org.ods.Context

boolean deploymentConfigExists(Context context, String targetProject, String componentId) {
  return resourceExists(context, targetProject, "dc", componentId)
}

boolean imageStreamExists(Context context, String targetProject, String componentId) {
  return resourceExists(context, targetProject, "is", componentId)
}

boolean resourceExists(Context context, String namespace, String kind, String name) {
  def statusCode = sh(
    script: "oc -n ${namespace} get ${kind}/${name} &> /dev/null",
    returnStatus: true,
    label: "Check existance of ${kind} ${name}"
  )
  def exists = statusCode == 0
  if (exists) {
    debug(context, "Resource ${kind}/${name} exists.")
  } else {
    debug(context, "Resource ${kind}/${name} does not exist.")
  }
  return exists
}

boolean automaticConfigChangeTriggerEnabled(Context context, String targetProject, String componentId) {
  try {
    def automaticValue = sh(
      script: """oc -n ${targetProject} get dc/${componentId} -o template --template '{{range .spec.triggers}}{{if eq .type "ConfigChange" }}configTriggerIsEnabled{{end}}{{end}}'""",
      returnStdout: true,
      label: "Check ConfigChange trigger for dc/${componentId}"
    ).trim()
    def exists = automaticValue == "configTriggerIsEnabled"
    if (exists) {
      debug(context, "ConfigChange trigger enabled for dc/${componentId}.")
    } else {
      debug(context, "No ConfigChange trigger set for dc/${componentId}.")
    }
    return exists
  } catch (Exception ex) {
    debug(context, "ConfigChange trigger of dc/${componentId} cannot be detected.")
    return false
  }
}

boolean automaticImageChangeTriggerEnabled(Context context, String targetProject, String componentId) {
  try {
    def automaticValue = sh(
      script: """oc -n ${targetProject} get dc/${componentId} -o template --template '{{range .spec.triggers}}{{if eq .type "ImageChange" }}{{.imageChangeParams.automatic}}{{end}}{{end}}'""",
      returnStdout: true,
      label: "Check ImageChange trigger for dc/${componentId}"
    ).trim()
    def exists = automaticValue == "true"
    if (exists) {
      debug(context, "ImageChange trigger enabled for dc/${componentId}.")
    } else {
      debug(context, "No ImageChange trigger set for dc/${componentId}.")
    }
    return exists
  } catch (Exception ex) {
    debug(context, "ImageChange trigger of dc/${componentId} cannot be detected.")
    return false
  }
}

def setLatestImageTag(Context context, String targetProject, String componentId, String tagversion) {
  sh(
    script: "oc -n ${targetProject} tag ${componentId}:${tagversion} ${componentId}:latest",
    label: "Update latest tag of is/${componentId} to ${tagversion}"
  )
}

OpenshiftDeployment rolloutDeployment(Context context, String targetProject, String componentId, int rolloutTimeout, boolean needsStart) {
  if (needsStart) {
    startRollout(context, targetProject, componentId)
  }
  return watchRollout(context, targetProject, componentId, rolloutTimeout)
}

def startRollout(Context context, String targetProject, String componentId) {
  sh(
    script: "oc -n ${targetProject} rollout latest dc/${componentId}",
    label: "Rollout latest deployment of dc/${componentId}"
  )
}

OpenshiftDeployment watchRollout(Context context, String targetProject, String componentId, int rolloutTimeout) {
  def rolloutResult = ''
  try {
    timeout(rolloutTimeout) {
      rolloutResult = sh(
        script: "oc -n ${targetProject} rollout status dc/${componentId} --watch=true",
        label: "Watch rollout of latest deployment of dc/${componentId}",
        returnStdout: true
      ).trim()
    }
    // rolloutResult is e.g.: replication controller "foo-123" successfully rolled out
    // Unfortunately there does not seem a more structured way to retrieve this information.
    def rolloutInfo = rolloutResult.split('"')
    if (rolloutInfo.size() < 2) {
      error "Got '${rolloutInfo}' as rollout status, which cannot be parsed properly ..."
    }
    def rolloutId = rolloutInfo[1] // part within the quotes
    if (!rolloutId.startsWith(componentId)) {
      error "Got '${rolloutInfo}' as rollout status, which cannot be parsed properly ..."
    }
    def rolloutStatus = sh(
      script: "oc -n ${targetProject} get rc/${rolloutId} -o jsonpath='{.metadata.annotations.openshift\\.io/deployment\\.phase}'",
      label: "Get status of latest rollout of dc/${componentId}",
      returnStdout: true
    ).trim()
    return new OpenshiftDeployment(rolloutId, rolloutStatus)
  } catch (ex) {
    debug(context, "Rollout exceeded ${rolloutTimeout}, cancelling ...")
    cancelRollout(context, targetProject, componentId)
    throw ex
  }
}

def cancelRollout(Context context, String targetProject, String componentId) {
  timeout(1) {
    sh(
      script: "oc -n ${targetProject} rollout cancel dc/${componentId}",
      label: "Cancel rollout of latest deployment of dc/${componentId}"
    )
  }
}

def debug(context, msg) {
  if (context.debug) {
    echo msg
  }
}
