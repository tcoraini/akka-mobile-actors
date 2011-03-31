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
import se.scalablesolutions.akka.mobile.theater.GroupManagement
import se.scalablesolutions.akka.mobile.util.messages._

object LocallyColocatedActorsTests {
  var nextToRefs: List[MobileActorRef] = _
  var atRefs: List[MobileActorRef] = _
  var hereRefs: List[MobileActorRef] = _
  
  val thisNode = TheaterNode("ubuntu-tcoraini", 1810)
  val thatNode = TheaterNode("localhost", 2312)

  @BeforeClass def initialize() {
    Mobile.startTheater(thisNode.hostname, thisNode.port) // Local Theater

    atRefs = Mobile.colocate[tests.StatefulActor](3) at LocalTheater.node
    hereRefs = Mobile.colocate[tests.StatefulActor](4).here
    nextToRefs = Mobile.colocate[tests.StatefulActor](2) nextTo (hereRefs(0))
  }

  @AfterClass def close() {
    LocalTheater.shutdown()
  }
}

class LocallyColocatedActorsTests extends JUnitSuite with ShouldMatchersForJUnit {
  import LocallyColocatedActorsTests._

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
  
  @Test
  def isGroupManagementProperlySet {
    import GroupManagement.{groups => grps}
    grps should have size (3)
    
    val listNextTo = grps.get(nextToRefs(0).groupId.get)
    val listAt = grps.get(atRefs(0).groupId.get)
    val listHere = grps.get(hereRefs(0).groupId.get)
    
    listNextTo should not be (null)
    listAt should not be (null)
    listHere should not be (null)

    listNextTo should have size (2)
    listAt should have size (3)
    listHere should have size (4)

    listNextTo should contain (nextToRefs(0))
    listNextTo should contain (nextToRefs(1))

    listAt should contain (atRefs(0))
    listAt should contain (atRefs(1))
    listAt should contain (atRefs(2))

    listHere should contain (hereRefs(0))
    listHere should contain (hereRefs(1))
    listHere should contain (hereRefs(2))
    listHere should contain (hereRefs(3))
  }

  @Test
  def testGroupMigration {
    val refs: List[MobileActorRef] = Mobile.colocate[StatefulActor](3).here

    refs(0).node should be ('local)
    refs(1).node should be ('local)
    refs(2).node should be ('local)

    refs(1) ! MoveGroupTo(thatNode.hostname, thatNode.port)

    while(refs(0).isLocal) { Thread.sleep(100) }

    refs(0).node should not be ('local)
    refs(1).node should not be ('local)
    refs(2).node should not be ('local)
  }
  
  @Test
  def testPartialGroupMigration {
    val refs: List[MobileActorRef] = Mobile.colocate[SleepyActor](3).here
    
    refs(0) ! Sleep(8000)
    refs(1) ! Sleep(3000)
    
    refs(2) ! MoveGroupTo(thatNode.hostname, thatNode.port)

    while(refs(2).isLocal) { Thread.sleep(100) }
    
    refs(1).node should not be ('local)
    refs(2).node should not be ('local)
    
    refs(0).node should be ('local)
  }
    

  @Test
  def isActorRemovedFromGroupAfterMigration {
    val actorToMigrate = hereRefs(2)
    val groupId = hereRefs(0).groupId.get
    def group = GroupManagement.groups.get(groupId)
    
    // Before migration
    group should have size (4)
    group should contain (actorToMigrate)
    actorToMigrate.groupId should equal (Some(groupId))
    
    // Migration
    actorToMigrate ! MoveTo(thatNode.hostname, thatNode.port)
    while(actorToMigrate.isLocal) { Thread.sleep(100) }

    // After migration
    group should have size (3)
    group should not contain (actorToMigrate)
    actorToMigrate.groupId should equal (None)
  }

}
