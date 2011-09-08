package se.scalablesolutions.akka.mobile.theater

import se.scalablesolutions.akka.mobile.actor.MobileActor
import se.scalablesolutions.akka.mobile.actor.MobileActorRef
import se.scalablesolutions.akka.mobile.actor.AttachRefToActor
import se.scalablesolutions.akka.mobile.Mobile
import se.scalablesolutions.akka.mobile.util.messages._
import se.scalablesolutions.akka.mobile.util.UUID
import se.scalablesolutions.akka.mobile.nameservice.NameService

import se.scalablesolutions.akka.actor.ActorRef

import se.scalablesolutions.akka.remote.RemoteClient

import se.scalablesolutions.akka.util.Logging

import scala.collection.mutable.HashMap

object RemoteSpawnHelper extends Logging {

  private var requestId: Long = 1

  private[mobile] def spawnActor(
      constructor: Either[Class[_ <: MobileActor], () => MobileActor], 
      node: TheaterNode): MobileActorRef = {

    val hostname = node.hostname
    val port = node.port

    constructor match {
      case Left(clazz) => 
	val requestId = UUID.newUuid
	val ref = MobileActorRef(clazz, requestId.toString, true)
	LocalTheater.sendTo(node, StartMobileActorRequest(requestId, clazz.getName))
	ref.start
	ref

      case Right(factory) =>
	// We create a local mobile ref and migrate it right away to the proper node
	val mobileRef = MobileActorRef(factory()) 
	mobileRef.start
	mobileRef ! MoveTo(hostname, port)
	mobileRef
    }
  }

  private[mobile] def completeActorSpawn(requestId: Long, uuid: String, node: TheaterNode): Unit = {
    log.debug("Mobile actor with UUID [%s] started in remote theater at %s.", uuid, node.format)
    //ReferenceManagement.attachRefToActor(requestId.toString, uuid)
    MobileActorRef(uuid) match {
      case Some(ref) => ref ! AttachRefToActor(node)
      case None => throw new RuntimeException("Detached actor with UUID " + uuid + " not found")
    }
  }

  private[mobile] def spawnColocatedActors(
      constructor: Either[Tuple2[Class[_ <: MobileActor], Int], Seq[() => MobileActor]], 
      node: TheaterNode,
      nextTo: Option[MobileActorRef] = None): List[MobileActorRef] = {
    
    val hostname = node.hostname
    val port = node.port

    constructor match {
      case Left((clazz, n)) => 
	val requestId = UUID.newUuid
	val refs = (for {
	  i <- 0 to (n - 1)
	  actorId = requestId + "_" + i
	} yield MobileActorRef(clazz, actorId, true)).toList
	refs.foreach(_.start)
	LocalTheater.sendTo(node, StartColocatedActorsRequest(requestId, clazz.getName, n, nextTo.map(_.uuid)))
	refs
      
      case Right(factories) =>
	// We create N local mobile ref's and migrate them right away to the proper node
	val groupId = GroupManagement.newGroupId
	val refs = for (factory <- factories) yield MobileActorRef(factory())
	refs.foreach { ref =>
	  ref.groupId = Some(groupId)
	  ref.start
	}
	refs(0) ! MoveGroupTo(hostname, port, nextTo.map(_.uuid))
        refs.toList
    }
  }

  private[mobile] def completeColocatedActorsSpawn(requestId: Long, uuids: Array[String], node: TheaterNode): Unit = {
    log.debug("%d colocated mobile actors started in remote theater at %s.", uuids.size, node.format)
    for (i <- 0 to (uuids.size - 1)) {
      ReferenceManagement.attachRefToActor(requestId + "_" + i, uuids(i))
    }
  }

  // TODO melhorar o meio de conseguir um incremento atomicamente
  def newRequestId: Long = this.synchronized {
    requestId += 1
    requestId
  }
}
