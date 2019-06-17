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

// Returns a list with the root vertices of the provided graph
def call (def graph){
    def rootVertices = []
    graph.vertexSet().each { vertex ->
      // Traverse all vertices and determine if they are root
      if (!graph.incomingEdgesOf(vertex))
        rootVertices.add(vertex)
  }
  return rootVertices
}