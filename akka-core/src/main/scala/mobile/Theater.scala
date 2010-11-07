package se.scalablesolutions.akka.mobile

import se.scalablesolutions.akka.actor.ActorRef
import se.scalablesolutions.akka.actor.Actor
import se.scalablesolutions.akka.actor.Actor._
import se.scalablesolutions.akka.actor.RemoteActorSerialization

import se.scalablesolutions.akka.remote.RemoteServer
import se.scalablesolutions.akka.remote.RemoteClient

import se.scalablesolutions.akka.util.Logging

import java.util.concurrent.ConcurrentHashMap

import scala.collection.mutable.HashMap

case class MovingActor(bytes: Array[Byte], sender: TheaterNode)
case class MobileActorRegistered(uuid: String)

case class StartMobileActorRequest(constructor: Either[String, Array[Byte]])
case class StartMobileActorReply(actorUuid: String)

case class TheaterNode(hostname: String, port: Int)

case object TheaterNode {
  // Defining an implicit conversion from TheaterNode to Tuple2 (hostname, port), just for convenience
  implicit def theaterNodeToTuple2(node: TheaterNode): Tuple2[String, Int] = {
    (node.hostname, node.port)
  }
}

object Theater extends Logging {
  
  val server = new RemoteServer
  
  // This theater comunicates with other theaters through these agents
  private val agents = new HashMap[TheaterNode, ActorRef]
  
  // Mobile actors running in this theater
  private val mobileActors = new ConcurrentHashMap[String, MobileActorRef]
  
  private var hostname: String = _
  private var port: Int = _

  // The address of this Theater
  private lazy val sender = TheaterNode(hostname, port)

  private var _isRunning = false

  def isRunning = _isRunning
  
  def start(hostname: String, port: Int) = {
    log.debug("Starting a theater at %s:%d.", hostname, port)

    this.hostname = hostname
    this.port = port

    server.setPipelineFactoryCreator(new TheaterPipelineFactoryCreator(mobileActors))
    server.start(hostname, port)

    val agentName = "theater@" + hostname + ":" + port
    server.register(agentName, actorOf(new TheaterAgent))

    _isRunning = true
  }

  // Request the migration of actor with UUID to some destination
  // Syntax: Theater migrate UUID to (host, port)
  def migrate(uuid: String) = new {
    def to(destination: Tuple2[String, Int]): Boolean = {
      val actorRef = mobileActors.get(uuid)
      if (_isRunning && actorRef != null) {
        val actorBytes = actorRef.startMigration(destination._1, destination._2)
        val agent = agentFor(destination._1, destination._2)
        agent ! MovingActor(actorBytes, sender)
        true
        //actorRef.migrateTo(destination._1, destination._2)
      } else false
    }
  }

  // def migrate(actor: MobileActorRef) = migrate(actor.uuid)
 
  def register(actor: MobileActorRef): Unit = {
    if (_isRunning) {
      mobileActors.put(actor.uuid, actor)
    } // TODO verificar isso, tratamento quando o theater nao estao rodando
  }

  def unregister(actor: MobileActorRef): Unit = {
    if (_isRunning) {
      mobileActors.remove(actor.uuid)
    }
  }

  def receiveActor(bytes: Array[Byte], sender: TheaterNode): String = {
    if (_isRunning) {
      log.debug("Theater at %s:%d just received a migrating actor from %s:%d", hostname, port, sender.hostname, sender.port)

      val actor = Mobile.mobileFromBinary(bytes)(DefaultActorFormat)
      register(new MobileActorRef(actor))
      // Notifying the sender that the actor is now registered in this theater
      sendToTheater(MobileActorRegistered(actor.uuid), sender)
      ""
    } else "" // TODO devolver um erro para quem solicitou a migração
  }

  def startLocalActor(clazz: Class[_ <: MobileActor]): MobileActorRef = {
    val mobileRef = Mobile.mobileOf(clazz.newInstance.asInstanceOf[MobileActor])

    mobileRef.start
    Theater.register(mobileRef)
    mobileRef
  }

  def startLocalActor(classname: String): MobileActorRef = {
    //startLocalActor(Class.forName(classname).asInstanceOf[Class[_ <: MobileActor]])

    
    val clazz = Class.forName(classname).asInstanceOf[Class[_ <: MobileActor]]
  }
  
  

  def startLocalActor(constructor: Either[String, Array[Byte]]): MobileActorRef = {
    val mobileRef = constructor match {
      case Left(classname) =>
        val clazz = Class.forName(classname).asInstanceOf[Class[_ <: MobileActor]]
        Mobile.mobileOf(clazz.newInstance.asInstanceOf[MobileActor])

      case Right(bytes) =>
        Mobile.mobileFromBinary(actor)
    }
    mobileRef.start
    Theater.register(mobileRef)
    mobileRef
  } 

  def finishMigration(actorUuid: String): Unit = {
    log.debug("Finishing the migration process of actor with UUID %s", actorUuid)
    val actor = mobileActors.get(actorUuid)
    if (actor != null) {
      val destination: TheaterNode = actor.migratingTo.get
      val newActorRef = Mobile.mobileOf(actorUuid, destination.hostname, destination.port, actor.timeout)
      actor.switchActorRef(newActorRef)
      Theater.unregister(actor)
      // TODO encaminhar mensagens
      // TODO destruir instancia
    }
  }

  def agentFor(hostname: String, port: Int): ActorRef = agents.get(TheaterNode(hostname, port)) match {
    case Some(agent) => agent
      
    case None => 
      val agentName = "theater@" + hostname + ":" + port
      val newAgent = RemoteClient.actorFor(agentName, hostname, port)
      agents += ((TheaterNode(hostname, port), newAgent))
      newAgent
  }

  def sendToTheater(message: Any, destination: TheaterNode): Unit = { 
    val agent = agentFor(destination.hostname, destination.port)
    agent ! message
  }

  def isLocal(hostname: String, port: Int): Boolean = (Theater.hostname == hostname && Theater.port == port)

}

class TheaterAgent extends Actor {
  
  def receive = {
    case MovingActor(bytes, sender) =>
      Theater.receiveActor(bytes, sender)
      //val uuid = Theater.receiveActor(bytes, sender)
      //self.reply(MobileActorRegistered(uuid))

    //case StartMobileActorRequest(classname) =>
    case StartMobileActorRequest(constructor) =>
      val ref = Theater.startLocalActor(constructor)
      self.reply(StartMobileActorReply(ref.uuid))

    case MobileActorRegistered(uuid) =>
      Theater.finishMigration(uuid)
      
    case msg =>
      Theater.log.debug("Theater agent received an unknown message: " + msg)
  }
}
