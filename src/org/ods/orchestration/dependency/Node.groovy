package org.ods.orchestration.dependency

class Node implements Serializable {

    Map data = [:]

    List<Node> from = []
    List<Node> to = []

    Node(Map data) {
        this.data = data
    }

    def addTo(node) {
        this.to << node
        node.addFrom(this)
    }

    def addFrom(node) {
        this.from << node
    }

    int inDegree() {
        this.from.size()
    }

    int outDegree() {
        this.to.size()
    }

    boolean isIsolated() {
        this.inDegree() == 0 && this.outDegree() == 0
    }

    boolean hasDirectLinkTo(Node n) {
        this.from.contains(n) || this.to.contains(n)
    }

    @Override
    String toString() {
        this.data.toString()
    }

}
