package se.scalablesolutions.akka.mobile.actor

import se.scalablesolutions.akka.mobile.dispatcher.MobileDispatchers

import se.scalablesolutions.akka.actor.ActorRef
import se.scalablesolutions.akka.actor.ScalaActorRef
import se.scalablesolutions.akka.actor.Actor
import se.scalablesolutions.akka.actor.Format
import se.scalablesolutions.akka.actor.ActorSerialization

import se.scalablesolutions.akka.dispatch.MessageInvocation
import se.scalablesolutions.akka.dispatch.MessageDispatcher
import se.scalablesolutions.akka.dispatch.CompletableFuture
import se.scalablesolutions.akka.dispatch.DefaultCompletableFuture
import se.scalablesolutions.akka.dispatch.ThreadBasedDispatcher
import se.scalablesolutions.akka.dispatch.ExecutorBasedEventDrivenDispatcher
import se.scalablesolutions.akka.dispatch.ExecutorBasedEventDrivenWorkStealingDispatcher

import se.scalablesolutions.akka.stm.TransactionManagement._

import se.scalablesolutions.akka.mobile.theater.LocalTheater
import se.scalablesolutions.akka.mobile.dispatcher.MobileMessageDispatcher
import se.scalablesolutions.akka.mobile.theater.GroupManagement
import se.scalablesolutions.akka.mobile.util.messages._

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit

case class RetainedMessageWithFuture(message: Any, timeout: Long, sender: Option[ActorRef], senderFuture: Option[CompletableFuture[Any]])

// TODO Estender LocalActorRef diretamente nao seria melhor?
trait LocalMobileActor extends InnerReference with MessageHolder {
  val retainedMessagesWithFutureQueue = new ConcurrentLinkedQueue[RetainedMessageWithFuture]

  // Check some conditions that must hold for the proper instantiation of the actor
  checkConditions()

  abstract override protected[akka] def actor: MobileActor = super.actor.asInstanceOf[MobileActor]

  abstract override def start(): ActorRef = {
    // Needed during deserialization
    ensureDispatcherIsMobile()
    super.start()
  }
  
  abstract override def stop(): Unit = {
    LocalTheater.unregister(outerRef)
    super.stop()
  }

  abstract override def !(message: Any)(implicit sender: Option[ActorRef] = None): Unit = {
    // All messages received (local and remote) are registered
    val profiler = LocalTheater.profiler
    val msg = message match {
      // Message from remote actor received and forwarded by local theater
      case remoteMsg: MobileActorMessage =>
	profiler.remoteMessageArrived(uuid, remoteMsg)
	remoteMsg.message
      
      case localMsg =>
	profiler.localMessageArrived(uuid)
	localMsg
    }

    super.!(msg)
  }
	

  abstract override def !!(message: Any, timeout: Long = this.timeout)(implicit sender: Option[ActorRef] = None): Option[Any] = {
    // TODO Verificar como fica isso. O próprio ActorRef será responsável por esperar pelo Futuro ser completado com o
    // resultado e então devolver a resposta. Mas o ActorRef será serializado e depois, talvez inutilizado. Vai dar certo?
    super.!!(message, timeout)
  }

  override def postMessageToMailbox(message: Any, senderOption: Option[ActorRef]): Unit = {
    if (isMigrating) {
      holdMessage(message, senderOption)
    }
    else {
      //super.postMessageToMailbox(message, senderOption)
      val invocation = new MessageInvocation(this, message, senderOption, None, transactionSet.get)
      if (hasPriority(message))
	dispatcher.asInstanceOf[MobileMessageDispatcher].dispatchWithPriority(invocation)
      else
	invocation.send
    }
  }
  
  // TODO Deve funcionar, mas antes temos que ver o que fazer com a desseriação, onde o dispatcher é sempre usado como
  // o padrão, e não como aquele pré-existente no ator.
  
  abstract override def dispatcher: MobileMessageDispatcher = super.dispatcher.asInstanceOf[MobileMessageDispatcher]

  abstract override def dispatcher_=(md: MessageDispatcher): Unit = md match {
    case mmd: MobileMessageDispatcher => super.dispatcher_=(mmd)

    case _ => throw new RuntimeException("A mobile actor must have a MobileMessageDispatcher as its dispatcher")
  }

  abstract override def postMessageToMailboxAndCreateFutureResultWithTimeout[T](
      message: Any,
      timeout: Long,
      senderOption: Option[ActorRef],
      senderFuture: Option[CompletableFuture[T]]): CompletableFuture[T] = {
    
    if (isMigrating) {
      val future = if (senderFuture.isDefined) senderFuture.get
                   else new DefaultCompletableFuture[T](timeout)
      // TODO tentar acertar o tipo T do CompletableFuture
      retainedMessagesWithFutureQueue.add(RetainedMessageWithFuture(message, timeout, senderOption, Some(future.asInstanceOf[CompletableFuture[Any]])))
      //retainedMessagesWithFutureQueue.add(RetainedMessageWithFuture(message, timeout, senderOption, Some(future)))
      future
    } else super.postMessageToMailboxAndCreateFutureResultWithTimeout(message, timeout, senderOption, senderFuture)
  }
  

  // TODO: Acho que o MobileDispatcher não permite que seja chamado 'invoke' depois que um ator esteja
  // com o campo isMigrating == true. Mas é bom ficar de olho.


  protected[mobile] def initMigration(): Unit = {
    actor.beforeMigration()
  }

  def endMigration(newActor: ActorRef): Unit = {
    forwardHeldMessages(newActor)

    // TODO isso deve ser chamado no nó de destino
//    actor.afterMigration()
  }
  
  private def checkConditions(): Unit = {
  // TODO comentei essas linhas por causa do metodo ensureDispatcherIsMobile, chamado sempre no start.
  // Creio que nao havera problemas
//    if (!isRunning) {
//      dispatcher = MobileDispatchers.globalMobileExecutorBasedEventDrivenDispatcher
//    }

    if (!actor.isInstanceOf[MobileActor]) {
      throw new RuntimeException("MobileActorRef should be used only with a MobileActor")
    }
  }

  private def hasPriority(message: Any): Boolean = message match {
    case m: MoveTo => true

    case _ => false
  }

  /**
   * When a mobile actor is started, we have to ensure it is running with a proper mobile
   * dispatcher.
   */
  private def ensureDispatcherIsMobile(): Unit = super.dispatcher match {
    case mmd: MobileMessageDispatcher => ()

    case _ => dispatcher = MobileDispatchers.globalMobileExecutorBasedEventDrivenDispatcher
  }

  override def groupId: Option[String] = actor.groupId
  
  override protected[mobile] def groupId_=(id: Option[String]) {
    // Removes this actor from the old group id, if it is not None
    groupId.foreach(GroupManagement.remove(outerRef, _))
    // Inserts this actor in the new group id, if it is not None
    id.foreach(GroupManagement.insert(outerRef, _))
    actor.groupId = id
  }

  protected[actor] def isLocal = true
  
  protected[actor] def node = LocalTheater.node
}
