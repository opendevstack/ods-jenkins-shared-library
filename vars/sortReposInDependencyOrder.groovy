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

private def dependencyGraphGetRootList(def graph){
  def rootVertices = []
  graph.vertexSet().each { vertex ->
    // Traverse all vertices and determine if they are root
    if (!graph.incomingEdgesOf(vertex))
      rootVertices.add(vertex)
  }
  return rootVertices
}

private def dependencyGraphPopulate(def repos){
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

def call(def repos) {
  def result = []
  def depGraph = dependencyGraphPopulate(repos)

  // Check for cyclic dependencies
  new CycleDetector(depGraph).detectCycles() ? error ('Error: Detected cyclic dependency.') : println ('No cyclic dependency found')

  // Traverse the graph and build the ordered list.
  // Depth first will put the dependencies at the top of the list
  dependencyGraphGetRootList(depGraph).each { root_node ->
    def iterator = new DepthFirstIterator<>(depGraph, root_node)
    while (iterator.hasNext()) {
      def repo = iterator.next()
      // println repo
      result.add(repos.find {it.name == repo })
    }
  }
  
  // Return list of repos in correct build order
  // println result.reverse().toString()
  return result.reverse()
}