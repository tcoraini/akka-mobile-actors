package se.scalablesolutions.akka.mobile.actor

import se.scalablesolutions.akka.actor.ActorRef
import se.scalablesolutions.akka.actor.ScalaActorRef

import scala.collection.mutable.SynchronizedQueue

case class HeldMessage(message: Any, sender: Option[ActorRef])

trait MessageHolder {
  private val lock = new Object

  protected val heldMessages = new SynchronizedQueue[HeldMessage]

  protected def holdMessage(message: Any, senderOption: Option[ActorRef]): Unit = {
    heldMessages.enqueue(HeldMessage(message, senderOption))
  }

  protected def processHeldMessages(p: HeldMessage => Unit): Unit = lock.synchronized {
    heldMessages.foreach(p)
    heldMessages.clear()
  }

  protected def forwardHeldMessages(to: ActorRef): Unit = {
    while (!heldMessages.isEmpty) {
      val hm = heldMessages.dequeue()
      to.!(hm.message)(hm.sender)
    }
  }    
}
    
