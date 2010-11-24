package se.scalablesolutions.akka.mobile.actor

import se.scalablesolutions.akka.mobile.theater.LocalTheater

import se.scalablesolutions.akka.actor.ActorRef
import se.scalablesolutions.akka.actor.ScalaActorRef
import se.scalablesolutions.akka.actor.RemoteActorRef
import se.scalablesolutions.akka.actor.RemoteActorSerialization._

import se.scalablesolutions.akka.remote.protocol.RemoteProtocol._

case class MobileActorMessage(senderHostname: String, senderPort: Int, message: Any)

trait RemoteMobileActor extends MobileReference {
  /*
   * Só funciona pq estamos dentro do pacote Akka. Quando não for o caso, como resolver?
   *
   * 1 - Deixar o mínimo de código necessário para a aplicação dentro do pacote Akka
   * 2 - Reescrever a classe RemoteActorRef toda
   */
  remoteActorRef: RemoteActorRef =>

  abstract override def postMessageToMailbox(message: Any, senderOption: Option[ActorRef]): Unit = {
    println("\n*** Sender: " + senderOption + " *** \n")
    senderOption match {
      case Some(ref) => println("\n*** Sender Home Address: " + ref.homeAddress + " ***\n")
      case _ => ()
    }

    val newMessage = MobileActorMessage(externalReference.homeTheater.hostname, externalReference.homeTheater.port, message)

    val requestBuilder = createRemoteRequestProtocolBuilder(this, newMessage, true, senderOption)
    val actorInfo = requestBuilder.getActorInfo.toBuilder
    actorInfo.setActorType(ActorType.MOBILE_ACTOR)

    requestBuilder.setActorInfo(actorInfo.build)

    remoteActorRef.remoteClient.send[Any](requestBuilder.build, None)
  }

  abstract override def stop: Unit = {
    _isRunning = false
    _isShutDown = true
  }

  def isLocal = false
}
