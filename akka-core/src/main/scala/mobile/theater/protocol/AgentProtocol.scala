package se.scalablesolutions.akka.mobile.theater.protocol

import se.scalablesolutions.akka.mobile.util.messages._

import se.scalablesolutions.akka.mobile.theater.Theater
import se.scalablesolutions.akka.mobile.theater.TheaterNode

import se.scalablesolutions.akka.actor.Actor
import se.scalablesolutions.akka.actor.ActorRef
import se.scalablesolutions.akka.remote.RemoteClient

import scala.collection.mutable.HashMap

class AgentProtocol(theater: Theater) extends TheaterProtocol(theater) {
  
  private lazy val agents = new HashMap[TheaterNode, ActorRef]

  def init(): Unit = {
    val agent = Actor.actor {
      case message: TheaterMessage => 
        theater.processMessage(message)
      
      case message => 
        println("\n\nMESSAGE RECEIVED BY THEATER AGENT UNKNOWN: " + message + "\n\n")
    }
    val name = "theaterAgent@" + theater.node.hostname + ":" + theater.node.port

    theater.registerAgent(name, agent)
    agents += theater.node -> agent
  }

  def sendTo(node: TheaterNode, message: TheaterMessage): Unit = {
    message.sender = Some(theater.node)
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
    
  def agentFor(node: TheaterNode): ActorRef = agents.get(node) match {
    case Some(agent) => agent
      
    case None => 
      val agentName = "theaterAgent@" + node.hostname + ":" + node.port
      val newAgent = RemoteClient.actorFor(agentName, node.hostname, node.port)
      agents += node -> newAgent
      newAgent
  }
}
