package se.scalablesolutions.akka.mobile

import se.scalablesolutions.akka.actor.Actor
import se.scalablesolutions.akka.actor.Actor._
import se.scalablesolutions.akka.actor.ActorRef
import se.scalablesolutions.akka.actor.LocalActorRef
import se.scalablesolutions.akka.actor.ScalaActorRef
import se.scalablesolutions.akka.actor.ActorSerialization

import se.scalablesolutions.akka.remote.RemoteClient

import se.scalablesolutions.akka.dispatch._
import se.scalablesolutions.akka.stm.TransactionConfig
import se.scalablesolutions.akka.util.Logging

import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference
import java.util.{Map => JMap}

class MobileActorRef(protected var reference: ActorRef) extends ActorRefMethodsDelegation with Logging {

  /* DEBUG ONLY */
  def retained: java.util.concurrent.ConcurrentLinkedQueue[RetainedMessage] = 
    if (isLocal) 
      reference.asInstanceOf[LocalMobileActor].retainedMessagesQueue
    else
      throw new RuntimeException("Não existem mensagens retidas para atores remotos")

  def mb: java.util.Queue[se.scalablesolutions.akka.dispatch.MessageInvocation] =
    if (isLocal)
      reference.mailbox.asInstanceOf[java.util.Queue[se.scalablesolutions.akka.dispatch.MessageInvocation]]
    else
      throw new RuntimeException("Não existe mailbox para atores remotos")
  /* * * * * */

  private var _isLocal = switchActorRef(reference)

  private var _isMigrating = false
  
  private var _migratingTo: Option[TheaterNode] = None
  
  def isLocal = _isLocal

  def isMigrating = _isMigrating

  def migratingTo: Option[TheaterNode] = _migratingTo

  /**
   * Changes the actor reference behind this proxy.
   * Returns true if the new actor is local, false otherwise.
   */ 
  def switchActorRef(actorRef: ActorRef): Boolean = {
    this.reference = actorRef

    _isLocal = actorRef match {
      case _: LocalMobileActor =>
        log.debug("Switching mobile reference for actor with UUID '%s' to a local reference")
        true

      case _: RemoteMobileActor => 
        log.debug("Switching mobile reference for actor with UUID '%s' to a remote reference")
        false

      case _ => throw new RuntimeException("A MobileActorRef should be created only with a mobile reference (local or remote)")
    }
    _isLocal
  }
  
  private[mobile] def startMigration(hostname: String, port: Int): Array[Byte] = {
    if (!isLocal) throw new RuntimeException("The method 'migrateTo' should be call only on local actors")
    
    _isMigrating = true
    _migratingTo = Some(TheaterNode(hostname, port))
    
    // The mailbox won't be serialized if the actor has not been started yet. In this case, we're migrating
    // a 'new' actor, that has been instantiated through a '() => MobileActor' factory
    val serializeMailbox = 
      if (isRunning) {
        // Sinalizing the start of the migration process
        reference ! Migrate
        true
      } else false

    ActorSerialization.toBinary(reference, serializeMailbox)(DefaultActorFormat)
  }

  private[mobile] def endMigration(): Unit = {
    if (isLocal && isMigrating) {
      val destination = migratingTo.get
      val remoteActorRef = Mobile.newRemoteMobileActor(uuid, destination.hostname, destination.port, reference.timeout)
      
      reference.asInstanceOf[LocalMobileActor].endMigration(remoteActorRef)
      reference.stop
      switchActorRef(remoteActorRef)

      _isMigrating = false
      _migratingTo = None
    }
  }

}
