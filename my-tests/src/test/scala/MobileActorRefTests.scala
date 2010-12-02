package tests

import org.scalatest.junit.JUnitSuite
import org.scalatest.junit.ShouldMatchersForJUnit
import org.junit.Test

import se.scalablesolutions.akka.actor.Actor
import se.scalablesolutions.akka.mobile._

class MobileActorRefTests extends JUnitSuite with ShouldMatchersForJUnit {

  @Test
  def mobileActorRefShouldOnlyBeCreatedWithMobileReference {
    val actorRef = Actor.actorOf(new StatefulActor)
    evaluating { new MobileActorRef(actorRef) } should produce [RuntimeException]
  }
}
