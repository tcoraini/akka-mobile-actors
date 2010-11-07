package se.scalablesolutions.akka.mobile

import se.scalablesolutions.akka.actor.ActorRef

import se.scalablesolutions.akka.remote.protocol.RemoteProtocol._
import se.scalablesolutions.akka.remote.MessageSerializer

import java.util.Map

import org.jboss.netty.channel._

@ChannelHandler.Sharable // FIXME eh mesmo?
class TheaterHandler(actors: Map[String, MobileActorRef]) extends SimpleChannelUpstreamHandler {

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

  private def handleMobileActorRequest(request: RemoteRequestProtocol) = {
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
        handleActorNotFound(uuid)
    }
  }

  private def handleActorNotFound(uuid: String) = {
    // procurar no serviço de nomes
  }
}

