package se.scalablesolutions.akka.mobile

import se.scalablesolutions.akka.config.Config._

import se.scalablesolutions.akka.util.Logging

import se.scalablesolutions.akka.mobile.nameservice.HashFunction
import se.scalablesolutions.akka.mobile.nameservice.DefaultHashFunction


object NamingServiceControl extends Logging {
  
  private lazy val defaultHashFunction = new DefaultHashFunction

  val nameServerNodes: Array[NodeInformation] = findNamingServerNodes

  val numberOfNamingServers = nameServerNodes.size

  private val hashFunction: HashFunction = config.getString("cluster.hash_function") match {
    case Some(classname) =>
      try {
        val instance = Class.forName(classname).newInstance
        instance.asInstanceOf[HashFunction]
      } catch {
        case cnfe: ClassNotFoundException =>
          log.warning("The class '%s' could not be found. Using the default hash function instead.", classname)
          defaultHashFunction
        
        case cce: ClassCastException =>
          log.warning("The class '%s' does not extend the HashFunction trait. Using the default hash function instead.", classname)
          defaultHashFunction
      }

    case None =>
      defaultHashFunction
  }

  def hash(key: String): Int = hashFunction.hash(key, numberOfNamingServers)

  private def findNamingServerNodes: Array[NodeInformation] = {
    val nodesWithNamingService = ClusterConfiguration.nodes.values.filter {
      node => node.hasNameServer
    }
  
    nodesWithNamingService.toArray
  }

  def nameServerFor(uuid: String): TheaterNode = nameServerFor(hash(uuid))
  
  private def nameServerFor(key: Int): TheaterNode = nameServerNodes(key)

  def hasNamingServer(node: TheaterNode) = nameServerNodes.exists(_ sameAs node)
}

        

