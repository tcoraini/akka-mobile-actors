package tests

import se.scalablesolutions.akka.mobile.actor.MobileActor
import se.scalablesolutions.akka.actor.ActorRef
import collection.mutable.HashMap

case class TestFunction(funcName: String, expectedResult: AnyRef)

object TestableActor {
  def execute(funcName: String): AnyRef = {
    None
  }
}

trait TestableActor extends MobileActor {
  abstract override def receive = {
    val myReceive: Receive = {
      case TestFunction(funcName, expectedResult) =>
	val result = TestableActor.execute(funcName)
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

case class Inquire(requestId: String, who: ActorRef, question: Any)
case class Question(requestId: String, question: Any)
case class Answer(requestId: String, answer: Any)

class InquirerActor extends MobileActor with Printer {
  private val requests = new HashMap[String, Any]
  
  def receive = {
    case Inquire(requestId, who, question) =>
      who ! Question(requestId, question)
  
    case Answer(requestId, answer) =>
      requests.put(requestId, answer)
  }

  def getAnswer(requestId: String): Option[Any] = requests.get(requestId)
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
