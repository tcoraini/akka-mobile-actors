package se.scalablesolutions.akka.mobile.theater

import se.scalablesolutions.akka.actor.Actor

class TheaterAgent(theater: Theater) extends Actor {
  
  def receive = {
    case MovingActor(bytes, sender) =>
      theater.receiveActor(bytes, sender)

    case StartMobileActorRequest(constructor) =>
      val ref = theater.startLocalActor(constructor)
      self.reply(StartMobileActorReply(ref.uuid))

    case MobileActorRegistered(uuid) =>
      theater.finishMigration(uuid)
    
    case ActorNewLocationNotification(uuid, hostname, port) =>
      log.debug("Theater agent at [%s:%d] received a notification that actor with UUID [%s] has migrated " + 
        "to [%s:%d].", theater.hostname, theater.port, uuid, hostname, port)

      ReferenceManagement.get(uuid) match {
        case Some(reference) => 
          reference.updateRemoteAddress(TheaterNode(hostname, port))

        case None => ()
      }

    case msg =>
      log.debug("Theater agent received an unknown message: " + msg)
  }
}
