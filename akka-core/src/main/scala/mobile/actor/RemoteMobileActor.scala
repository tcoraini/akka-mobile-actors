package se.scalablesolutions.akka.mobile.actor

import se.scalablesolutions.akka.mobile.theater.LocalTheater
import se.scalablesolutions.akka.mobile.theater.TheaterNode
import se.scalablesolutions.akka.mobile.theater.ReferenceManagement
import se.scalablesolutions.akka.mobile.util.messages._
import se.scalablesolutions.akka.mobile.nameservice.NameService

import se.scalablesolutions.akka.actor.ActorRef
import se.scalablesolutions.akka.actor.ScalaActorRef
import se.scalablesolutions.akka.actor.RemoteActorRef
import se.scalablesolutions.akka.actor.RemoteActorSerialization._

import se.scalablesolutions.akka.remote.protocol.RemoteProtocol._
import se.scalablesolutions.akka.remote.RemoteClientLifeCycleEvent
import se.scalablesolutions.akka.remote.RemoteClientDisconnected

import java.nio.channels.ClosedChannelException

import se.scalablesolutions.akka.actor.Actor
import se.scalablesolutions.akka.actor.Actor._

class ListenerActor extends Actor {
  println("[LISTENER] Initializing listener actor...")
  def receive = {
    case msg => println("[LISTENER] Received a notification: " + msg)
  }
}

trait RemoteMobileActor extends InnerReference {
  /*
   * Só funciona pq estamos dentro do pacote Akka. Quando não for o caso, como resolver?
   *
   * 1 - Deixar o mínimo de código necessário para a aplicação dentro do pacote Akka
   * 2 - Reescrever a classe RemoteActorRef toda
   */
  remoteActorRef: RemoteActorRef =>
  
//  val listener = actorOf[ListenerActor].start
//  remoteActorRef.remoteClient.addListener(this)

  abstract override def postMessageToMailbox(message: Any, senderOption: Option[ActorRef]): Unit = {
    // Event notifications about the remote client connection are treated locally
//    if (message.isInstanceOf[RemoteClientLifeCycleEvent]) {
  //    handleRemoteClientEvent(message.asInstanceOf[RemoteClientLifeCycleEvent])
    //} else {
      val newMessage = MobileActorMessage(homeTheater.hostname, homeTheater.port, message)

      val requestBuilder = createRemoteRequestProtocolBuilder(this, newMessage, true, senderOption)
      val actorInfo = requestBuilder.getActorInfo.toBuilder
      actorInfo.setActorType(ActorType.MOBILE_ACTOR)

      requestBuilder.setActorInfo(actorInfo.build)
      
      remoteActorRef.remoteClient.send[Any](requestBuilder.build, None)
//    }
  }

  abstract override def start: ActorRef = {
    ReferenceManagement.registerForRemoteClientEvents(this, remoteActorRef.remoteClient)
    super.start
  }

  abstract override def stop: Unit = {
    _isRunning = false
    _isShutDown = true
    ReferenceManagement.unregisterForRemoteClientEvents(this, remoteActorRef.remoteClient)
  }
  
  private[mobile] def handleRemoteClientEvent(message: RemoteClientLifeCycleEvent): Unit = message match {
    case rmd: RemoteClientDisconnected => tryToUpdateReference()
    
    case other => println("RemoteActorRef received a notification from its remote client: " + other)
  }

  private def tryToUpdateReference(): Unit = NameService.get(uuid) match {
    case Some(TheaterNode(remoteActorRef.hostname, remoteActorRef.port)) => 
      log.debug("Lost connection to remote node [%s:%d]. Actor [%s] was there and did not migrate.", 
		remoteActorRef.hostname, remoteActorRef.port, uuid)
      () // Actor did not migrate

    case Some(newAddress) => 
      log.debug("Lost connection to remote node [%s:%d]. Actor [%s] was there and migrated to [%s:%d]. Updating the reference.", 
		remoteActorRef.hostname, remoteActorRef.port, uuid, newAddress.hostname, newAddress.port)
      outerRef.updateRemoteAddress(newAddress)

    case None => ()
  }

  def isLocal = false
}
