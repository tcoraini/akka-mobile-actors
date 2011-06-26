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

    atRefs = Mobile.colocateOps[GroupIdAnswererActor](3) at LocalTheater.node
    hereRefs = Mobile.colocateOps[GroupIdAnswererActor](4).here
    nextToRefs = Mobile.colocateOps[GroupIdAnswererActor](2) nextTo (hereRefs(0))
  }

  @AfterClass def close() {
    LocalTheater.shutdown()
  }
}

class LocallyColocatedActorsTests extends JUnitSuite with ShouldMatchersForJUnit {
  import LocallyColocatedActorsTests._

//  @Test
  def testLocallyColocatedActorsSize {
    nextToRefs should have size (2)
    atRefs should have size (3)
    hereRefs should have size (4)
  }

//  @Test
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
    
//  @Test
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
  
//  @Test
  def isGroupManagementProperlySet {
    import GroupManagement.{groups => grps}
    grps should have size (3)
    
    val listNextTo = grps.get(nextToRefs(0).groupId.get)
    val listAt = grps.get(atRefs(0).groupId.get)
    val listHere = grps.get(hereRefs(0).groupId.get)
    
    listNextTo should not be (None)
    listAt should not be (None)
    listHere should not be (None)

    listNextTo.get should have size (2)
    listAt.get should have size (3)
    listHere.get should have size (4)

    listNextTo.get should contain (nextToRefs(0))
    listNextTo.get should contain (nextToRefs(1))

    listAt.get should contain (atRefs(0))
    listAt.get should contain (atRefs(1))
    listAt.get should contain (atRefs(2))

    listHere.get should contain (hereRefs(0))
    listHere.get should contain (hereRefs(1))
    listHere.get should contain (hereRefs(2))
    listHere.get should contain (hereRefs(3))
  }

//  @Test
  def testRemotelyColocatedByClassNameActors {
    val refs = Mobile.colocateOps[GroupIdAnswererActor](3) at thatNode
    
    refs(0) should not be ('local)
    refs(1) should not be ('local)
    refs(2) should not be ('local)
    
    // Checking that the group id of the actors are equal in the remote node
    val requestIds = for (ref <- refs) yield RemoteActorsTesting.testRemoteActor(ref, GroupId)
    def answers: Seq[Option[Any]] = for (id <- requestIds) yield RemoteActorsTesting.getAnswer(id)
    while (!answers.forall(_.isDefined)) { Thread.sleep(500) }
    val groupId = answers(0).get
    groupId should not be (None)
    for (answer <- answers) answer.get should equal (groupId)
  }

//  @Test
  def testRemotelyColocatedWithFactoriesActors {
    val refs = Mobile.colocateOps(
      () => new GroupIdAnswererActor,
      () => new GroupIdAnswererActor,
      () => new GroupIdAnswererActor) at thatNode
    
    while (!refs.forall(!_.isLocal)) { Thread.sleep(300) }

    refs(0) should not be ('local)
    refs(1) should not be ('local)
    refs(2) should not be ('local)
    
    // Checking that the group id of the actors are equal in the remote node
    val requestIds = for (ref <- refs) yield RemoteActorsTesting.testRemoteActor(ref, GroupId)
    def answers: Seq[Option[Any]] = for (id <- requestIds) yield RemoteActorsTesting.getAnswer(id)
    while (!answers.forall(_.isDefined)) { Thread.sleep(500) }
  
    val groupId = answers(0).get
    groupId should not be (None)
    for (answer <- answers) answer.get should equal (groupId)
  }

  @Test
  def testGroupMigration {
    val refs: List[MobileActorRef] = Mobile.colocateOps[GroupIdAnswererActor](3).here
    val groupId = refs(0).groupId

    refs(0).node should be ('local)
    refs(1).node should be ('local)
    refs(2).node should be ('local)

    refs(1) ! MoveGroupTo(thatNode.hostname, thatNode.port)

    while(refs(0).isLocal) { Thread.sleep(100) }

    refs(0).node should not be ('local)
    refs(1).node should not be ('local)
    refs(2).node should not be ('local)
    
    // Checking that the group id of the actors are maintained in the new node
    val requestIds = for (ref <- refs) yield RemoteActorsTesting.testRemoteActor(ref, GroupId)
    def answers: Seq[Option[Any]] = for (id <- requestIds) yield RemoteActorsTesting.getAnswer(id)
    while (!answers.forall(_.isDefined)) { Thread.sleep(500) }
    for (answer <- answers) answer.get should equal (groupId)
  }

//  @Test
  def testRemoteColocatedActorsNextToSingleRef {
    // Implicit conversion for co-located spawn with factories
    import Mobile._

    val controlRef = Mobile.spawnAt(new GroupIdAnswererActor, thatNode)
    
    while(controlRef.isLocal) { Thread.sleep(100) }

    // Refs spawned by class
    val refsC: List[MobileActorRef] = Mobile.colocateNextTo[GroupIdAnswererActor](2, controlRef)
    // Refs spawned by factories
    val refsF: List[MobileActorRef] = Mobile.colocateNextTo(new GroupIdAnswererActor, new GroupIdAnswererActor)(controlRef)

    while(refsF(1).isLocal) { Thread.sleep(100) }

    refsC(0).node should be === controlRef.node
    refsC(1).node should be === controlRef.node
    refsF(0).node should be === controlRef.node
    refsF(1).node should be === controlRef.node

    val allRefs = refsC ::: refsF
    // Checking that the group id of the actors are maintained in the new node
    val requestIds = for (ref <- (controlRef :: allRefs)) yield RemoteActorsTesting.testRemoteActor(ref, GroupId)
    def answers: Seq[Option[Any]] = for (id <- requestIds) yield RemoteActorsTesting.getAnswer(id)
    while (!answers.forall(_.isDefined)) { Thread.sleep(500) }

    val groupId = answers(0).get
    groupId should not equal None
    for (answer <- answers) answer.get should equal (groupId)
  }

//  @Test
  def testRemoteColocatedActorsNextToExistingGroup {
    // Implicit conversion for co-located spawn with factories
    import Mobile._

    val controlRefs = Mobile.colocateAt(new GroupIdAnswererActor, new GroupIdAnswererActor)(thatNode)
    
    while(controlRefs(0).isLocal) { Thread.sleep(100) }

    // Refs spawned by class
    val refsC: List[MobileActorRef] = Mobile.colocateNextTo[GroupIdAnswererActor](2, controlRefs(0))
    // Refs spawned by factories
    val refsF: List[MobileActorRef] = Mobile.colocateNextTo(new GroupIdAnswererActor, new GroupIdAnswererActor)(controlRefs(1))

    while(refsF(1).isLocal) { Thread.sleep(100) }

    refsC(0).node should be === controlRefs(0).node
    refsC(1).node should be === controlRefs(0).node
    refsF(0).node should be === controlRefs(0).node
    refsF(1).node should be === controlRefs(0).node

    val allRefs = refsC ::: refsF
    // Checking that the group id of the actors are maintained in the new node
    val requestIds = for (ref <- (controlRefs ::: allRefs)) yield RemoteActorsTesting.testRemoteActor(ref, GroupId)
    def answers: Seq[Option[Any]] = for (id <- requestIds) yield RemoteActorsTesting.getAnswer(id)
    while (!answers.forall(_.isDefined)) { Thread.sleep(500) }

    val groupId = answers(0).get
    groupId should not equal None
    for (answer <- answers) answer.get should equal (groupId)
  }

//  @Test
  def testLocalColocatedActorsNextToSingleRef {
    // Implicit conversion for co-located spawn with factories
    import Mobile._

    val controlRef = Mobile.spawnHere(new GroupIdAnswererActor)
    
    // Refs spawned by class
    val refsC: List[MobileActorRef] = Mobile.colocateNextTo[GroupIdAnswererActor](2, controlRef)
    // Refs spawned by factories
    val refsF: List[MobileActorRef] = Mobile.colocateNextTo(new GroupIdAnswererActor, new GroupIdAnswererActor)(controlRef)

    refsC(0).node should be === controlRef.node
    refsC(1).node should be === controlRef.node
    refsF(0).node should be === controlRef.node
    refsF(1).node should be === controlRef.node

    val allRefs = refsC ::: refsF
    val groupId = controlRef.groupId
    groupId should not equal None
    for (ref <- allRefs) ref.groupId should equal (groupId)
  }

//  @Test
  def testLocalColocatedActorsNextToExistingGroup {
    // Implicit conversion for co-located spawn with factories
    import Mobile._

    val controlRefs = Mobile.colocateHere(new GroupIdAnswererActor, new GroupIdAnswererActor)
    val groupId = controlRefs(0).groupId

    groupId should not equal None
    controlRefs(1).groupId should equal (groupId)
    
    // Refs spawned by class
    val refsC: List[MobileActorRef] = Mobile.colocateNextTo[GroupIdAnswererActor](2, controlRefs(0))
    // Refs spawned by factories
    val refsF: List[MobileActorRef] = Mobile.colocateNextTo(new GroupIdAnswererActor, new GroupIdAnswererActor)(controlRefs(1))

    refsC(0).node should be === controlRefs(0).node
    refsC(1).node should be === controlRefs(0).node
    refsF(0).node should be === controlRefs(0).node
    refsF(1).node should be === controlRefs(0).node

    val allRefs = refsC ::: refsF
    for (ref <- allRefs) ref.groupId should equal (groupId)
  }
  
//  @Test
  def testPartialGroupMigration {
    val refs: List[MobileActorRef] = Mobile.colocateOps[SleepyActor](3).here
    
    refs(0) ! Sleep(8000)
    refs(1) ! Sleep(3000)
    
    refs(2) ! MoveGroupTo(thatNode.hostname, thatNode.port)

    while(refs(2).isLocal) { Thread.sleep(100) }
    
    refs(1).node should not be ('local)
    refs(2).node should not be ('local)
    
    refs(0).node should be ('local)
  }

//  @Test
  def isActorRemovedFromGroupAfterMigration {
    val actorToMigrate = hereRefs(2)
    val groupId = hereRefs(0).groupId.get
    def group = GroupManagement.groups.get(groupId)
    
    // Before migration
    group should not be (None)
    group.get should have size (4)
    group.get should contain (actorToMigrate)
    actorToMigrate.groupId should equal (Some(groupId))
    
    // Migration
    actorToMigrate ! MoveTo(thatNode.hostname, thatNode.port)
    while(actorToMigrate.isLocal) { Thread.sleep(100) }

    // After migration
    group.get should have size (3)
    group.get should not contain (actorToMigrate)
    actorToMigrate.groupId should equal (None)
    
    // Ensuring that the groupId of the actor, on the remote node, is None
    val requestId = RemoteActorsTesting.testRemoteActor(actorToMigrate, GroupId)
    def answer = RemoteActorsTesting.getAnswer(requestId)
    while (!answer.isDefined) { Thread.sleep(500) }
    answer.get should equal (None)
  }

}
