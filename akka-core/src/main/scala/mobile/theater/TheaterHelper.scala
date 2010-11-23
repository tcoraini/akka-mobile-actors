package se.scalablesolutions.akka.mobile.theater

import se.scalablesolutions.akka.mobile.actor.MobileActor
import se.scalablesolutions.akka.mobile.actor.MobileActorRef
import se.scalablesolutions.akka.mobile.Mobile

import se.scalablesolutions.akka.actor.ActorRef

import se.scalablesolutions.akka.remote.RemoteClient

import se.scalablesolutions.akka.util.Logging

import scala.collection.mutable.HashMap

object TheaterHelper extends Logging {
  // This theater comunicates with other theaters through these agents
  private lazy val agents = {
    (new HashMap[TheaterNode, ActorRef]) += LocalTheater.node -> LocalTheater.agent
  }

  def spawnActorRemotely(constructor: Either[Class[_ <: MobileActor], () => MobileActor], node: TheaterNode): MobileActorRef = {
    val hostname = node.hostname
    val port = node.port

    // The function literal is not serializable
    val serializableConstructor: Either[String, Array[Byte]] = constructor match {
      case Left(clazz) => 
        Left(clazz.getName)

      case Right(factory) =>
        val mobileRef = new MobileActorRef(Mobile.newMobileActor(factory()))
        val bytes = mobileRef.startMigration(hostname, port)
        Right(bytes)
    }

    val agent = agentFor(hostname, port)
    (agent !! StartMobileActorRequest(serializableConstructor)) match {
      case Some(StartMobileActorReply(uuid)) => 
        log.debug("Mobile actor with UUID %s started remote theater %s:%d", uuid, hostname, port)
        new MobileActorRef(Mobile.newRemoteMobileActor(uuid, hostname, port, 5000L)) // TODO Timeout hard-coded?

      case None =>
        log.debug("Could not start actor at remote theater %s:%d, request timeout", hostname, port)
        throw new RuntimeException("Remote mobile actor start failed") // devolver algo relevante pra indicar o problema
    }
  }

  def agentFor(hostname: String, port: Int): ActorRef = agentFor(TheaterNode(hostname, port))

  def agentFor(node: TheaterNode): ActorRef = agents.get(node) match {
    case Some(agent) => agent
      
    case None => 
      val agentName = "theater@" + node.hostname + ":" + node.port
      val newAgent = RemoteClient.actorFor(agentName, node.hostname, node.port)
      agents += node -> newAgent
      newAgent
  }

  def sendToTheater(message: Any, destination: TheaterNode): Unit = { 
    val agent = agentFor(destination.hostname, destination.port)
    agent ! message
  } 
}
