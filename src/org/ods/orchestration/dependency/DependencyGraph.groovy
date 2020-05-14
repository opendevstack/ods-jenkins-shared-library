package org.ods.orchestration.dependency

class DependencyGraph<T> implements Serializable {

    List<T> nodes

    DependencyGraph(List<T> nodes = []) {
        this.nodes = nodes
    }

    static DependencyGraph<Node> resolve(List<Node> nodes) {
        def graph = new DependencyGraph<Node>()

        _resolve(nodes) { node ->
            // Add the node to the graph
            graph.nodes << node
        }

        return graph
    }

    static DependencyGraph<Set<Node>> resolveGroups(List<Node> nodes) {
        def graph = new DependencyGraph<Set<Node>>()

        _resolve(nodes) { node ->
            if (graph.nodes.isEmpty()) {
                // Initialize the graph with its first group
                graph.nodes << [node]
            } else {
                // Find the most recently added nodes group
                def lastGroup = graph.nodes.last()

                // Check if all nodes in lastGroup depend on node (or vice-versa)
                if (lastGroup.every { !it.hasDirectLinkTo(node) }) {
                    // Add node to existing group
                    lastGroup << node
                } else {
                    // Add node to new group
                    graph.nodes << [node]
                }
            }
        }

        return graph
    }

    @SuppressWarnings('Println')
    String toString() {
        this.nodes.each { node ->
            node.to.each { to ->
                println "${node} -> ${to}"
            }
        }
    }

    @SuppressWarnings('MethodName')
    private static void _resolve(List<Node> nodes, Closure handler) {
        // Create a mapping of nodes to distinct sets of dependencies
        Map<Node, Set> nodeDependencies = [:]
        nodes.each { node ->
            nodeDependencies[node] = node.to as Set
        }

        while (nodeDependencies.size() != 0) {
            def nodesReady = [] as Set<Node>

            // Find ready nodes (without any outbound dependencies)
            nodeDependencies.each { node, dependencies ->
                if (dependencies.isEmpty()) {
                    nodesReady << node
                }
            }

            // If there aren't any ready nodes, we've detected a cycle
            if (nodesReady.isEmpty()) {
                throw new CircularDependencyException('circular dependency detected')
            }

            nodesReady.each { ready ->
                // Handle a node ready to be processed
                handler(ready)

                // Remove ready nodes from nodeDependencies
                def nodeDependenciesToRemove = [:]
                nodeDependencies.each { node, dependencies ->
                    if (node == ready) {
                        nodeDependenciesToRemove << [ (node): dependencies ]
                    }
                }

                nodeDependencies = nodeDependencies - nodeDependenciesToRemove

                // Remove ready nodes from nodeDependencies (Groovy >= 2.5.0)
                // nodeDependencies.removeAll { node, dependencies -> node == ready }

                // Remove ready nodes from all nodes inside nodeDependencies
                nodeDependencies.each { node, dependencies -> nodeDependencies[node] -= ready }
            }
        }
    }

}
