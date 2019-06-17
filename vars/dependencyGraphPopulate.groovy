@Grab('org.jgrapht:jgrapht-core:1.3.1')
@Grab('org.jgrapht:jgrapht-io:1.3.1')

import org.jgrapht.*;
import org.jgrapht.graph.*;
import org.jgrapht.alg.*;
import org.jgrapht.alg.cycle.*;
import org.jgrapht.io.*;
import org.jgrapht.traverse.*;

import java.io.*;
import java.net.*;
import java.util.*;

// Build and return the graph based on provided repos list
def call (def repos) {
    def directedGraph = new DefaultDirectedGraph(DefaultEdge.class);
    // Vertices
    repos.each { repo ->
        directedGraph.addVertex (repo.name)
    }
    // Edges
    repos.each { repo ->
        if (!repo.pipelineConfig.dependencies.isEmpty())
        repo.pipelineConfig.dependencies.each { dep_url -> 
            dep_repo = repos.find { it.url == dep_url }
            if (!dep_repo)
            error ('Missing dependency defined in repository' + repo.name)
            directedGraph.addEdge (repo.name , dep_repo.name)
        }
    }
    return directedGraph
}