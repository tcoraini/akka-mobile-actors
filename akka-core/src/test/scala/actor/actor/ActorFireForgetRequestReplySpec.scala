package se.scalablesolutions.akka.actor

import java.util.concurrent.{TimeUnit, CyclicBarrier, TimeoutException}
import se.scalablesolutions.akka.config.ScalaConfig._
import org.scalatest.junit.JUnitSuite
import org.junit.Test

import se.scalablesolutions.akka.dispatch.Dispatchers
import Actor._

object ActorFireForgetRequestReplySpec {
  class ReplyActor extends Actor {
    self.dispatcher = Dispatchers.newThreadBasedDispatcher(self)

    def receive = {
      case "Send" =>
        self.reply("Reply")
      case "SendImplicit" =>
        self.sender.get ! "ReplyImplicit"
    }
  }

  class CrashingTemporaryActor extends Actor {
    self.lifeCycle = Some(LifeCycle(Temporary))

    def receive = {
      case "Die" =>
        state.finished.await
        throw new Exception("Expected exception")
    }
  }

  class SenderActor(replyActor: ActorRef) extends Actor {
    self.dispatcher = Dispatchers.newThreadBasedDispatcher(self)

    def receive = {
      case "Init" => replyActor ! "Send"
      case "Reply" => {
        state.s = "Reply"
        state.finished.await
      }
      case "InitImplicit" => replyActor ! "SendImplicit"
      case "ReplyImplicit" => {
        state.s = "ReplyImplicit"
        state.finished.await
      }
    }
  }

  object state {
    var s = "NIL"
    val finished = new CyclicBarrier(2)
  }
}

class ActorFireForgetRequestReplySpec extends JUnitSuite {
  import ActorFireForgetRequestReplySpec._

  @Test
  def shouldReplyToBangMessageUsingReply = {
    state.finished.reset
    val replyActor = actorOf[ReplyActor].start
    val senderActor = actorOf(new SenderActor(replyActor)).start
    senderActor ! "Init"
    try { state.finished.await(1L, TimeUnit.SECONDS) }
    catch { case e: TimeoutException => fail("Never got the message") }
    assert("Reply" === state.s)
  }

  @Test
  def shouldReplyToBangMessageUsingImplicitSender = {
    state.finished.reset
    val replyActor = actorOf[ReplyActor].start
    val senderActor = actorOf(new SenderActor(replyActor)).start
    senderActor ! "InitImplicit"
    try { state.finished.await(1L, TimeUnit.SECONDS) }
    catch { case e: TimeoutException => fail("Never got the message") }
    assert("ReplyImplicit" === state.s)
  }

  @Test
  def shouldShutdownCrashedTemporaryActor = {
    state.finished.reset
    val actor = actorOf[CrashingTemporaryActor].start
    assert(actor.isRunning)
    actor ! "Die"
    try { state.finished.await(1L, TimeUnit.SECONDS) }
    catch { case e: TimeoutException => fail("Never got the message") }
    Thread.sleep(100)
    assert(actor.isShutdown)
  }
}
