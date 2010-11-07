package se.scalablesolutions.akka.mobile

import se.scalablesolutions.akka.actor.Actor
import se.scalablesolutions.akka.actor.LocalActorRef
import se.scalablesolutions.akka.actor.RemoteActorRef
import se.scalablesolutions.akka.actor.Format
import se.scalablesolutions.akka.actor.SerializerBasedActorFormat
import se.scalablesolutions.akka.actor.IllegalActorStateException
import se.scalablesolutions.akka.actor.RemoteActorSerialization

import se.scalablesolutions.akka.remote.protocol.RemoteProtocol._
import se.scalablesolutions.akka.config.ScalaConfig._

object Mobile {
  
  private var algorithm: DistributionAlgorithm = new RoundRobinAlgorithm

  def spawn[T <: MobileActor : Manifest]: MobileActorRef = {
    val clazz = manifest[T].erasure.asInstanceOf[Class[_ <: MobileActor]]

    spawn(Left(clazz.getName))
    
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

  private def spawn(constructor: Either[String, () => MobileActor]): MobileActorRef = {
    val node: TheaterNode = algorithm.chooseTheater
    Theater.startActor(constructor) at node
  }

  def mobileOf(clazz: Class[_ <: MobileActor]): MobileActorRef = new MobileActorRef(createMobileLocalActorRef(clazz))

  def mobileOf(classname: String): MobileActorRef = {
    val clazz = Class.forName(classname).asInstanceOf[Class[_ <: MobileActor]]
    mobileOf(clazz)
  }

  def mobileOf(factory: => MobileActor): MobileActorRef = new MobileActorRef(createMobileLocalActorRef(factory))

  // For remote actors
  def mobileOf(actorId: String, hostname: String, port: Int, timeout: Long) = 
    new MobileActorRef(createMobileRemoteActorRef(actorId, hostname, port, timeout))


  // TODO provavalmente isso precisa ser private, expor apenas MobileActorRef aos clientes
  // Local Actor Ref
  def createMobileLocalActorRef(clazz: Class[_ <: MobileActor]): MobileLocalActorRef = new LocalActorRef(clazz) with MobileLocalActorRef

  def createMobileLocalActorRef(factory: => MobileActor): MobileLocalActorRef = new LocalActorRef(() => factory) with MobileLocalActorRef
  
  // Remote Actor Ref
  def createMobileRemoteActorRef(actorId: String, hostname: String, port: Int, timeout: Long): MobileRemoteActorRef = 
    new RemoteActorRef(actorId, actorId, hostname, port, timeout, None) with MobileRemoteActorRef
    

  /* Adapted from SerializationProtocol just to construct a MobileActorRef */
  def mobileFromBinary[T <: Actor](bytes: Array[Byte])(implicit format: Format[T]): MobileLocalActorRef =
    fromBinaryToMobileActorRef(bytes, format)

  private def fromBinaryToMobileActorRef[T <: Actor](bytes: Array[Byte], format: Format[T]): MobileLocalActorRef =
    fromProtobufToMobileActorRef(SerializedActorRefProtocol.newBuilder.mergeFrom(bytes).build, format, None)

  private def fromProtobufToMobileActorRef[T <: Actor](
    protocol: SerializedActorRefProtocol, format: Format[T], loader: Option[ClassLoader]): MobileLocalActorRef = {
    Actor.log.debug("Deserializing SerializedActorRefProtocol to MobileActorRef:\n" + protocol)

    val serializer =
      if (format.isInstanceOf[SerializerBasedActorFormat[_]])
        Some(format.asInstanceOf[SerializerBasedActorFormat[_]].serializer)
      else None

    val lifeCycle =
      if (protocol.hasLifeCycle) {
        val lifeCycleProtocol = protocol.getLifeCycle
        Some(if (lifeCycleProtocol.getLifeCycle == LifeCycleType.PERMANENT) LifeCycle(Permanent)
             else if (lifeCycleProtocol.getLifeCycle == LifeCycleType.TEMPORARY) LifeCycle(Temporary)
             else throw new IllegalActorStateException("LifeCycle type is not valid: " + lifeCycleProtocol.getLifeCycle))
      } else None

    val supervisor =
      if (protocol.hasSupervisor)
        Some(RemoteActorSerialization.fromProtobufToRemoteActorRef(protocol.getSupervisor, loader))
      else None

    val hotswap =
      if (serializer.isDefined && protocol.hasHotswapStack) Some(serializer.get
        .fromBinary(protocol.getHotswapStack.toByteArray, Some(classOf[PartialFunction[Any, Unit]]))
        .asInstanceOf[PartialFunction[Any, Unit]])
      else None

    val ar = new LocalActorRef(
      protocol.getUuid,
      protocol.getId,
      protocol.getActorClassname,
      protocol.getActorInstance.toByteArray,
      protocol.getOriginalAddress.getHostname,
      protocol.getOriginalAddress.getPort,
      if (protocol.hasIsTransactor) protocol.getIsTransactor else false,
      if (protocol.hasTimeout) protocol.getTimeout else Actor.TIMEOUT,
      if (protocol.hasReceiveTimeout) Some(protocol.getReceiveTimeout) else None,
      lifeCycle,
      supervisor,
      hotswap,
      loader.getOrElse(getClass.getClassLoader), // TODO: should we fall back to getClass.getClassLoader?
      protocol.getMessagesList.toArray.toList.asInstanceOf[List[RemoteRequestProtocol]], 
      format) with MobileLocalActorRef

    if (format.isInstanceOf[SerializerBasedActorFormat[_]] == false)
      format.fromBinary(protocol.getActorInstance.toByteArray, ar.actor.asInstanceOf[T])
    ar
  }


}


