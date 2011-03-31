package se.scalablesolutions.akka.mobile.theater

import se.scalablesolutions.akka.mobile.actor.MobileActorRef
import se.scalablesolutions.akka.mobile.actor.MobileActor
import se.scalablesolutions.akka.mobile.serialization.MobileSerialization
import se.scalablesolutions.akka.mobile.serialization.DefaultActorFormat
import se.scalablesolutions.akka.mobile.util.PipelineFactoryCreator
import se.scalablesolutions.akka.mobile.util.ClusterConfiguration
import se.scalablesolutions.akka.mobile.Mobile
import se.scalablesolutions.akka.mobile.util.messages._
import se.scalablesolutions.akka.mobile.theater.protocol.TheaterProtocol
import se.scalablesolutions.akka.mobile.theater.protocol.AgentProtobufProtocol
import se.scalablesolutions.akka.mobile.theater.protocol.AgentProtocol
import se.scalablesolutions.akka.mobile.nameservice.NameService
import se.scalablesolutions.akka.mobile.nameservice.DistributedNameService
import se.scalablesolutions.akka.mobile.tools.mobtrack.MobTrackGUI

import se.scalablesolutions.akka.actor.ActorRef
import se.scalablesolutions.akka.actor.RemoteActorSerialization
import se.scalablesolutions.akka.remote.RemoteServer
import se.scalablesolutions.akka.remote.protocol.RemoteProtocol._
import se.scalablesolutions.akka.remote.MessageSerializer
import se.scalablesolutions.akka.config.Config
import se.scalablesolutions.akka.util.Logging

import java.util.concurrent.ConcurrentHashMap
import collection.JavaConversions._

object LocalTheater extends Theater

