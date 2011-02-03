package se.scalablesolutions.akka.mobile.theater

import se.scalablesolutions.akka.mobile.actor.MobileActorRef
import se.scalablesolutions.akka.mobile.actor.MobileActor
import se.scalablesolutions.akka.mobile.serialization.MobileSerialization
import se.scalablesolutions.akka.mobile.serialization.DefaultActorFormat
import se.scalablesolutions.akka.mobile.util.PipelineFactoryCreator
import se.scalablesolutions.akka.mobile.Mobile
import se.scalablesolutions.akka.mobile.util.messages._
import se.scalablesolutions.akka.mobile.theater.protocol.TheaterProtocol
import se.scalablesolutions.akka.mobile.theater.protocol.AgentProtobufProtocol
import se.scalablesolutions.akka.mobile.theater.protocol.AgentProtocol
import se.scalablesolutions.akka.mobile.nameservice.NameService
import se.scalablesolutions.akka.mobile.nameservice.DistributedNameService
import se.scalablesolutions.akka.mobile.tools.mobtrack.MobTrackGUI

import se.scalablesolutions.akka.actor.ActorRef
import se.scalablesolutions.akka.remote.RemoteServer
import se.scalablesolutions.akka.remote.protocol.RemoteProtocol._
import se.scalablesolutions.akka.remote.MessageSerializer
import se.scalablesolutions.akka.config.Config
import se.scalablesolutions.akka.util.Logging

import java.util.concurrent.ConcurrentHashMap

object LocalTheater extends Theater

