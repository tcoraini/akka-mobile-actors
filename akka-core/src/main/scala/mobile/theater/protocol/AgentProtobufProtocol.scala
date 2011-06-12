package se.scalablesolutions.akka.mobile.theater.protocol

import se.scalablesolutions.akka.mobile.theater.Theater
import se.scalablesolutions.akka.mobile.theater.TheaterNode
import se.scalablesolutions.akka.mobile.theater.protocol.protobuf.ProtobufTheaterMessages._

import se.scalablesolutions.akka.actor.Actor
import se.scalablesolutions.akka.actor.ActorRef
import se.scalablesolutions.akka.remote.RemoteClient

import scala.collection.mutable.HashMap

class AgentProtobufProtocol extends ProtobufProtocol {

  private lazy val agents = new HashMap[TheaterNode, ActorRef]

  private lazy val localAgent = Actor.actor {
    case proto: TheaterMessageProtocol => 
      super.processMessage(proto)
    
    case message: Any => 
      println("\n\nMESSAGE RECEIVED BY THEATER AGENT UNKNOWN: " + message + "\n\n")
  }
  private val agentName = "theaterAgent@" + theater.node.hostname + ":" + theater.node.port

  override def init(theater: Theater): Unit = {
    super.init(theater)

    theater.registerAgent(agentName, localAgent)
    agents += theater.node -> localAgent
  }

  def sendTo(node: TheaterNode, message: TheaterMessageProtocol): Unit = {
    try {
      agentFor(node) ! message
    } catch {
      case e: IllegalStateException =>
        //TODO Gambiarra monstro
        (new Thread() {
          override def run(): Unit = {
            agentFor(node) ! message
          }
        }).start()
    }
  }
  
  def stop(): Unit = {
    theater.unregisterAgent(agentName)
    localAgent.stop()
    agents.clear
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
