package se.scalablesolutions.akka.mobile

import se.scalablesolutions.akka.mobile.algorithm.DistributionAlgorithm
import se.scalablesolutions.akka.mobile.algorithm.RoundRobinAlgorithm
import se.scalablesolutions.akka.mobile.actor.MobileActor
import se.scalablesolutions.akka.mobile.actor.MobileActorRef
import se.scalablesolutions.akka.mobile.theater.LocalTheater
import se.scalablesolutions.akka.mobile.theater.TheaterNode
import se.scalablesolutions.akka.mobile.theater.TheaterHelper
import se.scalablesolutions.akka.mobile.theater.GroupManagement
import se.scalablesolutions.akka.mobile.util.ClusterConfiguration

import se.scalablesolutions.akka.util.Logging
import se.scalablesolutions.akka.config.Config

import java.net.InetAddress

object Mobile extends Logging {
  
  // TODO configurar via arquivo de conf
  private lazy val algorithm: DistributionAlgorithm = {
    lazy val defaultAlgorithm = new RoundRobinAlgorithm
    try {
      ClusterConfiguration.instanceOf[DistributionAlgorithm, RoundRobinAlgorithm]("cluster.distribution-algorithm")
    } catch {
      case cce: ClassCastException =>
	val classname = Config.config.getString("cluster.distribution-algorithm", "")
	log.warning("The class [%s] does not extend the DistributionAlgorithm trait. Using the default algorithm [%s] instead.", 
                    classname, defaultAlgorithm.getClass.getName)
	defaultAlgorithm
    }
  }

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

    val node: TheaterNode = where.getOrElse(algorithm.chooseTheater)
  
    if (node.isLocal) {
      val mobileRef = constructor match {
        case Left(clazz) => MobileActorRef(clazz)

        case Right(factory) => MobileActorRef(factory())
      }
      mobileRef.groupId = groupId
      mobileRef.start
//      LocalTheater.register(mobileRef)
      mobileRef
    } else {
      TheaterHelper.spawnActorRemotely(constructor, node)
    }
  }


  def colocate[T <: MobileActor : Manifest](number: Int) = {
    val clazz = manifest[T].erasure.asInstanceOf[Class[_ <: MobileActor]]
    colocateOptions(Left(clazz, number))
  }

  def colocate(factories: (() => MobileActor)*) = colocateOptions(Right(factories))//new {

  private def colocateOptions(constructor: Either[Tuple2[Class[_ <: MobileActor], Int], Seq[() => MobileActor]]) = new {
    def nextTo(ref: MobileActorRef): List[MobileActorRef] = {
      spawnColocated(constructor, Some(ref.node))
    }

    def at(node: TheaterNode): List[MobileActorRef] = {
      spawnColocated(constructor, Some(node))
    }

    def here: List[MobileActorRef] = {
      spawnColocated(constructor, Some(LocalTheater.node))
    }

    def ! : List[MobileActorRef] = {
      spawnColocated(constructor, None)
    }
  }

  private def spawnColocated(
      constructor: Either[Tuple2[Class[_ <: MobileActor], Int], Seq[() => MobileActor]],
      where: Option[TheaterNode] = None): List[MobileActorRef] = {

    val node: TheaterNode = where.getOrElse(algorithm.chooseTheater)
    
    if (node.isLocal) {
      val mobileRefs: Seq[MobileActorRef] = constructor match {
	case Left((clazz, n)) =>
	  for (i <- 1 to n) yield spawn(Left(clazz), Some(LocalTheater.node))
	
	case Right(factories) =>
	  for (factory <- factories) yield spawn(Right(factory), Some(LocalTheater.node))
      }
      val groupId = GroupManagement.newGroupId
      mobileRefs.foreach { ref => 
	ref.groupId = Some(groupId)
	ref.start
      }
      mobileRefs.toList
    } else TheaterHelper.spawnColocatedActorsRemotely(constructor, node) // TODO spawn remoto
  }


  def startTheater(nodeName: String): Boolean = LocalTheater.start(nodeName)

  def startTheater(hostname: String, port: Int): Boolean = LocalTheater.start(hostname, port)
  
  // In this case, the system will try to guess which node should run, based on the machine's hostname
  def startTheater(): Boolean = {
    val localHostname = InetAddress.getLocalHost.getHostName
    val iterable = ClusterConfiguration.nodes.values.filter {
      description => description.node.hostname == localHostname
    } map {
      description => description.name
    }
    if (iterable.size > 0) {
      LocalTheater.start(iterable.head)
    } else {
      log.warning("Impossible to figure it out which node is supposed to run on this machine. Please use one of the following:\n" +
		  "\t Mobile.startTheater(nodeName: String)\n" + 
		  "\t Mobile.startTheater(hostname: String, port: Int)")
      
      false
    }
  }
}


