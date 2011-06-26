package tests

import se.scalablesolutions.akka.mobile.Mobile
import se.scalablesolutions.akka.mobile.actor.MobileActor
import se.scalablesolutions.akka.actor.ActorRef
import se.scalablesolutions.akka.util.UUID
import collection.mutable.HashMap


case class TestFunction(funcName: String, expectedResult: AnyRef)

object TestableActor {
  def execute(funcName: String, actor: ActorRef): AnyRef = {
    None
  }
}

trait TestableActor extends MobileActor {
  abstract override def receive = {
    val myReceive: Receive = {
      case TestFunction(funcName, expectedResult) =>
	val result = TestableActor.execute(funcName, self)
	self.reply(result == expectedResult)
    }
    myReceive.orElse(super.receive)
  }
}

/**
 * Actor classes used in tests
 */

trait Printer {
  protected def show(str: String): Unit = println("[" + this + "] " + str)
}  

case class Sleep(time: Long)
class SleepyActor extends MobileActor with Printer {
  def receive = {
    case Sleep(time) =>
      show("Sleepy actor going to sleep for " + time + " milliseconds...")
      Thread.sleep(time)
      show("Sleepy actor woke up!")

    case msg =>
      show("Sleepy actor received an unknown message: " + msg)
  }
}

/**
 * Remote actor testing infrastructure
 */
case class Inquire(requestId: String, who: ActorRef, question: Any)
case class Question(requestId: String, question: Any)
case class Answer(requestId: String, answer: Any)

object RemoteActorsTesting {
  private[tests] val requests = new HashMap[String, Any]

  private def newRequestId = UUID.newUuid.toString

  private val inquirer = Mobile.spawn[InquirerActor] here

  def testRemoteActor(who: ActorRef, question: Any): String = {
    val requestId = newRequestId
    inquirer ! Inquire(requestId, who, question)
    requestId
  }

  def getAnswer(requestId: String): Option[Any] = requests.get(requestId)
}

class InquirerActor extends MobileActor with Printer {
    
  def receive = {
    case Inquire(requestId, who, question) =>
      who ! Question(requestId, question)
  
    case Answer(requestId, answer) =>
      RemoteActorsTesting.requests.put(requestId, answer)
  }
}

class AnswererActor extends MobileActor with Printer {
  def receive = {
    case Question(requestId, question) =>
      question match {
	case x: Int => self.reply(Answer(requestId, x * x))
	
	case msg => show("Answerer actor received a: " + msg)
      }
  }
}

case object GroupId
class GroupIdAnswererActor extends MobileActor with Printer {
  def receive = {
    case Question(requestId, GroupId) =>
      self.reply(Answer(requestId, groupId))

    case msg => show("Answerer actor received a: " + msg)
  }
}
