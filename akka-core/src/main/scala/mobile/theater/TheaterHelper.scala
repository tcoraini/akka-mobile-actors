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

  def spawnActorRemotely(constructor: Either[Class[_ <: MobileActor], () => MobileActor], node: TheaterNode): MobileActorRef = {
    val hostname = node.hostname
    val port = node.port

    // The function literal is not serializable
    val serializableConstructor: Either[String, Array[Byte]] = constructor match {
      case Left(clazz) => 
        Left(clazz.getName)

      case Right(factory) =>
        val mobileRef = MobileActorRef(factory()) // TODO pra onde vai? ali embaixo criamos outra!
        val bytes = mobileRef.startMigration(hostname, port)
        Right(bytes)
    }

    // TODO Definitivamente precisamos de um protocolo melhor para essa tarefa
    val reqId = newRequestId
    LocalTheater.protocol.sendTo(node, StartMobileActorRequest(reqId, serializableConstructor))
    while (!mobileRefs.contains(reqId))
      Thread.sleep(200)

    mobileRefs.get(reqId).get

  }

  def completeActorSpawn(reply: StartMobileActorReply): Unit = {
    log.debug("Mobile actor with UUID [%s] started in remote theater at [%s:%d].", reply.uuid, reply.sender.get.hostname, reply.sender.get.port)
    val newRef = MobileActorRef(reply.uuid, reply.sender.get.hostname, reply.sender.get.port)
    mobileRefs.put(reply.requestId, newRef)
  }

  def newRequestId: Long = {
    requestId += 1
    requestId
  }
}
