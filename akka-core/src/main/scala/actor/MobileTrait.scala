package se.scalablesolutions.akka.actor

import se.scalablesolutions.akka.dispatch.MessageInvocation
import se.scalablesolutions.akka.dispatch.CompletableFuture
import se.scalablesolutions.akka.dispatch.DefaultCompletableFuture


import java.util.concurrent.ConcurrentLinkedQueue

case class RetainedMessage(message: Any, sender: Option[ActorRef])
case class RetainedMessageWithFuture(message: Any, timeout: Long, sender: Option[ActorRef], senderFuture: Option[CompletableFuture[Any]])


trait MobileTrait extends ActorRef with ScalaActorRef {
  val retainedMessagesQueue = new ConcurrentLinkedQueue[RetainedMessage]
  val retainedMessagesWithFutureQueue = new ConcurrentLinkedQueue[RetainedMessageWithFuture]

  private var retainMessages = false

  def startMigration[T <: Actor](implicit format: Format[T]): Array[Byte] = {
    retainMessages = true
    ActorSerialization.toBinary(this)
  }

  def forwardRetainedMessages(to: ActorRef): Unit = {
    for (RetainedMessage(message, sender) <- retainedMessagesQueue.toArray)
      to.!(message)(sender)
  }

  //override abstract def !(message: Any)(implicit sender: Option[ActorRef] = None): Unit = {
  //  if (retainMessages) retainedMessagesQueue.add(RetainedMessage(message, sender))
  //  else super.!(message)
  //}

  override abstract def !!(message: Any, timeout: Long = this.timeout)(implicit sender: Option[ActorRef] = None): Option[Any] = {
    // TODO Tratar o caso de estar retendo mensagens. O método tem que esperar
    super.!!(message, timeout)
  }


  override abstract def postMessageToMailbox(message: Any, senderOption: Option[ActorRef]): Unit = {
    if (retainMessages) retainedMessagesQueue.add(RetainedMessage(message, senderOption))
    else super.postMessageToMailbox(message, senderOption)
  }

  override abstract def postMessageToMailboxAndCreateFutureResultWithTimeout[T](
      message: Any,
      timeout: Long,
      senderOption: Option[ActorRef],
      senderFuture: Option[CompletableFuture[T]]): CompletableFuture[T] = {
    
    if (retainMessages) {
      val future = if (senderFuture.isDefined) senderFuture.get
                   else new DefaultCompletableFuture[T](timeout)
      // TODO tentar acertar o tipo T do CompletableFuture
      retainedMessagesWithFutureQueue.add(RetainedMessageWithFuture(message, timeout, senderOption, Some(future.asInstanceOf[CompletableFuture[Any]])))
      //retainedMessagesWithFutureQueue.add(RetainedMessageWithFuture(message, timeout, senderOption, Some(future)))
      future
    } else super.postMessageToMailboxAndCreateFutureResultWithTimeout(message, timeout, senderOption, senderFuture)
  }

  override abstract def invoke(messageHandle: MessageInvocation): Unit = {
      if (!retainMessages) super.invoke(messageHandle)
  }
}
