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
  
  // Implicit conversion to make it easier to spawn co-located actors from factories (non-default
  // constructor)
  implicit def fromByNameToFunctionLiteral(byName: => MobileActor): () => MobileActor = { () => byName }
  
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

  def spawnAt(factory: => MobileActor, node: TheaterNode): MobileActorRef = {
    spawn(Right(() => factory), Some(node))
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

  /**
   * Co-located actors
   */

  /**
   * Co-locate at some node chosen by algorithm
   */
  def colocate[T <: MobileActor : Manifest](number: Int) = {
    val clazz = manifest[T].erasure.asInstanceOf[Class[_ <: MobileActor]]
//    colocateOptions(Left(clazz, number))
    spawnColocated(Left(clazz, number), None)
  }

  def colocate(factories: (() => MobileActor)*) = // TODO factories pode ser vazio
    //colocateOptions(Right(factories))
    spawnColocated(Right(factories), None)

  def test(factory1: => () => MobileActor,
	    factory2: => () => MobileActor,
	    factories: (() => MobileActor)*) = { // TODO factories pode ser vazio
    //colocateOptions(Right(factories))
    //val allFactories = factory1 :: factory2 :: factories.toList
    //spawnColocated(Right(allFactories), None)
    "segundo metodo"
  }

  def test(factory: () => MobileActor) = "primeiro metodo"

  
  /**
   * Co-locate at local node
   */
  def colocateHere[T <: MobileActor : Manifest](number: Int) = {
    val clazz = manifest[T].erasure.asInstanceOf[Class[_ <: MobileActor]]
    spawnColocated(Left(clazz, number), Some(LocalTheater.node))
  }

  def colocateHere(factories: (() => MobileActor)*) = 
    spawnColocated(Right(factories), Some(LocalTheater.node))

  /**
   * Co-locate at the specified node
   */
  def colocateAt[T <: MobileActor : Manifest](number: Int, node: TheaterNode) = {
    val clazz = manifest[T].erasure.asInstanceOf[Class[_ <: MobileActor]]
    spawnColocated(Left(clazz, number), Some(node))
  }

  def colocateAt(factories: (() => MobileActor)*)(node: TheaterNode) = 
    spawnColocated(Right(factories), Some(node))
  
  /**
   * Co-locate next to some specified actor ref
   */
  def colocateNextTo[T <: MobileActor : Manifest](number: Int, ref: MobileActorRef) = {
    val clazz = manifest[T].erasure.asInstanceOf[Class[_ <: MobileActor]]
    spawnColocated(Left(clazz, number), Some(ref.node))
  }

  def colocateNextTo(factories: (() => MobileActor)*)(ref: MobileActorRef) = 
    spawnColocated(Right(factories), Some(ref.node))


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

  /*
   * OUTRA VERSAO
   */
  
  def colocateOps[T <: MobileActor : Manifest](number: Int) = {
    val clazz = manifest[T].erasure.asInstanceOf[Class[_ <: MobileActor]]
    colocateOptions(Left(clazz, number))
  }

  def colocateOps(factories: (() => MobileActor)*) = 
    colocateOptions(Right(factories))

  private def colocateOptions(constructor: Either[Tuple2[Class[_ <: MobileActor], Int], Seq[() => MobileActor]]) = new {
    // TODO os atores nesse caso tem que ter o groupId do ator que eles estao sendo colocados juntos
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


