package se.scalablesolutions.akka.mobile.theater.protocol

import se.scalablesolutions.akka.mobile.theater.Theater
import se.scalablesolutions.akka.mobile.theater.TheaterNode
import se.scalablesolutions.akka.mobile.theater.protocol.protobuf.ProtobufTheaterMessages._

import se.scalablesolutions.akka.actor.Actor
import se.scalablesolutions.akka.actor.ActorRef
import se.scalablesolutions.akka.remote.RemoteClient

import scala.collection.mutable.HashMap

class AgentProtobufProtocol(theater: Theater) extends ProtobufProtocol(theater) {

  private lazy val agents = new HashMap[TheaterNode, ActorRef]

  def init(): Unit = {
    val agent = Actor.actor {
      case proto: TheaterMessageProtocol => 
        super.processMessage(proto)
      
      case message: Any => 
        println("\n\nMESSAGE RECEIVED BY THEATER AGENT UNKNOWN: " + message + "\n\n")
    }
    val name = "theaterAgent@" + theater.hostname + ":" + theater.port

    theater.registerAgent(name, agent)
    agents += theater.node -> agent
  }

  def sendTo(node: TheaterNode, message: TheaterMessageProtocol): Unit = {
    agentFor(node) ! message
  }
    
  def agentFor(node: TheaterNode): ActorRef = agents.get(node) match {
    case Some(agent) => agent
      
    case None => 
      val agentName = "theaterAgent@" + node.hostname + ":" + node.port
      val newAgent = RemoteClient.actorFor(agentName, node.hostname, node.port)
      agents += node -> newAgent
      newAgent
  }
}
