package se.scalablesolutions.akka.mobile.actor

import se.scalablesolutions.akka.mobile.theater.Theater
import se.scalablesolutions.akka.mobile.theater.LocalTheater
import se.scalablesolutions.akka.mobile.theater.TheaterNode
import se.scalablesolutions.akka.mobile.theater.ReferenceManagement
import se.scalablesolutions.akka.mobile.serialization.DefaultActorFormat
import se.scalablesolutions.akka.mobile.nameservice.NameService
import se.scalablesolutions.akka.mobile.Mobile


import se.scalablesolutions.akka.actor.Actor
import se.scalablesolutions.akka.actor.Actor._
import se.scalablesolutions.akka.actor.ActorRef
import se.scalablesolutions.akka.actor.LocalActorRef
import se.scalablesolutions.akka.actor.RemoteActorRef
import se.scalablesolutions.akka.actor.ScalaActorRef
import se.scalablesolutions.akka.actor.ActorSerialization

import se.scalablesolutions.akka.remote.RemoteClient

import se.scalablesolutions.akka.dispatch._
import se.scalablesolutions.akka.stm.TransactionConfig
import se.scalablesolutions.akka.util.Logging

import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference
import java.util.{Map => JMap}

object MobileActorRef {

  /**
   * Returns a mobile reference that represents the local reference provided. If a mobile
   * reference already exists for this actor (which would be a proxy for a remote mobile
   * actor), then it is updated with this new reference.
   */
  def apply(reference: MobileReference): MobileActorRef = {
    ReferenceManagement.get(reference.uuid) match {
      case Some(mobileRef) =>
        mobileRef.switchActorRef(reference)
        mobileRef

      case None =>
        register(new MobileActorRef(reference))
    }
  }
  
  /**
   * Creates a local reference for the mobile actor instantiated with the factory provided.
   */
  def apply(factory: => MobileActor): MobileActorRef = {
    val localRef = new LocalActorRef(() => factory) with LocalMobileActor
    register(new MobileActorRef(localRef))
  }
  
  /**
   * Creates a local reference for a mobile actor of the class specified.
   */
  def apply(clazz: Class[_ <: MobileActor]): MobileActorRef = {
    val localRef = new LocalActorRef(clazz) with LocalMobileActor
    register(new MobileActorRef(localRef))
  }

  /**
   * Creates a local reference for a mobile actor which the class has the name specified.
   */
  /*def apply(classname: String): MobileActorRef = {
    val clazz = Class.forName(classname).asInstanceOf[Class[_ <: MobileActor]]
    MobileActorRef(clazz)
  }*/
  
  /**
   * Creates a reference from the UUID of the actor. The reference can be either local or remote.
   */
  def apply(uuid: String): Option[MobileActorRef] = {
    ReferenceManagement.get(uuid) match {
      // Actor with this uuid is local
      case Some(reference) => Some(reference)
      
      case None => NameService.get(uuid) match {
        case Some(node) => Some(MobileActorRef(uuid, node.hostname, node.port)) // Proxy for remote mobile actor

        case None => None // Actor not found
      }
    }
  }
  
  /**
   * Creates a remote reference for the actor with the specified UUID running in the Theater at hostname:port.
   */
  def apply(uuid: String, hostname: String, port: Int, timeout: Long = Actor.TIMEOUT): MobileActorRef = {
    ReferenceManagement.get(uuid) match {
      case Some(reference) => 
        reference.updateRemoteAddress(TheaterNode(hostname, port))
        reference

      case None =>
        val remoteRef = remoteMobileActor(uuid, hostname, port, timeout)
        register(new MobileActorRef(remoteRef))
    }
  }
  
  /**
   * Creates a reference which mixes in the RemoteMobileActor trait. It will be used
   * as a proxy for a mobile actor remotely located
   */
  private def remoteMobileActor(
      uuid: String, 
      hostname: String, 
      port: Int, 
      timeout: Long = Actor.TIMEOUT): RemoteMobileActor = {

    new RemoteActorRef(uuid, uuid, hostname, port, timeout, None) with RemoteMobileActor
  }
  
  /* Registers the mobile reference in the ReferenceManagement */
  private def register(reference: MobileActorRef): MobileActorRef = {
    ReferenceManagement.put(reference.uuid, reference)
    reference
  }
}

class MobileActorRef private(protected var reference: MobileReference) extends MethodDelegation with Logging {
  
  reference.externalReference = this

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

  private var _isMigrating = false
  
  private var _migratingTo: Option[TheaterNode] = None

  private var _homeTheater: Theater = LocalTheater
  
  def isLocal = reference.isLocal

  def isMigrating = _isMigrating

  def migratingTo: Option[TheaterNode] = _migratingTo

  def homeTheater = _homeTheater

  def homeTheater_=(theater: Theater) = {
    if (isLocal) {
      _homeTheater = theater
      this.homeAddress = new InetSocketAddress(theater.hostname, theater.port)
    }
  }

  /**
   * Changes the actor reference behind this proxy.
   * Returns true if the new actor is local, false otherwise.
   */ 
  private def switchActorRef(newRef: MobileReference): Unit = { // TODO encerrar algumas coisas na referencia anterior?
    reference.stop
    reference = newRef
    reference.externalReference = this

    val label = if (reference.isLocal) "local" else "remote"
    log.debug("Switching mobile reference for actor with UUID [%s] to a %s reference.", uuid, label)  
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
      val remoteActorRef = MobileActorRef.remoteMobileActor(uuid, destination.hostname, destination.port, reference.timeout)
      
      reference.asInstanceOf[LocalMobileActor].endMigration(remoteActorRef)
      switchActorRef(remoteActorRef)

      _isMigrating = false
      _migratingTo = None
    }
  }

  def updateRemoteAddress(newAddress: TheaterNode): Unit = {
    val newReference = 
      if (newAddress.isLocal) 
        throw new RuntimeException("THIS SHOULD NOT BE HAPPENING. THE REFERENCE SHOULD BE UPDATED AT THE MIGRATION") // TODO
      else 
        MobileActorRef.remoteMobileActor(uuid, newAddress.hostname, newAddress.port, timeout)

    switchActorRef(newReference)
  }
  

}
