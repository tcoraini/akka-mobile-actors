package se.scalablesolutions.akka.mobile

import se.scalablesolutions.akka.actor.ActorRef
import se.scalablesolutions.akka.actor.Actor

import se.scalablesolutions.akka.remote.protocol.RemoteProtocol._
import se.scalablesolutions.akka.remote.MessageSerializer

import se.scalablesolutions.akka.util.Logging

import se.scalablesolutions.akka.mobile.nameservice.NameService

import java.util.Map

import org.jboss.netty.channel._

@ChannelHandler.Sharable // FIXME eh mesmo?
class TheaterHandler(actors: Map[String, MobileActorRef]) extends SimpleChannelUpstreamHandler with Logging {

  override def messageReceived(ctx: ChannelHandlerContext, event: MessageEvent) = {
    val message = event.getMessage
    if (message.isInstanceOf[RemoteRequestProtocol]) {
      val request = message.asInstanceOf[RemoteRequestProtocol]
      if (request.getActorInfo.getActorType == ActorType.MOBILE_ACTOR)
        handleMobileActorRequest(request)
      else ctx.sendUpstream(event)
    } 
    else ctx.sendUpstream(event)
  }

  private def handleMobileActorRequest(request: RemoteRequestProtocol): Unit = {
    val uuid = request.getActorInfo.getUuid
    
    actors.get(uuid) match {
      case actor: ActorRef =>
        val message = MessageSerializer.deserialize(request.getMessage)
        // TODO Descomentar
        /*val sender =
          // FIXME classloader sempre como None? 
          if (request.hasSender) Some(RemoteActorSerialization.fromProtobufToRemoteActorRef(request.getSender, None))
          else None*/

        actor.!(message)(None)

      case null =>
        log.debug("Actor with uuid '%s' not found at %s:%d", uuid, Theater.hostname, Theater.port)
        handleActorNotFound(request)
    }
  }

  private def handleActorNotFound(request: RemoteRequestProtocol): Unit = {
    val uuid = request.getActorInfo.getUuid
    NameService.get(uuid) match {
      case Some(node) =>
        log.debug("Actor with uuid '%s' found at %s:%d. Redirecting message to it", uuid, node.hostname, node.port)
        val actorRef = Mobile.newRemoteMobileActor(uuid, node.hostname, node.port, Actor.TIMEOUT)
        val message = MessageSerializer.deserialize(request.getMessage)

        actorRef.!(message)(None) // TODO

      case None =>
        log.debug("Actor with uuid '%s' not found in the cluster", uuid)
        ()
    }
    // procurar no serviço de nomes
  }

  // Find the new actor location by sending a request to the name server
  /*private def findActorNewLocation(uuid: String): Option[TheaterNode] = {
    val namingServer = NamingServiceControl.namingServerFor(uuid)
    log.debug("The location of actor with uuid '%s' is in the naming server at %s:%d", uuid, namingServer.hostname, namingServer.port)
    val agent = 
      if (namingServer.isLocal) Theater.localAgent
      else TheaterHelper.agentFor(namingServer)

    (agent !! ActorLocationRequest(uuid)) match {
      case Some(ActorLocationResponse(hostname, port)) =>
        Some(TheaterNode(hostname, port))

      case _ => None
    }
  }*/
}

