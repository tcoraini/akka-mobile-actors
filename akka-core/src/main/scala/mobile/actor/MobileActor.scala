package se.scalablesolutions.akka.mobile.actor

import se.scalablesolutions.akka.mobile.theater.Theater
import se.scalablesolutions.akka.mobile.util.messages._

import se.scalablesolutions.akka.actor.Actor

import java.net.InetSocketAddress

trait MobileActor extends Actor {
  
  @transient protected[mobile] var optionMobileRef: Option[MobileActorRef] = None

  self.id = self.uuid

  // TODO so' funciona pq o codigo esta dentro do pacote akka.
  // Na trait Actor o metodo apply() e' privated[akka]
  override def apply(msg: Any): Unit = {
    if (specialBehavior.isDefinedAt(msg)) specialBehavior(msg)
    else super.apply(msg)
  }

  private val specialBehavior: Receive = {
    case MoveTo(hostname, port) => {
      println("\n\n** Mobile Self: " + optionMobileRef + " **\n\n")
      if (optionMobileRef.isDefined) {
	optionMobileRef.get.moveTo(hostname, port)
      }
    }
  }
//  become(Some(receive))
//
//  override def become(behavior: Option[Receive]) {
//    super.become(adaptBehavior(behavior))
//  }
//
//  protected def adaptBehavior(behavior: Option[Receive]): Option[Receive] = behavior match {
//    case Some(behav) => Some(specialBehaviour(behav))
//
//    case None => None
//  }
//
//  protected def specialBehaviour(behavior: Receive): Receive = {
//    case MoveTo(hostname, port) => {
//      if (mobileSelf.isDefined) {
//        mobileSelf.get.moveTo(hostname, port)
//      }
//    }
//    
//    case anyMsg =>
//      behavior(anyMsg)
//  }
  
  /**
   * Callbacks
   */
    
  def beforeMigration() {}

  def afterMigration() {}
}
