package se.scalablesolutions.akka.mobile.util.messages

import se.scalablesolutions.akka.mobile.theater.TheaterNode

/**
 * Migration-related messages
 */

// Message that request an actor to migrate to some node
case class MoveTo(hostname: String, port: Int)

// Message that request all actors from a group to migrate together to some node
case class MoveGroupTo(hostname: String, port: Int)

// Internal message for the group migration process
private[mobile] case object PrepareToMigrate


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
case class MobileActorsRegistered(uuids: Array[String]) extends TheaterMessage // TODO List para case generico?

case class MovingGroup(bytes: Array[Array[Byte]]) extends TheaterMessage

case class StartMobileActorRequest(requestId: Long, className: String) extends TheaterMessage
case class StartMobileActorReply(requestId: Long, uuid: String) extends TheaterMessage

case class StartColocatedActorsRequest(requestId: Long, className: String, number: Int) extends TheaterMessage
case class StartColocatedActorsReply(requestId: Long, uuids: Array[String]) extends TheaterMessage

case class StartMobileActorsGroupRequest(requestId: Long, constructor: Either[Tuple2[String, Int], List[Array[Byte]]]) extends TheaterMessage
case class StartMobileActorsGroupReply(requestId: Long, uuids: List[String]) extends TheaterMessage

case class ActorNewLocationNotification(uuid: String, hostname: String, port: Int) extends TheaterMessage

// Messages for the tracking system
case class MobTrackMigrate(uuid: String, from: TheaterNode, to: TheaterNode) extends TheaterMessage
case class MobTrackArrive(uuid: String, node: TheaterNode) extends TheaterMessage
case class MobTrackDepart(uuid: String, node: TheaterNode) extends TheaterMessage

/**
 * Message that wraps a request for a remote mobile actor
 */
case class MobileActorMessage(senderHostname: String, senderPort: Int, message: Any)

