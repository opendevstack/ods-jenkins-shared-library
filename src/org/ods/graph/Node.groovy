package org.ods.graph

class Node {
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

    def int inDegree() {
        return this.from.size()
    }

    def int outDegree() {
        return this.to.size()
    }

    def boolean isIsolated() {
        return this.inDegree() == 0 && this.outDegree() == 0
    }

    def boolean hasDirectLinkTo(Node n) {
        return this.from.contains(n) || this.to.contains(n)
    }

    def String toString() {
        return this.data.toString()
    }
}
