package tests

import org.scalatest.junit.JUnitSuite
import org.scalatest.junit.ShouldMatchersForJUnit
import org.junit._
import org.scalatest.FlatSpec
import org.scalatest.matchers.ShouldMatchers

import se.scalablesolutions.akka.mobile.Mobile
import se.scalablesolutions.akka.mobile.theater.LocalTheater
import se.scalablesolutions.akka.mobile.util.ClusterConfiguration
import se.scalablesolutions.akka.mobile.util.UUID

import util.Random

object UUIDTests {
  val nodeIndex = Random.nextInt(ClusterConfiguration.nodeNames.size)

  @BeforeClass def initialize() {
    Mobile.startTheater(ClusterConfiguration.nodeNames(nodeIndex)) // Local Theater
  }

  @AfterClass def close() {
    LocalTheater.shutdown()
  }
}

class UUIDTests extends JUnitSuite with ShouldMatchersForJUnit {
  import UUIDTests._

  @Test
  def testNodeInformationInUUID {
    val ref = Mobile.spawnHere[StatefulActor]
    val uuid: Long = ref.uuid.toLong

    val nodeIndexInUUID = nodeIndex + 1
    
    (uuid >> 41) should be (nodeIndexInUUID)
  }
}