private[mobile] trait Theater extends Logging {
  
  private val server = new RemoteServer
  
  // Mobile actors running in this theater
  private val mobileActors = new ConcurrentHashMap[String, MobileActorRef]
 
  private var _pipelineFactoryCreator: PipelineFactoryCreator = new TheaterPipelineFactoryCreator(mobileActors, this)

  private var _profiler: Profiler = _
  
//  var _protocol: TheaterProtocol = new AgentProtobufProtocol(this)
  var _protocol: TheaterProtocol = new AgentProtocol(this)

  private var mobTrack = false
  private var mobTrackNode: Option[TheaterNode] = None

  // The address of this Theater
  private var _node: TheaterNode = _
  def node = _node

  private var _isRunning = false
  def isRunning = _isRunning

  def profiler = _profiler

  def start(nodeName: String): Boolean = ClusterConfiguration.nodes.get(nodeName) match {
    case Some(description) => start(description)

    case None => 
      log.warning("There is no description for a node with name '%s' in the configuration file", nodeName)
      false
  }

  def start(hostname: String, port: Int): Boolean = {
    val nodeDescription = ClusterConfiguration.nodes.values.filter {
      desc => desc.node.hostname == hostname && desc.node.port == port
    }
    
    if (nodeDescription.size == 0) {
      log.warning("There is no description for a node at %s in the configuration file.", TheaterNode(hostname, port).format)
      false
    } else {
      // 'nodeDescription' is an Iterable[TheaterDescription], so we take the first (and hopefully only) element
      start(nodeDescription.head) 
    }
  }
  
  private def start(description: TheaterDescription): Boolean = {
    import description.{node => descriptionNode, name, profiling, hasNameServer}

    val profilingLabel = if (profiling) "Enabled" else "Disabled"
    val nameServerLabel = if (hasNameServer) "Enabled" else "Disabled"
    log.info("Starting theater at %s with...\n" + 
	     "\t name: %s\n" + 
	     "\t name server: %s\n" + 
	     "\t profiling: %s", descriptionNode.format, name, profilingLabel, nameServerLabel)

    _node = descriptionNode

    server.setPipelineFactoryCreator(_pipelineFactoryCreator)
    server.start(node.hostname, node.port)

    // TODO configurar por arquivo de conf
    _protocol.init()

    NameService.init()

    _profiler = new Profiler(this.node)
    
    if (Config.config.getString("cluster.mob-track.node").isDefined) {
      configureMobTrack()
    }

    _isRunning = server.isRunning
    // Returns true if the theater has been started properly
    _isRunning
  }
  
  def shutdown(): Unit = {
    log.info("Shutting down theater at %s.", node.format)

    mobileActors.values.foreach { ref =>
      NameService.remove(ref.uuid)
      ref.stop
    }
    mobileActors.clear
    
    server.shutdown
    _isRunning = false
  }
  
  def register(actor: MobileActorRef, fromMigration: Boolean = false): Unit = {
    if (_isRunning && actor != null) {
      mobileActors.put(actor.uuid, actor)
      
      // Registering in the name server
      NameService.put(actor.uuid, this.node)
      if (!fromMigration) {
	if (mobTrack) {
	  sendTo(mobTrackNode.get, MobTrackArrive(actor.uuid, this.node))
	}
      }
 
      log.debug("Registering actor with UUID [%s] in theater at %s.", actor.uuid, node.format)
    } // TODO verificar isso, tratamento quando o theater nao estao rodando
  }

  def unregister(actor: MobileActorRef, afterMigration: Boolean = false): Unit = {
    if (_isRunning && actor != null) {
      mobileActors.remove(actor.uuid)
      NameService.remove(actor.uuid)
      if (!afterMigration) {
	if (mobTrack) {
	  sendTo(mobTrackNode.get, MobTrackDepart(actor.uuid, this.node))
	}
      }

      log.debug("Unregistering actor with UUID [%s] from theater at %s", actor.uuid, node.format)
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
	val sender = findMessageSender(request)
	actor.!(message)(sender)

      // The actor is not here. Possibly it has been migrated to some other theater.
      case null =>
        log.debug("Actor with UUID [%s] not found at theater %s.", uuid, node.format)
        handleActorNotFound(request)
    }
  }

  private def findMessageSender(request: RemoteRequestProtocol): Option[ActorRef] = {
    if (request.hasSender) {
      val sender = request.getSender
      MobileActorRef(sender.getUuid) match {
	// Sender is a mobile actor
	case Some(actorRef) => Some(actorRef)
	// Sender is not a mobile actor, construct proxy as usual (from Akka)
	case None => Some(RemoteActorSerialization.fromProtobufToRemoteActorRef(sender, None)) // TODO e esse None?
      }
    } else None
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
        log.debug("Actor with UUID [%s] found at %s. The message will be redirected to it.", uuid, node.format)

        val actorRef = MobileActorRef(uuid, node.hostname, node.port)
        val (senderNode, message) = MessageSerializer.deserialize(request.getMessage) match {
          case MobileActorMessage(senderHostname, senderPort, msg) => (Some(TheaterNode(senderHostname, senderPort)), msg)
          case msg => (None, msg)
        }

        actorRef.!(message)(None) // TODO

        // Notifying the sender theater about the address change
        if (senderNode.isDefined) {
          log.debug("Notifying the sender of the message at %s the new location of the actor.", 
            senderNode.get.format)
          
          sendTo(senderNode.get, ActorNewLocationNotification(uuid, node.hostname, node.port))
        }

      case None =>
        log.debug("The actor with UUID [%s] not found in the cluster.", uuid)
        ()
    }
  }
  
  /**
   * Starts a local actor by its class name in this theater. This method will be called whenever
   * an actor is spawned somewhere using its class name, and the system decides that it should be run
   * in this theater.
   *
   * @param className the class name of the actor that should be spawned
   *
   * @return the reference of the new actor started
   */
  def startActorByClassName(className: String): MobileActorRef = {
    log.debug("Starting an actor of class %s at theater %s", className, node.format)
    val mobileRef = MobileActorRef(Class.forName(className).asInstanceOf[Class[_ <: MobileActor]])
    mobileRef.start
//    this.register(mobileRef)
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
//      this.register(ref)
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

        log.info("Theater at %s received a request to migrate actor with UUID [%s] to theater at %s.",
            node.format, uuid, TheaterNode(destHostname, destPort).format)

        val ref = mobileActors.get(uuid)
        if (ref != null) {
          val actorBytes = ref.startMigration()
          sendTo(TheaterNode(destHostname, destPort), MovingActor(actorBytes))
        } else {
          log.warning("Theater at %s received a request to migrate actor with UUID [%s], but the actor was " +
            "not found.", node.format, uuid)
        }
      }
    }
  }
  
  private[mobile] def migrate(actor: MobileActorRef, destination: TheaterNode): Unit = {
    if (_isRunning) {
      log.info("Theater at %s received a request to migrate actor with UUID [%s] to theater at %s.",
	       node.format, actor.uuid, destination.format)
      
      if (mobileActors.get(actor.uuid) == null) {
	throw new RuntimeException("Actor not registered in this theater, can't go on with migration")
      }

      val actorBytes = actor.startMigration()
      sendTo(destination, MovingActor(actorBytes))
    }
  }

  private[mobile] def migrateGroup(actorsBytes: Array[Array[Byte]], destination: TheaterNode): Unit = {
    if (_isRunning) {
      log.info("Theater at %s performing migration of %d actors to theater at %s.", 
	       node.format, actorsBytes.size, destination.format)

      sendTo(destination, MovingGroup(actorsBytes))
    }
  }

  /* Complete the actor migration in the sender theater */
  def finishMigration(uuids: Array[String], destination: TheaterNode): Unit = {
    uuids.foreach { uuid =>
      log.debug("Finishing the migration process of actor with UUID [%s]", uuid)
      profiler.remove(uuid)
      val actor = mobileActors.get(uuid)
      if (actor != null) {
	//println("RETAINED MESSAGES: " + actor.retained)
	//println("MAILBOX: " + actor.mb)
	actor.endMigration(destination)
	//      this.unregister(actor, true)
	// TODO destruir instancia
      }
    }
  }

  /**
   * Instantiates an actor migrating from another theater, starts and registers it.
   */
  def receiveActor(bytes: Array[Byte], sender: Option[TheaterNode]): String = {
    if (_isRunning) {
      if (!sender.isDefined)
        throw new RuntimeException("Can't perform a migration without knowing the original node of the actor.")

      log.debug("Theater at %s just received a migrating actor from %s.", 
        node.format, sender.get.format)

      val mobileRef = MobileSerialization.mobileFromBinary(bytes)(DefaultActorFormat)
      mobileRef.groupId.foreach(id => GroupManagement.insert(mobileRef, id))
//      register(mobileRef, true)
//      NameService.put(mobileRef.uuid, this.node)
      if (mobTrack) {
	sendTo(mobTrackNode.get, MobTrackMigrate(mobileRef.uuid, sender.get, this.node))
      }
      mobileRef.uuid
    } else "" // TODO como fazer melhor?
  }

  def processMessage(message: TheaterMessage): Unit = message match {
    case MovingActor(bytes) =>
      val uuid = receiveActor(bytes, message.sender)
      // Notifying the sender that the actor is now registered in this theater
      sendTo(message.sender.get, MobileActorsRegistered(Array(uuid)))

    case MobileActorsRegistered(uuids) =>
      finishMigration(uuids, message.sender.get)

    case MovingGroup(actorsBytes) =>
      val uuids = for { 
	bytes <- actorsBytes
	uuid = receiveActor(bytes, message.sender)
      } yield uuid
      sendTo(message.sender.get, MobileActorsRegistered(uuids))

    case StartMobileActorRequest(requestId, className) =>
      val ref = startActorByClassName(className)
      sendTo(message.sender.get, StartMobileActorReply(requestId, ref.uuid))

    case StartMobileActorReply(requestId, uuid) =>
      TheaterHelper.completeActorSpawn(requestId, uuid, message.sender.get)
    
    case StartColocatedActorsRequest(requestId, className, number) => {
      log.debug("Starting %d colocated actors of class %s in theater %s.", number, className, node.format)
      val groupId = GroupManagement.newGroupId
      val uuids = new Array[String](number)
      for (i <- 0 to (number - 1)) {
	val ref = startActorByClassName(className)
	ref.groupId = Some(groupId)
	uuids(i) = ref.uuid
      } 

      sendTo(message.sender.get, StartColocatedActorsReply(requestId, uuids))
    }
    
    case StartColocatedActorsReply(requestId, uuids) =>
      TheaterHelper.completeColocatedActorsSpawn(requestId, uuids, message.sender.get)

    case StartMobileActorsGroupRequest(requestId, constructor) =>
      val refs = startLocalActorsGroup(constructor)
      val uuids = refs.map(ref => ref.uuid)
      sendTo(message.sender.get, StartMobileActorsGroupReply(requestId, uuids))

//    case reply: StartMobileActorsGroupReply =>
//      TheaterHelper.completeActorsGroupSpawn(reply)
    
    case ActorNewLocationNotification(uuid, newHostname, newPort) =>
      log.debug("Theater at %s received a notification that actor with UUID [%s] has migrated " + 
        "to %s.", node.format, uuid, TheaterNode(newHostname, newPort).format)

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
      log.debug("Theater at %s received an unknown message: %s. Discarding it.", node.format, trash)
  }

  def isLocal(hostname: String, port: Int): Boolean = 
    (LocalTheater.node.hostname == hostname && LocalTheater.node.port == port)

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
        log.debug("MobTrack is activated and running at node %s", mobTrackNode.get.format)
      case _ =>
	log.debug("MobTrack is not running.")
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


