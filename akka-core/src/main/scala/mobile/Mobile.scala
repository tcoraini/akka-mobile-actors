package se.scalablesolutions.akka.mobile

import se.scalablesolutions.akka.mobile.algorithm.DistributionAlgorithm
import se.scalablesolutions.akka.mobile.algorithm.RoundRobinAlgorithm

import se.scalablesolutions.akka.mobile.actor.MobileActor
import se.scalablesolutions.akka.mobile.actor.MobileActorRef
import se.scalablesolutions.akka.mobile.actor.LocalMobileActor
import se.scalablesolutions.akka.mobile.actor.RemoteMobileActor

import se.scalablesolutions.akka.mobile.theater.LocalTheater
import se.scalablesolutions.akka.mobile.theater.TheaterNode
import se.scalablesolutions.akka.mobile.theater.TheaterHelper

import se.scalablesolutions.akka.actor.Actor
import se.scalablesolutions.akka.actor.LocalActorRef
import se.scalablesolutions.akka.actor.RemoteActorRef

object Mobile {
  
  private var algorithm: DistributionAlgorithm = new RoundRobinAlgorithm

  def spawn[T <: MobileActor : Manifest]: MobileActorRef = {
    val clazz = manifest[T].erasure.asInstanceOf[Class[_ <: MobileActor]]
    spawn(Left(clazz))
  }

  def spawn(factory: => MobileActor): MobileActorRef = {
    spawn(Right(() => factory))
  }

  private def spawn(constructor: Either[Class[_ <: MobileActor], () => MobileActor]): MobileActorRef = {
    val node: TheaterNode = algorithm.chooseTheater
  
    if (node.isLocal) {
      val mobileRef = constructor match {
        case Left(clazz) => MobileActorRef(clazz)

        case Right(factory) => MobileActorRef(factory())
      }
      mobileRef.start
      LocalTheater.register(mobileRef)
      mobileRef
    } else {
      TheaterHelper.spawnActorRemotely(constructor, node)
    }
  }

  /*private[this] def newMobileActor(factory: => MobileActor): LocalMobileActor = 
    new LocalActorRef(() => factory) with LocalMobileActor

  private[this] def newMobileActor(clazz: Class[_ <: MobileActor]): LocalMobileActor = 
    new LocalActorRef(clazz) with LocalMobileActor

  private[this] def newMobileActor(classname: String): LocalMobileActor = {
    val clazz = Class.forName(classname).asInstanceOf[Class[_ <: MobileActor]]
    newMobileActor(clazz)
  }*/

  // For remote actors
  /*def newRemoteMobileActor(actorId: String, hostname: String, port: Int, timeout: Long): RemoteMobileActor = 
    new RemoteActorRef(actorId, actorId, hostname, port, timeout, None) with RemoteMobileActor*/

}


