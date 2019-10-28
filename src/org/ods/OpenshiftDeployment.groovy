package org.ods

class OpenshiftDeployment {

  String id
  String status

  OpenshiftDeployment(String id, String status) {
    this.id = id
    this.status = status
  }
}
