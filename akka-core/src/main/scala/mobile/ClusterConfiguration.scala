package se.scalablesolutions.akka.mobile

import scala.collection.immutable.HashMap

import se.scalablesolutions.akka.config.Config._

import se.scalablesolutions.akka.util.Logging

case class NodeInformation(hostname: String, port: Int)

object ClusterConfiguration extends Logging {
  
  lazy val nodes = loadConfiguration()
  
  def loadConfiguration(): HashMap[String, NodeInformation] = {
    log.debug("Reading the cluster description from configuration file") 

    val nodesNames = config.getList("cluster.nodes")

    var _nodes = new HashMap[String, NodeInformation]
    
    for {
      node <- nodesNames
      label = "cluster." + node
      hostname = config.getString(label + ".hostname").getOrElse(throw new RuntimeException("Cluster configuration file not properly formed")) 
      port = config.getInt(label + ".port").getOrElse(throw new RuntimeException("Cluster configuration file not properly formed"))
      
      nodeInfo = NodeInformation(hostname, port)
    } _nodes = _nodes + ((hostname, nodeInfo)) // TODO Indexado pelo host, se for permitir mais de um teatro por host, tem q mudar
    
    _nodes
  }
}
