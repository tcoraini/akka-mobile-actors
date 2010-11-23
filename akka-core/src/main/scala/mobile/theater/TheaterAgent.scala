package se.scalablesolutions.akka.mobile.theater

import se.scalablesolutions.akka.actor.Actor

class TheaterAgent extends Actor {
  
  def receive = {
    case MovingActor(bytes, sender) =>
      LocalTheater.receiveActor(bytes, sender)

    case StartMobileActorRequest(constructor) =>
      val ref = LocalTheater.startLocalActor(constructor)
      self.reply(StartMobileActorReply(ref.uuid))

    case MobileActorRegistered(uuid) =>
      LocalTheater.finishMigration(uuid)
    
    case msg =>
      LocalTheater.log.debug("Theater agent received an unknown message: " + msg)
  }
}
