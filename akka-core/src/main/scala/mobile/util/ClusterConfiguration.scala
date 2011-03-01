package se.scalablesolutions.akka.mobile.util

import se.scalablesolutions.akka.mobile.theater.TheaterNode
import se.scalablesolutions.akka.mobile.theater.TheaterNodeInformation

import scala.collection.immutable.HashMap

import se.scalablesolutions.akka.config.Config.config

import se.scalablesolutions.akka.util.Logging

object ClusterConfiguration extends Logging {

  lazy val nodes = loadConfiguration()

  lazy val numberOfNodes = nodes.size

  def loadConfiguration(): HashMap[String, TheaterNodeInformation] = {
    log.debug("Reading the cluster description from configuration file") 

    val nodesNames: Seq[String] = config.getList("cluster.nodes")

    var _nodes = new HashMap[String, TheaterNodeInformation]
    
    for {
      nodeName <- nodesNames
      label = "cluster." + nodeName
      
      hostname = config.getString(label + ".hostname")
      if (hostname.isDefined)
      port = config.getInt(label + ".port")
      if (port.isDefined)
      hasNameServer = config.getBool(label + ".name-server").getOrElse(false)
      
      theaterNode = TheaterNode(hostname.get, port.get)
      nodeInfo = TheaterNodeInformation(nodeName, theaterNode, hasNameServer)
    } _nodes = _nodes + ((nodeName, nodeInfo)) 
    
    _nodes
  }
}
