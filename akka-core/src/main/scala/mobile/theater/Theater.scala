package se.scalablesolutions.akka.mobile.theater

import se.scalablesolutions.akka.mobile.actor.MobileActorRef
import se.scalablesolutions.akka.mobile.actor.MobileActor
import se.scalablesolutions.akka.mobile.serialization.MobileSerialization
import se.scalablesolutions.akka.mobile.serialization.DefaultActorFormat
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

object LocalTheater extends Theater

private[mobile] trait Theater extends Logging {
  
  private val server = new RemoteServer
  
  // Mobile actors running in this theater
  private val mobileActors = new ConcurrentHashMap[String, MobileActorRef]
  
  private var _hostname: String = _
  private var _port: Int = _

  def hostname: String = _hostname
  def port: Int = _port

  // The address of this Theater
  private var _node: TheaterNode = _
  def node = _node

  private var _isRunning = false
  def isRunning = _isRunning

  private var _agent: ActorRef = _
  private[mobile] def agent = _agent // TODO privado? Mover para outra classe?

  def start(hostname: String, port: Int) = {
    log.debug("Starting a theater at %s:%d.", hostname, port)

    _hostname = hostname
    _port = port
    _node = TheaterNode(hostname, port)

    server.setPipelineFactoryCreator(new TheaterPipelineFactoryCreator(mobileActors, this))
    server.start(hostname, port)

    val agentName = "theater@" + hostname + ":" + port
    _agent = actorOf(new TheaterAgent(this))
    server.register(agentName, _agent)

    NameService.init()

    _isRunning = true
  }

  def register(actor: MobileActorRef): Unit = {
    if (_isRunning) {
      mobileActors.put(actor.uuid, actor)
      actor.homeTheater = this
      
      // Registering in the name server
      NameService.put(actor.uuid, this.node)

      log.debug("Registering actor with UUID [%s] in theater at [%s:%d]", actor.uuid, hostname, port)
    } // TODO verificar isso, tratamento quando o theater nao estao rodando
  }

  def unregister(actor: MobileActorRef): Unit = {
    if (_isRunning) {
      mobileActors.remove(actor.uuid)

      log.debug("Unregistering actor with UUID [%s] from theater at [%s:%d]", actor.uuid, hostname, port)
    }
  }

  /**
   * Registers an agent within this theater. Agents are regular actors (not mobile) that run in the server 
   * started by the theater, performing some designated task
   */
  def registerAgent(name: String, agent: ActorRef): Unit = {
    server.register(name, agent)
  }

  /* Unregisters and agent */
  def unregisterAgent(name: String): Unit = {
    server.unregister(name)
  }

  /**
   * Starts a local actor within this theater. This method will be called whenever
   * an actor is spawned somewhere and the system decides that it should be run
   * in this theater.
   *
   * @return the reference of the new actor started
   */
  def startLocalActor(constructor: Either[String, Array[Byte]]): MobileActorRef = {
    val mobileRef: MobileActorRef = constructor match {
      case Left(classname) =>
        MobileActorRef(Class.forName(classname).asInstanceOf[Class[_ <: MobileActor]])
        
      case Right(bytes) =>
        MobileSerialization.mobileFromBinary(bytes)(DefaultActorFormat)
    }
    
    mobileRef.start
    this.register(mobileRef)
    mobileRef
  } 

  // Request the migration of actor with UUID to some destination
  // Syntax: Theater migrate UUID to (host, port)
  def migrate(uuid: String) = new {
    def to(destination: Tuple2[String, Int]): Unit = {
      if (_isRunning) {
        val (destHostname, destPort) = destination

        log.info("Theater at [%s:%d] received a request to migrate actor with UUID [%s] to theater at [%s:%d].",
            hostname, port, uuid, destHostname, destPort)

        val ref = mobileActors.get(uuid)
        if (ref != null) {
          val actorBytes = ref.startMigration(destHostname, destPort)
          sendToTheater(MovingActor(actorBytes, /* sender */ node), TheaterNode(destHostname, destPort))
        } else {
          log.warning("Theater at [%s:%d] received a request to migrate actor with UUID [%s], but the actor was " +
            "not found.", hostname, port, uuid)
        }
      }
    }
  }
  
  /* Complete the actor migration in the sender theater */
  def finishMigration(actorUuid: String): Unit = {
    log.debug("Finishing the migration process of actor with UUID [%s]", actorUuid)
    val actor = mobileActors.get(actorUuid)
    if (actor != null) {
      //println("RETAINED MESSAGES: " + actor.retained)
      //println("MAILBOX: " + actor.mb)
      actor.endMigration()
      this.unregister(actor)
      // TODO destruir instancia
    }
  }

  /**
   * Instantiates an actor migrating from another theater, starts and registers it.
   */
  def receiveActor(bytes: Array[Byte], sender: TheaterNode): Unit = {
    if (_isRunning) {
      log.debug("Theater at [%s:%d] just received a migrating actor from [%s:%d]", 
        hostname, port, sender.hostname, sender.port)

      val mobileRef = MobileSerialization.mobileFromBinary(bytes)(DefaultActorFormat)
      register(mobileRef)
      NameService.put(mobileRef.uuid, this.node)
      
      // Notifying the sender that the actor is now registered in this theater
      sendToTheater(MobileActorRegistered(mobileRef.uuid), sender)
    }
  }

  def isLocal(hostname: String, port: Int): Boolean = (LocalTheater.hostname == hostname && LocalTheater.port == port)
}


