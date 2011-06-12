package se.scalablesolutions.akka.mobile.actor

import se.scalablesolutions.akka.mobile.theater.Theater
import se.scalablesolutions.akka.mobile.util.messages._
import se.scalablesolutions.akka.mobile.util.UUID
import se.scalablesolutions.akka.actor.Actor

import java.net.InetSocketAddress

@serializable 
trait MobileActor extends Actor {

  private[actor] var groupId: Option[String] = None

  self.uuid = UUID.newUuid.toString

  override private[akka] def apply(msg: Any): Unit = {
    if (specialBehavior.isDefinedAt(msg)) specialBehavior(msg)
    else super.apply(msg)
  }

  private val specialBehavior: Receive = {
    case MoveTo(hostname, port) =>
      outerRef.foreach(_.moveTo(hostname, port))

    case MoveGroupTo(hostname, port) =>
      outerRef.foreach(_.moveGroupTo(hostname, port))

    case PrepareToMigrate =>
      outerRef.foreach(_.prepareToMigrate())
  }

  private def outerRef: Option[MobileActorRef] = self match {
    case inner: InnerReference => inner.outerRef match {
      case null => None
      case ref => Some(ref)
    }
    
    case _ => None
  }

  /**
   * Callbacks
   */
    
  def beforeMigration() {}

  def afterMigration() {}
}
