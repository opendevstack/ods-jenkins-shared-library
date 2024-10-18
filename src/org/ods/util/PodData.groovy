package org.ods.util

import groovy.transform.TypeChecked
import com.cloudbees.groovy.cps.NonCPS

// PodData describes a Kubernetes pod.
@TypeChecked
class PodData {

    // podName is the name of the pod.
    // Example: foo-3-vfhxb
    String podName

    // podNamespace is the namespace in which the pod is running.
    // Example: foo
    String podNamespace

    // podMetaDataCreationTimestamp equals .metadata.creationTimestamp.
    // Example: 2020-11-02T10:57:35Z
    String podMetaDataCreationTimestamp

    // deploymentId is the name of the pod manager, such as the ReplicaSet or
    // ReplicationController . It is read from .metadata.generateName.
    // Example: foo-3
    String deploymentId

    // podStatus is the status phase of the pod, equal to .status.phase
    // Example: Running
    String podStatus

    // containers is a map of container names to their image.
    // Example: [bar: '172.30.21.193:5000/foo/bar@sha256:a828...4389']
    Map<String, String> containers

    @NonCPS
    Map<String, Object> toMap() {
        [
            podName: podName,
            podNamespace: podNamespace,
            podMetaDataCreationTimestamp: podMetaDataCreationTimestamp,
            deploymentId: deploymentId,
            podStatus: podStatus,
            containers: containers,
        ]
    }

    @NonCPS
    String toString() {
        toMap().toMapString()
    }

}
