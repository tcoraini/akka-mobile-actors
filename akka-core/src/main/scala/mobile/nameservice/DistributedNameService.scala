package se.scalablesolutions.akka.mobile.nameservice

import se.scalablesolutions.akka.mobile.theater.TheaterNode
import se.scalablesolutions.akka.mobile.util.ClusterConfiguration

import se.scalablesolutions.akka.util.Logging
import se.scalablesolutions.akka.config.Config

object DistributedNameService extends Logging {
  
  private val nodes: Array[TheaterNode] = findNameServiceNodes

  private val numberOfNodes = nodes.size

  private lazy val defaultHashFunction = new DefaultHashFunction

  private val hashFunction: HashFunction = Config.config.getString("cluster.name-service.hash-function") match {
    case Some(classname) =>
      try {
        val instance = Class.forName(classname).newInstance.asInstanceOf[HashFunction]
        log.info("Using '%s' as the hash function for the distributed name service.", classname)
        instance
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

  private def hash(key: String): Int = hashFunction.hash(key, numberOfNodes)

  private def findNameServiceNodes: Array[TheaterNode] = {
    val nodesWithNameService = ClusterConfiguration.nodes.values.filter {
      node => node.hasNameServer
    } map {
      node => TheaterNode(node.hostname, node.port)
    }
  
    nodesWithNameService.toArray
  }

  private def nameServerFor(uuid: String): TheaterNode = {
    nodes(hash(uuid))
  }
}

class DistributedNameService extends NameService {
  import DistributedNameService._

  private var initialized = false

  def init(): Unit = {
    if (!initialized) {
      // In case there is a local name server, we start a NameServiceAgent
      if (nodes.exists(node => node.isLocal)) {
        NameServiceAgent.startLocalAgent()
      }
      initialized = true
    }
  }

  def put(uuid: String, node: TheaterNode): Unit = {
    val nameServer = nameServerFor(uuid)   
    val agent = NameServiceAgent.agentFor(nameServer)
    agent ! ActorRegistrationRequest(uuid, node.hostname, node.port)
  }


  def get(uuid: String): Option[TheaterNode] = {
    val nameServer = nameServerFor(uuid)
    val agent = NameServiceAgent.agentFor(nameServer)
    (agent !! ActorLocationRequest(uuid)) match {
      case Some(ActorLocationResponse(hostname, port)) =>
        Some(TheaterNode(hostname, port))

      case _ => None
    }
  }

  def remove(uuid: String): Unit = {
    val nameServer = nameServerFor(uuid)   
    val agent = NameServiceAgent.agentFor(nameServer)
    agent ! ActorUnregistrationRequest(uuid)
  }

  override def toString = "Distributed Name Service"
}

