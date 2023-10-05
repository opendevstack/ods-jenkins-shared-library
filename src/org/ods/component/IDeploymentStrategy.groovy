package org.ods.component

import org.ods.util.PodData

@SuppressWarnings('MethodCount')
interface IDeploymentStrategy {

    Map<String, List<PodData>> deploy()

}
