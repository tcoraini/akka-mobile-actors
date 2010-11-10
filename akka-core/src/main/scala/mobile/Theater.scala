package se.scalablesolutions.akka.mobile

import se.scalablesolutions.akka.actor.ActorRef
import se.scalablesolutions.akka.actor.Actor
import se.scalablesolutions.akka.actor.Actor._
import se.scalablesolutions.akka.actor.RemoteActorSerialization

import se.scalablesolutions.akka.remote.RemoteServer
import se.scalablesolutions.akka.remote.RemoteClient

import se.scalablesolutions.akka.util.Logging

import java.util.concurrent.ConcurrentHashMap

import TheaterHelper._

case class MovingActor(bytes: Array[Byte], sender: TheaterNode)
case class MobileActorRegistered(uuid: String)

case class StartMobileActorRequest(constructor: Either[String, Array[Byte]])
case class StartMobileActorReply(actorUuid: String)

case class TheaterNode(hostname: String, port: Int) {
  def isLocal: Boolean = Theater.isLocal(hostname, port)
}

case object TheaterNode {
  // Defining an implicit conversion from TheaterNode to Tuple2 (hostname, port), just for convenience
  implicit def theaterNodeToTuple2(node: TheaterNode): Tuple2[String, Int] = {
    (node.hostname, node.port)
  }
}

object Theater extends Logging {
  
  val server = new RemoteServer
  
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

      val mobileRef = MobileSerialization.mobileFromBinary(bytes)(DefaultActorFormat)
      register(mobileRef)
      println("MAILBOX: " + mobileRef.mb)
      // Notifying the sender that the actor is now registered in this theater
      sendToTheater(MobileActorRegistered(mobileRef.uuid), sender)
      ""
    } else "" // TODO devolver um erro para quem solicitou a migração
  }

  def startLocalActor(constructor: Either[String, Array[Byte]]): MobileActorRef = {
    val mobileRef: MobileActorRef = constructor match {
      case Left(classname) =>
        new MobileActorRef(Mobile.newMobileActor(classname))
        
      case Right(bytes) =>
        MobileSerialization.mobileFromBinary(bytes)(DefaultActorFormat)
    }
    
    mobileRef.start
    Theater.register(mobileRef)
    mobileRef
  } 

  def finishMigration(actorUuid: String): Unit = {
    log.debug("Finishing the migration process of actor with UUID %s", actorUuid)
    val actor = mobileActors.get(actorUuid)
    if (actor != null) {
      println("RETAINED MESSAGES: " + actor.retained)
      println("MAILBOX: " + actor.mb)
      actor.endMigration()
      Theater.unregister(actor)
      // TODO encaminhar mensagens
      // TODO destruir instancia
    }
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
