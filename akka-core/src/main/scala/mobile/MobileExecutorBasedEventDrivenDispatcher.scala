/**
 * Copyright (C) 2009-2010 Scalable Solutions AB <http://scalablesolutions.se>
 */

package se.scalablesolutions.akka.mobile

import se.scalablesolutions.akka.dispatch.MessageDispatcher
import se.scalablesolutions.akka.dispatch.ThreadPoolBuilder
import se.scalablesolutions.akka.dispatch.Dispatchers
import se.scalablesolutions.akka.dispatch.MessageInvocation

import se.scalablesolutions.akka.actor.{ActorRef, IllegalActorStateException}

import java.util.Queue
import java.util.concurrent.{ConcurrentLinkedQueue, LinkedBlockingQueue}

class MobileExecutorBasedEventDrivenDispatcher(
  _name: String,
  throughput: Int = Dispatchers.THROUGHPUT,
  capacity: Int = Dispatchers.MAILBOX_CAPACITY) extends MessageDispatcher with ThreadPoolBuilder {

  mailboxCapacity = capacity

  @volatile private var active: Boolean = false

  val name = "mobile:event-driven:dispatcher:" + _name
  init

  def dispatch(invocation: MessageInvocation) = invocation.receiver match {
    case receiver: MobileLocalActorRef =>
      getMailbox(invocation.receiver).add(invocation)
      dispatch(receiver)

    case _ => throw new RuntimeException("This dispatcher can only dispatch messages for mobile actors")
  }


  /**
   * @return the mailbox associated with the actor
   */
  private def getMailbox(receiver: ActorRef) = receiver.mailbox.asInstanceOf[Queue[MessageInvocation]]

  override def mailboxSize(actorRef: ActorRef) = getMailbox(actorRef).size

  override def register(actorRef: ActorRef) = {
    if (actorRef.mailbox eq null ) {
      if (mailboxCapacity <= 0) actorRef.mailbox = new ConcurrentLinkedQueue[MessageInvocation]
      else actorRef.mailbox = new LinkedBlockingQueue[MessageInvocation](mailboxCapacity)
    }
    super.register(actorRef)
  }

  def dispatch(receiver: MobileLocalActorRef): Unit = if (active) {
    executor.execute(new Runnable() {
      def run = {
        var lockAcquiredOnce = false
        var finishedBeforeMailboxEmpty = false
        val lock = receiver.dispatcherLock
        val mailbox = getMailbox(receiver)
        // this do-while loop is required to prevent missing new messages between the end of the inner while
        // loop and releasing the lock
        do {
          if (lock.tryLock) {
            // Only dispatch if we got the lock. Otherwise another thread is already dispatching.
            lockAcquiredOnce = true
            try {
              finishedBeforeMailboxEmpty = processMailbox(receiver)
            } finally {
              lock.unlock
              if (finishedBeforeMailboxEmpty) dispatch(receiver)
            }
          }
        } while ((lockAcquiredOnce && !finishedBeforeMailboxEmpty && !mailbox.isEmpty && !receiver.isMigrating))
      }
    })
  } else {
    log.warning("%s is shut down,\n\tignoring the rest of the messages in the mailbox of\n\t%s", toString, receiver)
  }

  /**
   * Process the messages in the mailbox of the given actor.
   *
   * @return true if the processing finished before the mailbox was empty, due to the throughput constraint
   */
  def processMailbox(receiver: MobileLocalActorRef): Boolean = {
    var processedMessages = 0
    val mailbox = getMailbox(receiver)
    // Don't dispatch if the actor is migrating, the messages should stay in the mailbox to be
    // serialized
    if (receiver.isMigrating) return false
    
    var messageInvocation = mailbox.poll
    while (messageInvocation != null) {
      messageInvocation.invoke
      processedMessages += 1
      // check if we simply continue with other messages, or reached the throughput limit
      // either way, if the actor is migrating, we don't proceed with the dispatching
      if ((throughput <= 0 || processedMessages < throughput) && !receiver.isMigrating) messageInvocation = mailbox.poll
      else if (receiver.isMigrating) return false
      else {
        messageInvocation = null
        return !mailbox.isEmpty
      }
    }
    false
  }

  def start = if (!active) {
    log.debug("Starting up %s\n\twith throughput [%d]", toString, throughput)
    active = true
  }

  def shutdown = if (active) {
    log.debug("Shutting down %s", toString)
    executor.shutdownNow
    active = false
    references.clear
  }

  def ensureNotActive(): Unit = if (active) throw new IllegalActorStateException(
    "Can't build a new thread pool for a dispatcher that is already up and running")

  override def toString = "ExecutorBasedEventDrivenDispatcher[" + name + "]"

  private def init = withNewThreadPoolWithLinkedBlockingQueueWithUnboundedCapacity.buildThreadPool
}
