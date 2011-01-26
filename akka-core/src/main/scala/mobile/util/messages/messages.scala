package se.scalablesolutions.akka.mobile.util.messages

import se.scalablesolutions.akka.mobile.theater.TheaterNode

/**
 * Message that request an actor to migrate to some node
 */
case class MoveTo(hostname: String, port: Int)

/**
 * Name Service messages:
 */
case class ActorRegistrationRequest(acturUuid: String, hostname: String, port: Int)
case class ActorUnregistrationRequest(actorUuid: String)

case class ActorLocationRequest(actorUuid: String)
case class ActorLocationResponse(hostname: String, port: Int)
case object ActorNotFound

/**
 * Inter-theater messages
 */
trait TheaterMessage {
  var sender: Option[TheaterNode] = None
}

case class MovingActor(bytes: Array[Byte]) extends TheaterMessage
case class MobileActorRegistered(uuid: String) extends TheaterMessage

case class StartMobileActorRequest(requestId: Long, constructor: Either[String, Array[Byte]]) extends TheaterMessage
case class StartMobileActorReply(requestId: Long, uuid: String) extends TheaterMessage

case class StartMobileActorsGroupRequest(requestId: Long, constructor: Either[Tuple2[String, Int], List[Array[Byte]]]) extends TheaterMessage
case class StartMobileActorsGroupReply(requestId: Long, uuids: List[String]) extends TheaterMessage

case class ActorNewLocationNotification(uuid: String, hostname: String, port: Int) extends TheaterMessage

/**
 * Message that wraps a request for a remote mobile actor
 */
case class MobileActorMessage(senderHostname: String, senderPort: Int, message: Any)

