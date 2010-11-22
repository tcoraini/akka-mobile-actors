package se.scalablesolutions.akka.mobile.theater

import se.scalablesolutions.akka.mobile.actor.MobileActorRef
import se.scalablesolutions.akka.mobile.serialization.MobileSerialization
import se.scalablesolutions.akka.mobile.serialization.DefaultActorFormat
import se.scalablesolutions.akka.mobile.util.NodeInformation

import se.scalablesolutions.akka.mobile.Mobile

import se.scalablesolutions.akka.actor.ActorRef
import se.scalablesolutions.akka.actor.Actor
import se.scalablesolutions.akka.actor.Actor._
import se.scalablesolutions.akka.actor.RemoteActorSerialization

import se.scalablesolutions.akka.remote.RemoteServer
import se.scalablesolutions.akka.remote.RemoteClient

import se.scalablesolutions.akka.config.Config
import se.scalablesolutions.akka.util.Logging

import se.scalablesolutions.akka.mobile.nameservice.NameService
import se.scalablesolutions.akka.mobile.nameservice.DistributedNameService

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
  // Defining some implicit conversions, just for convenience

  // From TheaterNode to Tuple2 (hostname, port)
  implicit def theaterNodeToTuple2(node: TheaterNode): Tuple2[String, Int] = {
    (node.hostname, node.port)
  }

  // From NodeInformation to TheaterNode
  implicit def nodeInformationToTheaterNode(nodeInfo: NodeInformation) = TheaterNode(nodeInfo.hostname, nodeInfo.port)
}

object Theater extends Logging {
  
  val server = new RemoteServer
  
  // Mobile actors running in this theater
  private val mobileActors = new ConcurrentHashMap[String, MobileActorRef]
  
  private var _hostname: String = _
  private var _port: Int = _

  def hostname: String = _hostname
  def port: Int = _port

  // The address of this Theater
  private[mobile] lazy val localNode = TheaterNode(hostname, port)

  private var _isRunning = false

  def isRunning = _isRunning

  private var _localAgent: ActorRef = _
  private[mobile] def localAgent = _localAgent

  def start(hostname: String, port: Int) = {
    log.debug("Starting a theater at %s:%d.", hostname, port)

    _hostname = hostname
    _port = port

    server.setPipelineFactoryCreator(new TheaterPipelineFactoryCreator(mobileActors))
    server.start(hostname, port)

    val agentName = "theater@" + hostname + ":" + port
    _localAgent = actorOf(new TheaterAgent)
    server.register(agentName, _localAgent)

    initNameService()

    _isRunning = true
  }

  def initNameService(): Unit = {
    lazy val defaultNameService = new DistributedNameService

    // The user can specify his own name service through the configuration file
    val nameService: NameService = Config.config.getString("cluster.name-service.name-service-class") match {
      case Some(classname) =>
        try {
          val instance = Class.forName(classname).newInstance.asInstanceOf[NameService]
          log.info("Using '%s' as the name service.", classname)
          instance
        } catch {
          case cnfe: ClassNotFoundException =>
            log.warning("The class '%s' could not be found. Using the default name service (%s) instead.", 
                classname, defaultNameService)
            defaultNameService
          
          case cce: ClassCastException =>
            log.warning("The class '%s' does not extend the NameService trait. Using the default name service (%s) instead.", 
                classname, defaultNameService)
            defaultNameService
        }

      case None =>
        defaultNameService
    }
    
    nameService.init()
    NameService.init(nameService)
  }

  // Request the migration of actor with UUID to some destination
  // Syntax: Theater migrate UUID to (host, port)
  def migrate(uuid: String) = new {
    def to(destination: Tuple2[String, Int]): Boolean = {
      val actorRef = mobileActors.get(uuid)
      if (_isRunning && actorRef != null) {
        val actorBytes = actorRef.startMigration(destination._1, destination._2)
        val agent = agentFor(destination._1, destination._2)
        agent ! MovingActor(actorBytes, localNode)
        true
        //actorRef.migrateTo(destination._1, destination._2)
      } else false
    }
  }

  // def migrate(actor: MobileActorRef) = migrate(actor.uuid)
 
  def register(actor: MobileActorRef): Unit = {
    if (_isRunning) {
      mobileActors.put(actor.uuid, actor)
      
      // Registering in the name server
      NameService.put(actor.uuid, localNode)
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
      NameService.put(mobileRef.uuid, localNode)
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

  def registerAgent(name: String, agent: ActorRef): Unit = {
    server.register(name, agent)
  }

  def unregisterAgent(name: String) = {
    server.unregister(name)
  }

}


