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

import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference
import java.util.{Map => JMap}

class MobileActorRef(private var actorRef: ActorRef) extends ActorRef with ScalaActorRef {

  private var isActorLocal = switchActorRef(actorRef)
  
  /**
   * Changes the actor reference behind this proxy.
   * Returns true if the new actor is local, false otherwise.
   */ 
  def switchActorRef(actorRef: ActorRef): Boolean = {
    this.actorRef = actorRef

    actorRef match {
      case _: MobileLocalActorRef => true
      case _ => false
    }
  }
  
  // TODO Não seria melhor uma mensagem MigrateTo que essa classe interceptaria e desencadearia esse processo?
  def migrateTo(hostname: String, port: Int): Boolean = {
    if (!isActorLocal) throw new RuntimeException("The method 'migrateTo' should be call only on local actors")

    // Sinalizing the start of the migration process
    actorRef ! Migrate
    val bytes = ActorSerialization.toBinary(actorRef)(DefaultActorFormat)
    val theaterAgentName = "theater@" + hostname + ":" + port
    val theaterAgent = RemoteClient.actorFor(theaterAgentName, hostname, port)
    
    // Sending the serialized actor and waiting for the confirmation
    val confirmation = theaterAgent !! MovingActor(bytes)
    val newActorRef = confirmation match {
      // TODO verificar isso do id, essa classe tem um id diferente do id da classe que ela representa
      case Some(MobileActorRegistered(id)) if id == actorRef.id =>
        Mobile.mobileActorFor(actorRef.id, hostname, port, actorRef.timeout)
      case msg => 
        throw new RuntimeException("Migration failed, confirmation not received. \n Instead received " + msg)
    }
    
    // Switching for the now RemoteActorRef serving as a proxy for the migrated actor
    switchActorRef(newActorRef)

    true
  }

  def mobid = actorRef.id
  def mobid_=(id: String) = { actorRef.id = id }

  // Normais
  def start: ActorRef = actorRef.start
  def stop: Unit = actorRef.stop
  def actorClassName: String = actorRef.actorClassName
  def actorClass: Class[_ <: Actor] = actorRef.actorClass
  def dispatcher_=(md: MessageDispatcher): Unit = actorRef.dispatcher_=(md)
  def dispatcher: MessageDispatcher = actorRef.dispatcher
  def makeTransactionRequired: Unit = actorRef.makeTransactionRequired
  def transactionConfig_=(config: TransactionConfig): Unit = actorRef.transactionConfig_=(config)
  def transactionConfig: TransactionConfig = actorRef.transactionConfig
  def makeRemote(hostname: String, port: Int): Unit = actorRef.makeRemote(hostname, port)
  def makeRemote(address: InetSocketAddress): Unit = actorRef.makeRemote(address)
  def homeAddress_=(address: InetSocketAddress): Unit = actorRef.homeAddress_=(address)
  def remoteAddress: Option[InetSocketAddress] = actorRef.remoteAddress
  def link(actorRef: ActorRef): Unit = actorRef.link(actorRef)
  def unlink(actorRef: ActorRef): Unit = actorRef.unlink(actorRef)
  def startLink(actorRef: ActorRef): Unit = actorRef.startLink(actorRef)
  def startLinkRemote(actorRef: ActorRef, hostname: String, port: Int): Unit = actorRef.startLinkRemote(actorRef, hostname, port)
  def spawn(clazz: Class[_ <: Actor]): ActorRef = actorRef.spawn(clazz)
  def spawnRemote(clazz: Class[_ <: Actor], hostname: String, port: Int): ActorRef = actorRef.spawnRemote(clazz, hostname, port)
  def spawnLink(clazz: Class[_ <: Actor]): ActorRef = actorRef.spawnLink(clazz)
  def spawnLinkRemote(clazz: Class[_ <: Actor], hostname: String, port: Int): ActorRef = actorRef.spawnLinkRemote(clazz, hostname, port)
  def supervisor: Option[ActorRef] = actorRef.supervisor
  def shutdownLinkedActors: Unit = actorRef.shutdownLinkedActors

  override def !(message: Any)(implicit sender: Option[ActorRef] = None): Unit = {
    actorRef.!(message)
  }

  override def !!(message: Any, timeout: Long = this.timeout)(implicit sender: Option[ActorRef] = None): Option[Any] = {
    actorRef.!!(message, timeout)
  }

  override def !!![T](message: Any, timeout: Long = this.timeout)(implicit sender: Option[ActorRef] = None): Future[T] = {
    actorRef.!!!(message, timeout)
  }

  override def forward(message: Any)(implicit sender: Some[ActorRef]) = {
    actorRef.forward(message)
  }
  
  // Protected
  def mailbox: AnyRef = unsupported
  def mailbox_=(value: AnyRef):AnyRef = unsupported
  def handleTrapExit(dead: ActorRef, reason: Throwable): Unit = unsupported
  def restart(reason: Throwable, maxNrOfRetries: Int, withinTimeRange: Int): Unit = unsupported
  def restartLinkedActors(reason: Throwable, maxNrOfRetries: Int, withinTimeRange: Int): Unit = unsupported
  def linkedActors: JMap[String, ActorRef] = unsupported
  def linkedActorsAsList: List[ActorRef] = unsupported
  def invoke(messageHandle: MessageInvocation): Unit = unsupported
  def remoteAddress_=(addr: Option[InetSocketAddress]): Unit = unsupported
  def supervisor_=(sup: Option[ActorRef]): Unit = unsupported
  def actorInstance: AtomicReference[Actor] = unsupported
  def registerSupervisorAsRemoteActor: Option[String] = unsupported
  def postMessageToMailbox(message: Any, senderOption: Option[ActorRef]): Unit = unsupported
  def postMessageToMailboxAndCreateFutureResultWithTimeout[T](
      message: Any,
      timeout: Long,
      senderOption: Option[ActorRef],
      senderFuture: Option[CompletableFuture[T]]): CompletableFuture[T] = unsupported
  
  private def unsupported = throw new UnsupportedOperationException("Not supported for MobileActorRef")

}
