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

  /**
   * Callbacks
   */
    
  def beforeMigration() {}

  def afterMigration() {}
}
