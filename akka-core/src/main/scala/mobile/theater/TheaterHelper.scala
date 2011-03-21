package se.scalablesolutions.akka.mobile.theater

import se.scalablesolutions.akka.mobile.actor.MobileActor
import se.scalablesolutions.akka.mobile.actor.MobileActorRef
import se.scalablesolutions.akka.mobile.Mobile
import se.scalablesolutions.akka.mobile.util.messages._

import se.scalablesolutions.akka.actor.ActorRef

import se.scalablesolutions.akka.remote.RemoteClient

import se.scalablesolutions.akka.util.Logging

import scala.collection.mutable.HashMap

object TheaterHelper extends Logging {

  private var requestId: Long = 1

  private val mobileRefs = new HashMap[Long, MobileActorRef]
  private val mobileGroups = new HashMap[Long, List[MobileActorRef]]

  def spawnActorRemotely(constructor: Either[Class[_ <: MobileActor], () => MobileActor], node: TheaterNode): MobileActorRef = {
    val hostname = node.hostname
    val port = node.port

    constructor match {
      case Left(clazz) => 
	val requestId = newRequestId
	LocalTheater.protocol.sendTo(node, StartMobileActorRequest(requestId, clazz.getName))
	MobileActorRef(requestId.toString, node.hostname, node.port, true)

      case Right(factory) =>
	// We create a local mobile ref and migrate it right away to the proper node
	val mobileRef = MobileActorRef(factory()) 
	mobileRef.start
	mobileRef ! MoveTo(hostname, port)
	mobileRef
    }
  }

  def completeActorSpawn(reply: StartMobileActorReply): Unit = {
    log.debug("Mobile actor with UUID [%s] started in remote theater at %s.", 
	      reply.uuid, 
	      TheaterNode(reply.sender.get.hostname, reply.sender.get.port).format)
    ReferenceManagement.attachRefToActor(reply.requestId.toString, reply.uuid)
  }

  def spawnActorsGroupRemotely(
      constructor: Either[Tuple2[Class[_ <: MobileActor], Int], List[() => MobileActor]], 
      node: TheaterNode): List[MobileActorRef] = {
    
    val hostname = node.hostname
    val port = node.port    
        
    // The function literal is not serializable
    val serializableConstructor: Either[Tuple2[String, Int], List[Array[Byte]]] = constructor match {
      case Left((clazz, n)) =>
        Left(clazz.getName, n)

      case Right(factories) =>
	val array = for {
	  factory <- factories
	  mobileRef = MobileActorRef(factory()) // TODO pra onde vai? ali embaixo criamos outra! O ideal e' evitar essa criação.
	                                        // Como serializar sem ter que criar um MobileActorRef?
	  bytes = mobileRef.startMigration(hostname, port)
	} yield bytes
	Right(array.toList)
    }
    
    // TODO Definitivamente precisamos de um protocolo melhor para essa tarefa
    val reqId = newRequestId
    LocalTheater.protocol.sendTo(node, StartMobileActorsGroupRequest(reqId, serializableConstructor))
    while (!mobileGroups.contains(reqId))
      Thread.sleep(200)

    val refs = mobileGroups.get(reqId).get
    mobileGroups.remove(reqId)
    refs
  }

  def completeActorsGroupSpawn(reply: StartMobileActorsGroupReply): Unit = {
    log.debug("Mobile co-located actors started in remote theater at %s.", TheaterNode(reply.sender.get.hostname, reply.sender.get.port).format)
    val newRefs = 
      (for {
	uuid <- reply.uuids
	ref = MobileActorRef(uuid, reply.sender.get.hostname, reply.sender.get.port)
      } yield ref).toList
    mobileGroups.put(reply.requestId, newRefs)
  }

  def newRequestId: Long = {
    requestId += 1
    requestId
  }
}
