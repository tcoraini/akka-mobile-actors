package se.scalablesolutions.akka.mobile

import se.scalablesolutions.akka.actor.ActorRef
import se.scalablesolutions.akka.actor.Actor
import se.scalablesolutions.akka.actor.Actor._
import se.scalablesolutions.akka.remote.RemoteNode

import se.scalablesolutions.akka.mobile.Mobile._

case class MovingActor(bytes: Array[Byte])
case class MobileActorRegistered(actorId: String)

object Theater {
  lazy val agent = actorOf(new TheaterAgent)

  def start(hostname: String, port: Int): Unit = {
    RemoteNode.start(hostname, port)
    val agentName = "theater@" + hostname + ":" + port
    RemoteNode.register(agentName, agent)
  }

  def receiveActor(bytes: Array[Byte], senderOption: Option[ActorRef] = None): ActorRef = {
    println("[THEATER] Received actor")
    val actor = mobileFromBinary(bytes)(DefaultActorFormat)
    RemoteNode.register(actor)
    //senderOption.foreach { sender => sender ! MobileActorRegistered(actor.id) }
    actor
  }
} 

class TheaterAgent extends Actor {
  
  def receive = {
    case MovingActor(bytes) =>
      val actor = Theater.receiveActor(bytes, self.sender)
      self.reply(MobileActorRegistered(actor.id))
      
    case msg => 
      println("TheaterAgent received something unknown: " + msg)
  }
}
