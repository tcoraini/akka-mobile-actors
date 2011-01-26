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
import se.scalablesolutions.akka.mobile.theater.GroupManagement

import se.scalablesolutions.akka.actor.Actor
import se.scalablesolutions.akka.actor.LocalActorRef
import se.scalablesolutions.akka.actor.RemoteActorRef

object Mobile {
  
  private var algorithm: DistributionAlgorithm = new RoundRobinAlgorithm

  /**
   * Spawn in some node chosen by the distribution algorithm
   */
  def spawn[T <: MobileActor : Manifest]: MobileActorRef = {
    val clazz = manifest[T].erasure.asInstanceOf[Class[_ <: MobileActor]]
    spawn(Left(clazz))
  }

  def spawn(factory: => MobileActor): MobileActorRef = {
    spawn(Right(() => factory))
  }

  /**
    * Spawn at the local node
    */
  def spawnHere[T <: MobileActor : Manifest]: MobileActorRef = {
    val clazz = manifest[T].erasure.asInstanceOf[Class[_ <: MobileActor]]
    spawn(Left(clazz), Some(LocalTheater.node))
  }

  def spawnHere(factory: => MobileActor): MobileActorRef = {
    spawn(Right(() => factory), Some(LocalTheater.node))
  }

  /**
    * Spawn at the specified node
    */
  def spawnAt[T <: MobileActor : Manifest](node: TheaterNode): MobileActorRef = {
    val clazz = manifest[T].erasure.asInstanceOf[Class[_ <: MobileActor]]
    spawn(Left(clazz), Some(node))
  }

  def spawnAt(node: TheaterNode, factory: => MobileActor): MobileActorRef = {
    spawn(Right(() => factory), Some(node))
  }
  
  /**
   * Co-located actors
   */
  def spawnTogetherHere[T <: MobileActor : Manifest](number: Int): List[MobileActorRef] = {
    val groupId = GroupManagement.newGroupId
    val clazz = manifest[T].erasure.asInstanceOf[Class[_ <: MobileActor]]
    (for (i <- 1 to number)
      yield spawn(Left(clazz), Some(LocalTheater.node), Some(groupId))).toList
  }

  def spawnTogetherHere(factories: (() => MobileActor)*): List[MobileActorRef] = {
    val groupId = GroupManagement.newGroupId
    (for (factory <- factories)
      yield spawn(Right(factory), Some(LocalTheater.node), Some(groupId))).toList
  }

  def spawnTogetherAt[T <: MobileActor : Manifest](node: TheaterNode, number: Int): List[MobileActorRef] = {
    val clazz = manifest[T].erasure.asInstanceOf[Class[_ <: MobileActor]]
    TheaterHelper.spawnActorsGroupRemotely(Left((clazz, number)), node)
  }
  
  def spawnTogetherAt(node: TheaterNode)(factories: (() => MobileActor)*): List[MobileActorRef] = {
    TheaterHelper.spawnActorsGroupRemotely(Right(factories.toList), node)
  }

  // TODO tem como unificar os metodos de spawn normal e co-locados?
  private def spawn(
      constructor: Either[Class[_ <: MobileActor], () => MobileActor], 
      where: Option[TheaterNode] = None,
      groupId: Option[String] = None): MobileActorRef = {

    val node: TheaterNode = where match {
      case Some(theater) => theater
      case None => algorithm.chooseTheater
    }
  
    if (node.isLocal) {
      val mobileRef = constructor match {
        case Left(clazz) => MobileActorRef(clazz)

        case Right(factory) => MobileActorRef(factory())
      }
      mobileRef.groupId = groupId
      mobileRef.start
      LocalTheater.register(mobileRef)
      mobileRef
    } else {
      TheaterHelper.spawnActorRemotely(constructor, node)
    }
  }

}


