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


trait RemoteMobileActor extends InnerReference {
  /*
   * Só funciona pq estamos dentro do pacote Akka. Quando não for o caso, como resolver?
   *
   * 1 - Deixar o mínimo de código necessário para a aplicação dentro do pacote Akka
   * 2 - Reescrever a classe RemoteActorRef toda
   */
  remoteActorRef: RemoteActorRef =>

  abstract override def postMessageToMailbox(message: Any, senderOption: Option[ActorRef]): Unit = {
    val newMessage = MobileActorMessage(LocalTheater.node.hostname, LocalTheater.node.port, message)

    val requestBuilder = createRemoteRequestProtocolBuilder(this, newMessage, true, senderOption)
    val actorInfo = requestBuilder.getActorInfo.toBuilder
    actorInfo.setActorType(ActorType.MOBILE_ACTOR)

    requestBuilder.setActorInfo(actorInfo.build)
    
    remoteActorRef.remoteClient.send[Any](requestBuilder.build, None)
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
    
    case other => log.debug("RemoteActorRef received a notification from its remote client: " + other)
  }

  private def tryToUpdateReference(): Unit = NameService.get(uuid) match {
    case Some(TheaterNode(remoteActorRef.hostname, remoteActorRef.port)) => 
      log.debug("Lost connection to remote node %s. Actor with UUID [%s] was there and did not migrate.", 
		TheaterNode(remoteActorRef.hostname, remoteActorRef.port).format, uuid)
      () // Actor did not migrate TODO O que fazer?

    case Some(newAddress) => {
      log.debug("Lost connection to remote node %s. Actor with UUID [%s] was there and migrated to %s. Updating the reference.", 
		TheaterNode(remoteActorRef.hostname, remoteActorRef.port).format, 
		uuid, 
		TheaterNode(newAddress.hostname, newAddress.port).format)
      outerRef.updateRemoteAddress(newAddress)
    }

    case None => ()
  }

  protected[actor] def isLocal = false
  
  protected[actor] def node = TheaterNode(remoteActorRef.hostname, remoteActorRef.port)
}
