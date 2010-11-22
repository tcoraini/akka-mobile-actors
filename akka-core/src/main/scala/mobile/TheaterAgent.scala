package se.scalablesolutions.akka.mobile

import se.scalablesolutions.akka.actor.Actor

class TheaterAgent extends Actor {
  
  def receive = {
    case MovingActor(bytes, sender) =>
      Theater.receiveActor(bytes, sender)
      //val uuid = Theater.receiveActor(bytes, sender)
      //self.reply(MobileActorRegistered(uuid))

    case StartMobileActorRequest(constructor) =>
      val ref = Theater.startLocalActor(constructor)
      self.reply(StartMobileActorReply(ref.uuid))

    case MobileActorRegistered(uuid) =>
      Theater.finishMigration(uuid)
    
    case msg =>
      Theater.log.debug("Theater agent received an unknown message: " + msg)
  }
}
