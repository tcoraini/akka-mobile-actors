package se.scalablesolutions.akka.mobile.nameservice

import se.scalablesolutions.akka.actor.Actor
import se.scalablesolutions.akka.actor.ActorRef
import se.scalablesolutions.akka.remote.RemoteClient
import se.scalablesolutions.akka.mobile.util.messages._

import se.scalablesolutions.akka.mobile.theater.LocalTheater
import se.scalablesolutions.akka.mobile.theater.TheaterNode

import scala.collection.mutable.HashMap

object NameServiceAgent {
  private val agents = new HashMap[TheaterNode, ActorRef]
  
  def agentName(node: TheaterNode): String = agentName(node.hostname, node.port)
  
  def agentName(hostname: String, port: Int): String = {
    "nameserver@" + hostname + ":" + port
  }
  
  def agentFor(hostname: String, port: Int): ActorRef = agentFor(TheaterNode(hostname, port))

  def agentFor(node: TheaterNode): ActorRef = agents.get(node) match {
    case Some(agent) => agent
      
    case None => 
      val name = agentName(node)
      val newAgent = RemoteClient.actorFor(name, node.hostname, node.port)
      agents += node -> newAgent
      newAgent
  }

  private[nameservice] def startLocalAgent(): ActorRef = {
    val agent = Actor.actorOf(new NameServiceAgent)
    val name = agentName(LocalTheater.node)
    LocalTheater.registerAgent(name, agent)
    agents += (LocalTheater.node -> agent)
    agent
  }
}

class NameServiceAgent extends Actor {

  private val actors = new HashMap[String, TheaterNode]

  def receive = {
    // Register a new actor in the name service
    case ActorRegistrationRequest(actorUuid, hostname, port) =>
      actors += (actorUuid -> TheaterNode(hostname, port))
  
    // Unregister an actor from the name service
    case ActorUnregistrationRequest(actorUuid) =>
      actors.remove(actorUuid)

    // Request the location of a certain actor in the cluster
    case ActorLocationRequest(actorUuid) =>
      actors.get(actorUuid) match {
        case Some(node) =>
          self.reply(ActorLocationResponse(node.hostname, node.port))

        case None =>
          self.reply(ActorNotFound)
      }
  }
}

          

