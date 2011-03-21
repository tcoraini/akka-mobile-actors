package se.scalablesolutions.akka.mobile.actor

import se.scalablesolutions.akka.actor.RemoteActorRef
import se.scalablesolutions.akka.actor.ActorRef

case class AttachRefToActor(uuid: String)

trait DetachedRemoteActor extends RemoteMobileActor {
  remoteActorRef: RemoteActorRef =>

  holdMessages = true

  abstract override def postMessageToMailbox(message: Any, senderOption: Option[ActorRef]): Unit = message match {
    case AttachRefToActor(uuid) =>
      _uuid = uuid
      holdMessages = false
    
    case _ => super.postMessageToMailbox(message, senderOption)
  }
}
  
