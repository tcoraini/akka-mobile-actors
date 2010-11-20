package se.scalablesolutions.akka.mobile

import se.scalablesolutions.akka.actor.Actor
import se.scalablesolutions.akka.actor.LocalActorRef
import se.scalablesolutions.akka.actor.RemoteActorRef

object Mobile {
  
  private var algorithm: DistributionAlgorithm = new RoundRobinAlgorithm

  def spawn[T <: MobileActor : Manifest]: MobileActorRef = {
    val clazz = manifest[T].erasure.asInstanceOf[Class[_ <: MobileActor]]

    spawn(Left(clazz))
    
    //val node: TheaterNode = algorithm.chooseTheater
    //Theater.start(clazz) at node

    //val localRef = mobileOf(manifest[T].erasure.asInstanceOf[Class[_ <: MobileActor]])
    //val mobileRef = new MobileActorRef(localRef)  

    //Theater.register(mobileRef)
    //mobileRef
  }

  def spawn(factory: => MobileActor): MobileActorRef = {
    spawn(Right(() => factory))
  }

  private def spawn(constructor: Either[Class[_ <: MobileActor], () => MobileActor]): MobileActorRef = {
    val node: TheaterNode = algorithm.chooseTheater
  
    if (node.isLocal) {
      val mobileRef = constructor match {
        case Left(clazz) => new MobileActorRef(newMobileActor(clazz))

        case Right(factory) => new MobileActorRef(newMobileActor(factory()))
      }
      mobileRef.start
      Theater.register(mobileRef)
      mobileRef
    } else {
      TheaterHelper.spawnActorRemotely(constructor, node)
    }
  }

  private[mobile] def newMobileActor(factory: => MobileActor): LocalMobileActor = 
    new LocalActorRef(() => factory) with LocalMobileActor

  private[mobile] def newMobileActor(clazz: Class[_ <: MobileActor]): LocalMobileActor = 
    new LocalActorRef(clazz) with LocalMobileActor

  private[mobile] def newMobileActor(classname: String): LocalMobileActor = {
    val clazz = Class.forName(classname).asInstanceOf[Class[_ <: MobileActor]]
    newMobileActor(clazz)
  }

  // For remote actors
  def newRemoteMobileActor(actorId: String, hostname: String, port: Int, timeout: Long): RemoteMobileActor = 
    new RemoteActorRef(actorId, actorId, hostname, port, timeout, None) with RemoteMobileActor

}


