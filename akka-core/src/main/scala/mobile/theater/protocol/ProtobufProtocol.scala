package se.scalablesolutions.akka.mobile.theater.protocol

import se.scalablesolutions.akka.mobile.theater.Theater
import se.scalablesolutions.akka.mobile.theater.TheaterNode

import se.scalablesolutions.akka.mobile.theater.protocol.protobuf.ProtobufTheaterMessages._
import se.scalablesolutions.akka.mobile.theater.protocol.protobuf.ProtobufTheaterMessages.{TheaterMessageType => MessageType}
import se.scalablesolutions.akka.mobile.util.messages._

import com.google.protobuf.ByteString
import com.google.protobuf.Message

import collection.JavaConversions._

abstract class ProtobufProtocol extends TheaterProtocol {
  
  /**
   * Abstract method, should be overriden by the subclasses.
   * It sends a message in the protobuf format to some node.
   */
  def sendTo(node: TheaterNode, message: TheaterMessageProtocol)
  
  /**
   * Constructs a Protobuf message based on the respective case class message.
   */
  def sendTo(node: TheaterNode, message: TheaterMessage): Unit = {
    // Constructs a protobuf message (of the types defined in TheaterProtocol.proto) based
    // on the TheaterMessage received
    val protobufMessage = message match {

      case MovingActor(bytes) =>
        MovingActorProtocol.newBuilder
            .setActorBytes(ByteString.copyFrom(bytes))
            .build

      case MobileActorsRegistered(uuids: Array[String]) =>
        MobileActorsRegisteredProtocol.newBuilder
            .addAllUuids(uuids.toList)
            .build

      case StartMobileActorRequest(requestId, className) => 
        StartActorRequestProtocol.newBuilder
            .setRequestId(requestId)
	    .setClassName(className)
	    .build

      case StartMobileActorReply(requestId, uuid) =>
        StartActorReplyProtocol.newBuilder
            .setRequestId(requestId)
            .setActorUuid(uuid)
            .build

      case ActorNewLocationNotification(uuid, hostname, port) =>
        ActorNewLocationNotificationProtocol.newBuilder
            .setUuid(uuid)
            .setHostname(hostname)
            .setPort(port)
            .build
    }
    
    // Wraps the message in a TheaterMessageProtocol kind of message, and sends it
    // to node.
    sendTo(node, constructProtobufMessage(protobufMessage))
  } 

  /**
   * Wraps a protobuf message (constructed in the sendTo method above) in a TheaterMessageProtocol
   * message, to be send over the wire to other theater.
   */
  private def constructProtobufMessage(message: Message): TheaterMessageProtocol = {
    val sender = TheaterNodeProtocol.newBuilder
        .setHostname(theater.node.hostname)
        .setPort(theater.node.port)
        .build
    val builder = TheaterMessageProtocol.newBuilder
        .setSender(sender)

    message match {
      case msg: MovingActorProtocol =>
        builder
          .setMessageType(MessageType.MOVING_ACTOR)
          .setMovingActor(msg)
          .build

      case msg: MobileActorsRegisteredProtocol =>
        builder
          .setMessageType(MessageType.MOBILE_ACTORS_REGISTERED)
          .setMobileActorsRegistered(msg)
          .build

      case msg: StartActorRequestProtocol =>
        builder
          .setMessageType(MessageType.START_ACTOR_REQUEST)
          .setStartActorRequest(msg)
          .build

      case msg: StartActorReplyProtocol =>
        builder
          .setMessageType(MessageType.START_ACTOR_REPLY)
          .setStartActorReply(msg)
          .build

      case msg: ActorNewLocationNotificationProtocol =>
        builder
          .setMessageType(MessageType.ACTOR_NEW_LOCATION_NOTIFICATION)
          .setActorNewLocationNotification(msg)
          .build
    }
  }
  
  /**
   * Processes a message received from some node, in the protobuf format. It transforms
   * that message in an instance of some TheaterMessage subclass, and passes this to
   * the theater.
   */
  def processMessage(message: TheaterMessageProtocol): Unit = {
    import TheaterMessageType._
    
    val theaterMessage = message.getMessageType match {
      case MOVING_ACTOR =>
        val bytes = message.getMovingActor.getActorBytes.toByteArray
        MovingActor(bytes)

      case START_ACTOR_REQUEST =>
        val request = message.getStartActorRequest
	StartMobileActorRequest(request.getRequestId, request.getClassName)

      case START_ACTOR_REPLY =>
        val reply = message.getStartActorReply
        StartMobileActorReply(reply.getRequestId, reply.getActorUuid)

      case MOBILE_ACTORS_REGISTERED =>
	val uuids = message.getMobileActorsRegistered.getUuidsList.asInstanceOf[List[String]]
        MobileActorsRegistered(uuids.toArray)
      
      case ACTOR_NEW_LOCATION_NOTIFICATION =>
        val notification = message.getActorNewLocationNotification
        ActorNewLocationNotification(notification.getUuid, notification.getHostname, notification.getPort)
    }
    theaterMessage.sender = Some(TheaterNode(message.getSender.getHostname, message.getSender.getPort))
    theater.processMessage(theaterMessage)
  }
}
