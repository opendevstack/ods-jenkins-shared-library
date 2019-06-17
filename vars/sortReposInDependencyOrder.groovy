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

def call(def repos) {
  def g = new DefaultDirectedGraph(DefaultEdge.class);
  def result = []

  //
  // Build dependency graph
  //
  // Vertices
  repos.each { repo ->
    println repo.toString()
    g.addVertex (repo.name)
  }
  // Edges
  repos.each { repo ->
    if (!repo.pipelineConfig.dependencies.isEmpty())
      repo.pipelineConfig.dependencies.each { dep_url -> 
        dep_repo = repos.find { it.url == dep_url }
        g.addEdge (repo.name , dep_repo.name)
      }
  }
  // println g.toString()

  //
  // Verify graph and build list
  //

  // Check for cyclic dependencies
  new CycleDetector(g).detectCycles() ? error ('Error: Detected cyclic dependency.') : println ('No cyclic dependency found')

  // Determine all roots (independent graphs)
  def root_nodes = []
  g.vertexSet().each { vertex ->
    // Traverse all vertices and determine if they are root
    if (!g.incomingEdgesOf(vertex))
      root_nodes.add(vertex)
  }
  // println "root node is" + root_nodes.toString()
  
  // Traverse the graph and build the ordered list.
  // Depth first will put the dependencies at the top of the list
  root_nodes.each { root_node ->
    def iterator = new DepthFirstIterator<>(g, root_node)
    while (iterator.hasNext()) {
      def repo = iterator.next()
      // println repo
      result.add(repos.find {it.name == repo })
    }
  }
  
  // Return list of repos in correct build order
  // println result.reverse().toString()
  result.reverse()
}