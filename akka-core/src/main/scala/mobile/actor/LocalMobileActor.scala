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

import se.scalablesolutions.akka.mobile.dispatcher.MobileMessageDispatcher
import se.scalablesolutions.akka.mobile.util.messages._

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit


case class RetainedMessage(message: Any, sender: Option[ActorRef])
case class RetainedMessageWithFuture(message: Any, timeout: Long, sender: Option[ActorRef], senderFuture: Option[CompletableFuture[Any]])

// TODO Estender LocalActorRef diretamente nao seria melhor?
trait LocalMobileActor extends MobileReference {
  val retainedMessagesQueue = new ConcurrentLinkedQueue[RetainedMessage]
  val retainedMessagesWithFutureQueue = new ConcurrentLinkedQueue[RetainedMessageWithFuture]

  private var _isMigrating = false
  def isMigrating = _isMigrating
  
  if (!isRunning) {
    dispatcher = MobileDispatchers.globalMobileExecutorBasedEventDrivenDispatcher
  }

  if (!actor.isInstanceOf[MobileActor])
    throw new RuntimeException("MobileActorRef should be used only with a MobileActor")
  
  // TODO: APAGAR
  private[this] def serializedActor[T <: Actor](implicit format: Format[T]): Array[Byte] = {
    if (!isMigrating) throw new RuntimeException("This actor is not migrating. You should send it a 'MoveTo' message first")
    else ActorSerialization.toBinary(this)
  }

  def forwardRetainedMessages(to: ActorRef): Unit = {
    for (RetainedMessage(message, sender) <- retainedMessagesQueue.toArray)
      to.!(message)(sender)
    // TODO PROBLEMA: preciso encaminhar a mensagem, mas preciso que a resposta seja colocada no future que eu já tenho.
    // Como fazer?
    //for (RetainedMessageWithFuture(message, timeout, sender, senderFuture)
  }

  abstract override def actor: MobileActor = super.actor.asInstanceOf[MobileActor]

  abstract override def start(): ActorRef = {
    actor.asInstanceOf[MobileActor].mobileSelf = Some(externalReference)
    ensureDispatcherIsMobile()
    super.start()
  }

  abstract override def !!(message: Any, timeout: Long = this.timeout)(implicit sender: Option[ActorRef] = None): Option[Any] = {
    // TODO Verificar como fica isso. O próprio ActorRef será responsável por esperar pelo Futuro ser completado com o
    // resultado e então devolver a resposta. Mas o ActorRef será serializado e depois, talvez inutilizado. Vai dar certo?
    super.!!(message, timeout)
  }

  abstract override def postMessageToMailbox(message: Any, senderOption: Option[ActorRef]): Unit = {
    if (isMigrating) retainedMessagesQueue.add(RetainedMessage(message, senderOption))
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
  //
  // abstract override def invoke(messageHandle: MessageInvocation): Unit = {
  //   // Don't process messages during migration, put them in the retained messages queue
  //   if (isMigrating) {
  //     messageHandle.senderFuture match {
  //     // Message without Future
  //     case None => 
  //       retainedMessagesQueue.add(RetainedMessage(messageHandle.message, messageHandle.sender))
  //     // Message with Future
  //     case Some(future) =>
  //       // Converting back from Nanoseconds to Milliseconds
  //       val timeout = TimeUnit.NANOSECONDS.toMillis(future.timeoutInNanos)
  //       retainedMessagesWithFutureQueue.add(RetainedMessageWithFuture(messageHandle.message, timeout, messageHandle.sender, messageHandle.senderFuture))
  //     }
  //   }
  //   else {
  //     super.invoke(messageHandle)
  //   }
  // }

  protected[mobile] def initMigration(): Unit = {
    _isMigrating = true
    actor.beforeMigration()
  }

  def endMigration(newActor: ActorRef): Unit = {
    for (RetainedMessage(message, sender) <- retainedMessagesQueue.toArray)
      newActor.!(message)(sender)

    actor.afterMigration()
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

  def isLocal = true
}