private[mobile] trait Theater extends Logging {
  
  private val server = new RemoteServer
  
  // Mobile actors running in this theater
  private val mobileActors = new ConcurrentHashMap[String, MobileActorRef]
 
  private var _pipelineFactoryCreator: PipelineFactoryCreator = new TheaterPipelineFactoryCreator(mobileActors, this)

  private var _statistics: Statistics = _
  
//  var _protocol: TheaterProtocol = new AgentProtobufProtocol(this)
  var _protocol: TheaterProtocol = new AgentProtocol(this)

  private var mobTrack = false
  private var mobTrackNode: Option[TheaterNode] = None

  private var _hostname: String = _
  private var _port: Int = _

  def hostname: String = _hostname
  def port: Int = _port

  // The address of this Theater
  private var _node: TheaterNode = _
  def node = _node

  private var _isRunning = false
  def isRunning = _isRunning

  def statistics = _statistics

  def start(hostname: String, port: Int) = {
    log.debug("Starting a theater at %s:%d.", hostname, port)

    _hostname = hostname
    _port = port
    _node = TheaterNode(hostname, port)

    server.setPipelineFactoryCreator(_pipelineFactoryCreator)
    server.start(hostname, port)

    _protocol.init()

    NameService.init()

    _statistics = new Statistics(this.node)

    
    if (Config.config.getString("cluster.mob-track.node").isDefined) {
      configureMobTrack()
    }

    _isRunning = true
  }
  
  def register(actor: MobileActorRef, fromMigration: Boolean = false): Unit = {
    if (_isRunning) {
      mobileActors.put(actor.uuid, actor)
      actor.homeTheater = this
      
      // Registering in the name server
      NameService.put(actor.uuid, this.node)
      if (!fromMigration) {
	if (mobTrack) {
	  sendTo(mobTrackNode.get, MobTrackArrive(actor.uuid, this.node))
	}
      }
 
      log.debug("Registering actor with UUID [%s] in theater at [%s:%d].", actor.uuid, hostname, port)
    } // TODO verificar isso, tratamento quando o theater nao estao rodando
  }

  def unregister(actor: MobileActorRef, afterMigration: Boolean = false): Unit = {
    if (_isRunning) {
      mobileActors.remove(actor.uuid)
      NameService.remove(actor.uuid)
      if (!afterMigration) {
	if (mobTrack) {
	  sendTo(mobTrackNode.get, MobTrackDepart(actor.uuid, this.node))
	}
      }

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
   * Handles messages received from a remote theater to some mobile actor that supposedly is in
   * this theater.
   */
  def handleMobileActorRequest(request: RemoteRequestProtocol): Unit = {
    val uuid = request.getActorInfo.getUuid

    mobileActors.get(uuid) match {
      case actor: MobileActorRef =>
        val message = MessageSerializer.deserialize(request.getMessage)
        // TODO ver o negocio do sender
        actor.!(message)(None)

      // The actor is not here. Possibly it has been migrated to some other theater.
      case null =>
        log.debug("Actor with UUID [%s] not found at theater [%s:%d].", uuid, hostname, port)
        handleActorNotFound(request)
    }
  }
  
  /**
   * Handles the case where the actor was not found at this theater. It possibly has been migrated
   * to some other theater. First this theater will try to find the actor in some other node in the
   * cluster, using the name service. In case of success, it will redirect the message to the actor
   * and then notify the sender theater about the address change
   */
  private def handleActorNotFound(request: RemoteRequestProtocol): Unit = {
    val uuid = request.getActorInfo.getUuid
    NameService.get(uuid) match {
      case Some(node) =>
        log.debug("Actor with UUID [%s] found at [%s:%d]. The message will be redirected to it.", uuid, node.hostname, node.port)

        val actorRef = MobileActorRef(uuid, node.hostname, node.port)
        val (senderNode, message) = MessageSerializer.deserialize(request.getMessage) match {
          case MobileActorMessage(senderHostname, senderPort, msg) => (Some(TheaterNode(senderHostname, senderPort)), msg)
          case msg => (None, msg)
        }

        actorRef.!(message)(None) // TODO

        // Notifying the sender theater about the address change
        if (senderNode.isDefined) {
          log.debug("Notifying the sender of the message at [%s:%d] the new location of the actor.", 
            senderNode.get.hostname, senderNode.get.port)
          
          sendTo(senderNode.get, ActorNewLocationNotification(uuid, node.hostname, node.port))
        }

      case None =>
        log.debug("The actor with UUID [%s] not found in the cluster.", uuid)
        ()
    }
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

  def startLocalActorsGroup(constructor: Either[Tuple2[String, Int], List[Array[Byte]]]): List[MobileActorRef] = {
    val listOfMobileRefs = constructor match {
      case Left((classname, n)) =>
	(for (i <- 1 to n) yield MobileActorRef(Class.forName(classname).asInstanceOf[Class[_ <: MobileActor]])).toList
      
      case Right(list) =>
	(for (bytes <- list) yield MobileSerialization.mobileFromBinary(bytes)(DefaultActorFormat)).toList
    }
    
    val groupId = GroupManagement.newGroupId
    listOfMobileRefs.foreach(ref => {
      ref.groupId = Some(groupId)
      ref.start
      this.register(ref)
    })
    listOfMobileRefs
  }

  /**
   * Requests the migration of the actor with UUID 'uuid' to some destination, represented
   * by a (hostname, port) tuple.
   *
   * Syntax: 
   *  theater migrate UUID to (hostname, port) 
   * or, more verbosely:
   *  theater.migrate(UUID) to (hostname, port)
   */
  @deprecated("Usar migrate(actor, destination)")
  private[this] def migrate(uuid: String) = new {
    def to(destination: Tuple2[String, Int]): Unit = {
      if (_isRunning) {
        val (destHostname, destPort) = destination

        log.info("Theater at [%s:%d] received a request to migrate actor with UUID [%s] to theater at [%s:%d].",
            hostname, port, uuid, destHostname, destPort)

        val ref = mobileActors.get(uuid)
        if (ref != null) {
          val actorBytes = ref.startMigration(destHostname, destPort)
          sendTo(TheaterNode(destHostname, destPort), MovingActor(actorBytes))
        } else {
          log.warning("Theater at [%s:%d] received a request to migrate actor with UUID [%s], but the actor was " +
            "not found.", hostname, port, uuid)
        }
      }
    }
  }
  
  private[mobile] def migrate(actor: MobileActorRef, destination: TheaterNode): Unit = {
    if (_isRunning) {
      log.info("Theater at [%s:%d] received a request to migrate actor with UUID [%s] to theater at [%s:%d].",
	       hostname, port, actor.uuid, destination.hostname, destination.port)
      
      if (mobileActors.get(actor.uuid) == null) {
	throw new RuntimeException("Actor not registered in this theater, can't go on with migration")
      }

      val actorBytes = actor.startMigration(destination.hostname, destination.port)
      sendTo(destination, MovingActor(actorBytes))
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
      this.unregister(actor, true)
      // TODO destruir instancia
    }
  }

  /**
   * Instantiates an actor migrating from another theater, starts and registers it.
   */
  def receiveActor(bytes: Array[Byte], sender: Option[TheaterNode]): Unit = {
    if (_isRunning) {
      if (!sender.isDefined)
        throw new RuntimeException("Can't perform a migration without knowing the original node of the actor.")

      log.debug("Theater at [%s:%d] just received a migrating actor from [%s:%d].", 
        hostname, port, sender.get.hostname, sender.get.port)

      val mobileRef = MobileSerialization.mobileFromBinary(bytes)(DefaultActorFormat)
      register(mobileRef, true)
      NameService.put(mobileRef.uuid, this.node)
      if (mobTrack) {
	sendTo(mobTrackNode.get, MobTrackMigrate(mobileRef.uuid, sender.get, this.node))
      }

      // Notifying the sender that the actor is now registered in this theater
      sendTo(sender.get, MobileActorRegistered(mobileRef.uuid))
    }
  }

  def processMessage(message: TheaterMessage): Unit = message match {
    case MovingActor(bytes) =>
      receiveActor(bytes, message.sender)

    case StartMobileActorRequest(requestId, constructor) =>
      val ref = startLocalActor(constructor)
      sendTo(message.sender.get, StartMobileActorReply(requestId, ref.uuid))

    case reply: StartMobileActorReply =>
      TheaterHelper.completeActorSpawn(reply)

    case StartMobileActorsGroupRequest(requestId, constructor) =>
      val refs = startLocalActorsGroup(constructor)
      val uuids = refs.map(ref => ref.uuid)
      sendTo(message.sender.get, StartMobileActorsGroupReply(requestId, uuids))

    case reply: StartMobileActorsGroupReply =>
      TheaterHelper.completeActorsGroupSpawn(reply)
    
    case MobileActorRegistered(uuid) =>
      finishMigration(uuid)

    case ActorNewLocationNotification(uuid, newHostname, newPort) =>
      log.debug("Theater at [%s:%d] received a notification that actor with UUID [%s] has migrated " + 
        "to [%s:%d].", hostname, port, uuid, newHostname, newPort)

      val reference = ReferenceManagement.get(uuid)
      if (reference.isDefined) {
        reference.get.updateRemoteAddress(TheaterNode(newHostname, newPort))
      }

    case MobTrackMigrate(uuid, from, to) =>
      MobTrackGUI.migrate(uuid, from, to)

    case MobTrackArrive(uuid, node) =>
      MobTrackGUI.arrive(uuid, node)

    case MobTrackDepart(uuid, node) =>
      MobTrackGUI.depart(uuid, node)

    case trash =>
      log.debug("Theater at [%s:%d] received an unknown message: %s. Discarding it.", hostname, port, trash)
  }

  def isLocal(hostname: String, port: Int): Boolean = 
    (LocalTheater.hostname == hostname && LocalTheater.port == port)

  /**
   * PRIVATE METHODS
   */
  private def sendTo(node: TheaterNode, message: TheaterMessage): Unit = {
    _protocol.sendTo(node, message)
  }

  /*
   * Configuration of the mobile actors tracking system
   */
  private def configureMobTrack(): Unit = {
    val nodeName = Config.config.getString("cluster.mob-track.node").get
    val hostname = Config.config.getString("cluster." + nodeName + ".hostname")
    val port = Config.config.getInt("cluster." + nodeName + ".port")
    
    (hostname, port) match {
      case (Some(_hostname), Some(_port)) =>
	mobTrack = true
	mobTrackNode = Some(TheaterNode(_hostname, _port))
        log.debug("MobTrack activated and running at node [%s:%d]", _hostname, _port)
      
      case _ =>
	log.debug("MobTrack not running.")
        ()
    }
  }

  /**
   * SETTERS AND GETTERS 
   */
  
  def protocol: TheaterProtocol = _protocol

  def protocol_=(protocol: TheaterProtocol): Unit = {
    if (!_isRunning) {
      _protocol = protocol
    }
  }

  def pipelineFactoryCreator = _pipelineFactoryCreator

  def pipelineFactoryCreator_=(creator: PipelineFactoryCreator): Unit = {
    if (!_isRunning) {
      _pipelineFactoryCreator = creator
    }
  }

}

