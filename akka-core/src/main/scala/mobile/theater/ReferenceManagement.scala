package se.scalablesolutions.akka.mobile.theater

import se.scalablesolutions.akka.mobile.actor.MobileActorRef

import java.util.concurrent.ConcurrentHashMap

object ReferenceManagement {
  
  val references = new ConcurrentHashMap[String, MobileActorRef]

  private[mobile] def put(uuid: String, reference: MobileActorRef): Unit = {
    references.put(uuid, reference)
  }

  def get(uuid: String): Option[MobileActorRef] = {
    references.get(uuid) match {
      case null => None

      case reference => Some(reference)
    }
  }

  private[mobile] def remove(uuid: String): Unit = {
    references.remove(uuid)
  }
}
