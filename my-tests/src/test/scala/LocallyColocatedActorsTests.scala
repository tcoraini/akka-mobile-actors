package tests

import org.scalatest.junit.JUnitSuite
import org.scalatest.junit.ShouldMatchersForJUnit
import org.junit._
import org.scalatest.FlatSpec
import org.scalatest.matchers.ShouldMatchers

import se.scalablesolutions.akka.actor.Actor
import se.scalablesolutions.akka.mobile.Mobile
import se.scalablesolutions.akka.mobile.actor.MobileActorRef
import se.scalablesolutions.akka.mobile.theater.LocalTheater

class LocallyColocatedActorsTests extends JUnitSuite with ShouldMatchersForJUnit {

  var nextToRefs: List[MobileActorRef] = _
  var atRefs: List[MobileActorRef] = _
  var hereRefs: List[MobileActorRef] = _

  @Before def initialize() {
    Mobile.startTheater("node_1") // Local Theater
    
    atRefs = Mobile.colocate[tests.StatefulActor](3) at LocalTheater.node
    hereRefs = Mobile.colocate[tests.StatefulActor](4).here
    nextToRefs = Mobile.colocate[tests.StatefulActor](2) nextTo (hereRefs(0))
  }

  @After def close() {
    LocalTheater.shutdown()
  }
  
  @Test
  def testLocallyColocatedActorsSize {
    nextToRefs should have size (2)
    atRefs should have size (3)
    hereRefs should have size (4)
  }

  @Test
  def testLocallyColocatedActorsGroupIds {
    nextToRefs(0).groupId should be === nextToRefs(1).groupId

    atRefs(0).groupId should be === atRefs(1).groupId
    atRefs(0).groupId should be === atRefs(2).groupId
    
    hereRefs(0).groupId should be === hereRefs(1).groupId
    hereRefs(0).groupId should be === hereRefs(2).groupId
    hereRefs(0).groupId should be === hereRefs(3).groupId

    nextToRefs(0).groupId should not be (atRefs(0).groupId)
    nextToRefs(0).groupId should not be (hereRefs(0).groupId)
    atRefs(0).groupId should not be (hereRefs(0).groupId)
  }
    
  @Test
  def testLocallyColocatedActorsIsAtLocalNode {
    nextToRefs(0).node should be ('local)
    nextToRefs(1).node should be ('local)
    
    atRefs(0).node should be ('local)
    atRefs(1).node should be ('local)
    atRefs(2).node should be ('local)

    hereRefs(0).node should be ('local)
    hereRefs(1).node should be ('local)
    hereRefs(2).node should be ('local)
    hereRefs(3).node should be ('local)
  }
}
