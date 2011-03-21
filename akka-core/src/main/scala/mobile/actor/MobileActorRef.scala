package se.scalablesolutions.akka.mobile.actor

import se.scalablesolutions.akka.mobile.theater.Theater
import se.scalablesolutions.akka.mobile.theater.LocalTheater
import se.scalablesolutions.akka.mobile.theater.TheaterNode
import se.scalablesolutions.akka.mobile.theater.ReferenceManagement
import se.scalablesolutions.akka.mobile.theater.GroupManagement
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
  def apply(reference: InnerReference): MobileActorRef = {
    ReferenceManagement.get(reference.uuid) match {
      case Some(mobileRef) =>
        mobileRef.switchActorRef(reference)
	if (reference.isLocal) {
	  LocalTheater.register(mobileRef)
	}
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
  def apply(
      uuid: String, 
      hostname: String, 
      port: Int, 
      detached: Boolean = false,
      timeout: Long = Actor.TIMEOUT): MobileActorRef = {
    
    ReferenceManagement.get(uuid) match {
      case Some(reference) => 
        reference.updateRemoteAddress(TheaterNode(hostname, port))
        reference

      case None =>
        val remoteRef = remoteMobileActor(uuid, hostname, port, timeout, detached)
        register(new MobileActorRef(remoteRef), detached)
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
      timeout: Long = Actor.TIMEOUT,
      detached: Boolean = false): RemoteMobileActor = {

    if (!detached) 
      new RemoteActorRef(uuid, uuid, hostname, port, timeout, None) with RemoteMobileActor
    else 
      new RemoteActorRef(uuid, uuid, hostname, port, timeout, None) with RemoteMobileActor with DetachedRemoteActor
  }

  /* Registers the mobile reference in the ReferenceManagement */
  private def register(reference: MobileActorRef, detached: Boolean = false): MobileActorRef = {
    ReferenceManagement.put(reference.uuid, reference, detached)
    if (reference.isLocal) {
      LocalTheater.register(reference)
    }
    reference
  }
}

class MobileActorRef private(protected var innerRef: InnerReference) extends MethodDelegation with Logging {
  
  innerRef.outerRef = this

  /* DEBUG ONLY */
/*  def retained: java.util.concurrent.ConcurrentLinkedQueue[RetainedMessage] = 
    if (isLocal) 
      innerRef.asInstanceOf[LocalMobileActor].retainedMessagesQueue
    else
      throw new RuntimeException("Não existem mensagens retidas para atores remotos")
*/
  def mb: java.util.Queue[se.scalablesolutions.akka.dispatch.MessageInvocation] =
    if (isLocal)
      innerRef.mailbox.asInstanceOf[java.util.Queue[se.scalablesolutions.akka.dispatch.MessageInvocation]]
    else
      throw new RuntimeException("Não existe mailbox para atores remotos")
  /* * * * * */

  private var _isMigrating = false
  
  private var _migratingTo: Option[TheaterNode] = None

  private var _homeTheater: Theater = LocalTheater
  
  private var _groupId: Option[String] = None

  def isMigrating = _isMigrating

  def migratingTo: Option[TheaterNode] = _migratingTo
  
  private def homeTheater = _homeTheater
  protected[mobile] def homeTheater_=(theater: Theater) = {
    if (isLocal) {
      _homeTheater = theater
      // TODO verificar se isso estava sendo usado. Em caso afirmativo, tem que atualizar o homeAddress
      // para o valor inicial de homeTheater
      //this.homeAddress = new InetSocketAddress(theater.hostname, theater.port)
    }
  }

  def groupId = _groupId
  def groupId_=(id: Option[String]) {
    // Removes this actor from the old group id, if it is not None
    groupId.foreach(GroupManagement.remove(this, _))
    // Inserts this actor in the new group id, if it is not None
    id.foreach(GroupManagement.insert(this, _))
    _groupId = id
  }

  def node: TheaterNode = innerRef.node
  
  def isLocal = innerRef.isLocal

  // To be called by the actor when it receives a MoveTo message
  private[mobile] def moveTo(hostname: String, port: Int): Unit = {
    LocalTheater.migrate(this, TheaterNode(hostname, port))
  }

  /**
   * Changes the actor reference behind this proxy.
   * Returns true if the new actor is local, false otherwise.
   */ 
  protected def switchActorRef(newRef: InnerReference): Unit = { // TODO encerrar algumas coisas na referencia anterior?
    innerRef.stop
    innerRef = newRef
    innerRef.outerRef = this

    val label = if (innerRef.isLocal) "local" else "remote"
    log.debug("Switching mobile reference for actor with UUID [%s] to a %s reference.", uuid, label)  
  }

  /* TODO protected[mobile]*/ def startMigration(hostname: String, port: Int): Array[Byte] = {
    if (!isLocal) throw new RuntimeException("The method 'startMigration' should be call only on local actors")
    
    _isMigrating = true
    _migratingTo = Some(TheaterNode(hostname, port))
    
    // The mailbox won't be serialized if the actor has not been started yet. In this case, there will be
    // no messages in it's mailbox. This was used in a previous form of actor remote spawn, where the actor
    // was no started before being serialized. But still makes sense, and maybe will be used in the future.
    val serializeMailbox = 
      if (isRunning) {
        // Sinalizing the start of the migration process
        innerRef.asInstanceOf[LocalMobileActor].initMigration()
        true
      } else false

    ActorSerialization.toBinary(innerRef, serializeMailbox)(DefaultActorFormat)
  }

  protected[mobile] def endMigration(): Unit = {
    if (isLocal && isMigrating) {
      val destination = migratingTo.get
      val remoteActorRef = MobileActorRef.remoteMobileActor(uuid, destination.hostname, destination.port, innerRef.timeout)
      
      innerRef.asInstanceOf[LocalMobileActor].endMigration(remoteActorRef)
      switchActorRef(remoteActorRef)

      _isMigrating = false
      _migratingTo = None
    }
  }

  protected[mobile] def updateRemoteAddress(newAddress: TheaterNode): Unit = {
    log.debug("Updating the remote address for actor with UUID [%s] to theater %s.", uuid, TheaterNode(newAddress.hostname, newAddress.port).format)
    val newReference =
      if (newAddress.isLocal)
        throw new RuntimeException("THIS SHOULD NOT BE HAPPENING. THE REFERENCE SHOULD BE UPDATED AT THE MIGRATION") // TODO
      else
        MobileActorRef.remoteMobileActor(uuid, newAddress.hostname, newAddress.port, timeout)

    switchActorRef(newReference)
  }
  

}
