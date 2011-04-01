package tests

import org.scalatest.junit.JUnitSuite
import org.scalatest.junit.ShouldMatchersForJUnit
import org.junit._
import org.scalatest.FlatSpec
import org.scalatest.matchers.ShouldMatchers

import se.scalablesolutions.akka.mobile.Mobile
import se.scalablesolutions.akka.mobile.actor.MobileActorRef
import se.scalablesolutions.akka.mobile.theater.LocalTheater
import se.scalablesolutions.akka.mobile.theater.TheaterNode
import se.scalablesolutions.akka.mobile.util.messages._

import se.scalablesolutions.akka.util.UUID

object MobileActorTests {

  val thisNode = TheaterNode("ubuntu-tcoraini", 1810)
  val thatNode = TheaterNode("localhost", 2312)

  @BeforeClass def initialize() {
    Mobile.startTheater(thisNode.hostname, thisNode.port) // Local Theater
  }

  @AfterClass def close() {
    LocalTheater.shutdown()
  }
}

class MobileActorTests extends JUnitSuite with ShouldMatchersForJUnit {
  import MobileActorTests._

  @Test
  def testReplyToSender {
    val remoteRef = Mobile.spawnAt[AnswererActor](thatNode)
    
    val requestId = RemoteActorsTesting.testRemoteActor(remoteRef, 12)

    def answer = RemoteActorsTesting.getAnswer(requestId)
    while (!answer.isDefined) { Thread.sleep(500) }
    answer.get should equal (144)
  }
}
