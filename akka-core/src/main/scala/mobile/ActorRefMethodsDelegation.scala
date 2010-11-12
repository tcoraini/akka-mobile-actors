package se.scalablesolutions.akka.mobile

import se.scalablesolutions.akka.actor.Actor
import se.scalablesolutions.akka.actor.ActorRef
import se.scalablesolutions.akka.actor.ScalaActorRef

import se.scalablesolutions.akka.dispatch._
import se.scalablesolutions.akka.stm.TransactionConfig

import se.scalablesolutions.akka.config.FaultHandlingStrategy
import se.scalablesolutions.akka.config.ScalaConfig.LifeCycle

import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference
import java.util.{Map => JMap}

trait ActorRefMethodsDelegation extends ActorRef with ScalaActorRef {
  
  protected var reference: ActorRef

  /* 
   * ActorRef
   */
  
  // Implemented methods from ActorRef that depends on attributes of the class
  // Should then forward the call to the actual reference, with the correct attribute values
  override def setReceiveTimeout(timeout: Long) = reference.setReceiveTimeout(timeout) 
  override def getReceiveTimeout(): Option[Long] = reference.receiveTimeout
  override def setTrapExit(exceptions: Array[Class[_ <: Throwable]]) = reference.setTrapExit(exceptions) 
  override def getTrapExit(): Array[Class[_ <: Throwable]] = reference.getTrapExit
  override def setFaultHandler(handler: FaultHandlingStrategy) = reference.setFaultHandler(handler)
  override def getFaultHandler(): Option[FaultHandlingStrategy] = reference.getFaultHandler
  override def setLifeCycle(lifeCycle: LifeCycle) = reference.setLifeCycle(lifeCycle)
  override def getLifeCycle(): Option[LifeCycle] = reference.getLifeCycle
  override def setDispatcher(dispatcher: MessageDispatcher) = reference.setDispatcher(dispatcher)
  override def getDispatcher(): MessageDispatcher = reference.getDispatcher
  override def compareTo(other: ActorRef) = reference.compareTo(other)
  override def getUuid() = reference.getUuid
  override def uuid = reference.uuid
  override def isBeingRestarted: Boolean = reference.isBeingRestarted
  override def isRunning: Boolean = reference.isRunning
  override def isShutdown: Boolean = reference.isShutdown
  override def isDefinedAt(message: Any): Boolean = reference.isDefinedAt(message)
  override def homeAddress: InetSocketAddress = reference.homeAddress
  override def mailboxSize = reference.mailboxSize

  // Overrided methods from AnyRef
  // Should be overrided to forward to the reference
  override def hashCode: Int = reference.hashCode
  override def equals(that: Any): Boolean = reference.equals(that)
  override def toString = reference.toString

  // Abstract methods from ActorRef
  // Should only forward the call to the actual reference
  def actorClass: Class[_ <: Actor] = reference.actorClass
  def actorClassName: String = reference.actorClassName
  def dispatcher_=(md: MessageDispatcher): Unit = reference.dispatcher_=(md)
  def dispatcher: MessageDispatcher = reference.dispatcher
  def makeRemote(hostname: String, port: Int): Unit = reference.makeRemote(hostname, port)
  def makeRemote(address: InetSocketAddress): Unit = reference.makeRemote(address)
  def makeTransactionRequired: Unit = reference.makeTransactionRequired
  def transactionConfig_=(config: TransactionConfig): Unit = reference.transactionConfig_=(config)
  def transactionConfig: TransactionConfig = reference.transactionConfig
  def homeAddress_=(address: InetSocketAddress): Unit = reference.homeAddress_=(address)
  def remoteAddress: Option[InetSocketAddress] = reference.remoteAddress
  def start: ActorRef = reference.start
  def stop: Unit = reference.stop
  def link(reference: ActorRef): Unit = reference.link(reference)
  def unlink(reference: ActorRef): Unit = reference.unlink(reference)
  def startLink(reference: ActorRef): Unit = reference.startLink(reference)
  def startLinkRemote(reference: ActorRef, hostname: String, port: Int): Unit = reference.startLinkRemote(reference, hostname, port)
  def spawn(clazz: Class[_ <: Actor]): ActorRef = reference.spawn(clazz)
  def spawnRemote(clazz: Class[_ <: Actor], hostname: String, port: Int): ActorRef = reference.spawnRemote(clazz, hostname, port)
  def spawnLink(clazz: Class[_ <: Actor]): ActorRef = reference.spawnLink(clazz)
  def spawnLinkRemote(clazz: Class[_ <: Actor], hostname: String, port: Int): ActorRef = reference.spawnLinkRemote(clazz, hostname, port)
  def supervisor: Option[ActorRef] = reference.supervisor

  // Protected methdos from ActorRef
  // These SHOULD NOT be invoked in a MobileActorRef, because there is no way we can forward the call
  // to the actual reference (unless this class goes on the 'akka' package.
  def remoteAddress_=(addr: Option[InetSocketAddress]): Unit = unsupported
  def invoke(messageHandle: MessageInvocation): Unit = unsupported
  def postMessageToMailbox(message: Any, senderOption: Option[ActorRef]): Unit = unsupported
  def postMessageToMailboxAndCreateFutureResultWithTimeout[T](
      message: Any,
      timeout: Long,
      senderOption: Option[ActorRef],
      senderFuture: Option[CompletableFuture[T]]): CompletableFuture[T] = unsupported
  def actorInstance: AtomicReference[Actor] = unsupported
  def supervisor_=(sup: Option[ActorRef]): Unit = unsupported 
  def mailbox: AnyRef = unsupported
  def mailbox_=(value: AnyRef):AnyRef = unsupported
  def handleTrapExit(dead: ActorRef, reason: Throwable): Unit = unsupported
  def restart(reason: Throwable, maxNrOfRetries: Int, withinTimeRange: Int): Unit = unsupported
  def restartLinkedActors(reason: Throwable, maxNrOfRetries: Int, withinTimeRange: Int): Unit = unsupported
  def registerSupervisorAsRemoteActor: Option[String] = unsupported
  def linkedActors: JMap[String, ActorRef] = unsupported
  def linkedActorsAsList: List[ActorRef] = unsupported

  private def unsupported = throw new UnsupportedOperationException("This method can't be invoked on a MobileActorRef.")  
  
  /* 
   * ActorRefShared
   */

  // Abstract method from ActorRefShared
  def shutdownLinkedActors: Unit = reference.shutdownLinkedActors

  /*
   * ScalaActorRef
   */
  
  // Implemented methods from ScalaActorRef
  // Should forward to the actual reference
  override def sender: Option[ActorRef] = reference.sender
  override def senderFuture(): Option[CompletableFuture[Any]] = reference.senderFuture
  
  override def !(message: Any)(implicit sender: Option[ActorRef] = None): Unit = 
    reference.!(message)(sender)
  override def !!(message: Any, timeout: Long = this.timeout)(implicit sender: Option[ActorRef] = None): Option[Any] = 
    reference.!!(message, timeout)(sender)
  override def !!![T](message: Any, timeout: Long = this.timeout)(implicit sender: Option[ActorRef] = None): Future[T] = 
    reference.!!!(message, timeout)(sender)
  override def forward(message: Any)(implicit sender: Some[ActorRef]) = 
    reference.forward(message)(sender)

}
