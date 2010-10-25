package se.scalablesolutions.akka.mobile

import se.scalablesolutions.akka.actor.ActorRef
import se.scalablesolutions.akka.actor.ScalaActorRef

import se.scalablesolutions.akka.dispatch.MessageInvocation
import se.scalablesolutions.akka.dispatch.CompletableFuture
import se.scalablesolutions.akka.dispatch.DefaultCompletableFuture

import se.scalablesolutions.akka.dispatch.ThreadBasedDispatcher
import se.scalablesolutions.akka.dispatch.ExecutorBasedEventDrivenDispatcher
import se.scalablesolutions.akka.dispatch.ExecutorBasedEventDrivenWorkStealingDispatcher

import se.scalablesolutions.akka.actor.Actor
import se.scalablesolutions.akka.actor.Format
import se.scalablesolutions.akka.actor.ActorSerialization

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit

/* Messages */
case object Migrate

case class RetainedMessage(message: Any, sender: Option[ActorRef])
case class RetainedMessageWithFuture(message: Any, timeout: Long, sender: Option[ActorRef], senderFuture: Option[CompletableFuture[Any]])

// TODO Estender LocalActorRef diretamente nao seria melhor?
trait MobileLocalActorRef extends ActorRef with ScalaActorRef {
  val retainedMessagesQueue = new ConcurrentLinkedQueue[RetainedMessage]
  val retainedMessagesWithFutureQueue = new ConcurrentLinkedQueue[RetainedMessageWithFuture]

  private var _isMigrating = false
  def isMigrating = _isMigrating
  
  if (actor.isInstanceOf[MobileActor] == false)
    throw new RuntimeException("MobileActorRef should be used only with a MobileActor")

  // Setting a specific dispatcher for mobile actors
  // TODO what if the actor is running? (like after deserialization)
  if (!isRunning)
    dispatcher = MobileDispatcher.globalMobileExecutorBasedEventDrivenDispatcher

  def serializedActor[T <: Actor](implicit format: Format[T]): Array[Byte] = {
    if (!isMigrating) throw new RuntimeException("This actor is not migrating. You should send it a 'Migrate' message first")
    else ActorSerialization.toBinary(this)
  }

  def forwardRetainedMessages(to: ActorRef): Unit = {
    for (RetainedMessage(message, sender) <- retainedMessagesQueue.toArray)
      to.!(message)(sender)
    // TODO PROBLEMA: preciso encaminhar a mensagem, mas preciso que a resposta seja colocada no future que eu já tenho.
    // Como fazer?
    //for (RetainedMessageWithFuture(message, timeout, sender, senderFuture)
  }

  abstract override def !!(message: Any, timeout: Long = this.timeout)(implicit sender: Option[ActorRef] = None): Option[Any] = {
    // TODO Verificar como fica isso. O próprio ActorRef será responsável por esperar pelo Futuro ser completado com o
    // resultado e então devolver a resposta. Mas o ActorRef será serializado e depois, talvez inutilizado. Vai dar certo?
    super.!!(message, timeout)
  }

  abstract override def postMessageToMailbox(message: Any, senderOption: Option[ActorRef]): Unit = {
    message match {
      case Migrate =>
        initMigration()
      case msg =>
        if (isMigrating) retainedMessagesQueue.add(RetainedMessage(msg, senderOption))
        else super.postMessageToMailbox(msg, senderOption)
    }
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

  abstract override def invoke(messageHandle: MessageInvocation): Unit = {
    // Don't process messages during migration, put them in the retained messages queue
    if (isMigrating) {
      //println("[" + this.actorInstance + "] Processing invoke, migrating, puting in the retained messages queue...")
      //println("\t\t\t\t Message: " + messageHandle.message)
      messageHandle.senderFuture match {
      // Message without Future
      case None => 
        retainedMessagesQueue.add(RetainedMessage(messageHandle.message, messageHandle.sender))
      // Message with Future
      case Some(future) =>
        // Converting back from Nanoseconds to Milliseconds
        val timeout = TimeUnit.NANOSECONDS.toMillis(future.timeoutInNanos)
        retainedMessagesWithFutureQueue.add(RetainedMessageWithFuture(messageHandle.message, timeout, messageHandle.sender, messageHandle.senderFuture))
      }
    }
    else {
      //println("[" + this.actorInstance + "] Processing invoke, not migrating, calling super.invoke...")
      //println("\t\t\t\t Message: " + messageHandle.message)
      super.invoke(messageHandle)
    }
  }

  private def initMigration(): Unit = {
    _isMigrating = true
    // We should stop the message dispatching, so the messages won't be processed in this actor
    // TODO What about Reactor dispatchers?
    /*dispatcher match {
      case _: ThreadBasedDispatcher =>
        dispatcher.shutdown
      
      case _: ExecutorBasedEventDrivenDispatcher =>
        dispatcherLock.lock

      case _: ExecutorBasedEventDrivenWorkStealingDispatcher =>
        dispatcherLock.tryLock
    }*/
  }
}
