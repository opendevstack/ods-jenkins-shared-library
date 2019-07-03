import org.ods.graph.DependencyGraph
import org.ods.graph.Node

// Create dependency-ordered lists of repository
def call(List<Map> repos) {
    // Transform the list of repository configs into a list of graph nodes
    def nodes = repos.collect { new Node(it) }
    nodes.each { node ->
        node.data.pipelineConfig.dependencies.each { dependency ->
            // Find all nodes that node depends on
            nodes.findAll { it.data.url == dependency }.each {
                // Add a relation between dependent nodes
                node.addTo(it)
            }
        }
    }

    // Transform a list of graph nodes into a list of repository configs
    return DependencyGraph.resolve(nodes).nodes.collect { [it.data] }
}
