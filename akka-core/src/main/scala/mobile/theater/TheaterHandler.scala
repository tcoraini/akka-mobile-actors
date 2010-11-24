package se.scalablesolutions.akka.mobile.theater

import se.scalablesolutions.akka.mobile.nameservice.NameService
import se.scalablesolutions.akka.mobile.actor.MobileActorRef
import se.scalablesolutions.akka.mobile.Mobile
import se.scalablesolutions.akka.mobile.actor.MobileActorMessage

import se.scalablesolutions.akka.actor.ActorRef
import se.scalablesolutions.akka.actor.Actor

import se.scalablesolutions.akka.remote.protocol.RemoteProtocol._
import se.scalablesolutions.akka.remote.MessageSerializer

import se.scalablesolutions.akka.util.Logging

import java.util.Map

import org.jboss.netty.channel._

case class ActorNewLocationNotification(uuid: String, hostname: String, port: Int)

@ChannelHandler.Sharable // FIXME eh mesmo?
class TheaterHandler(actors: Map[String, MobileActorRef], theater: Theater) extends SimpleChannelUpstreamHandler with Logging {

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
        val message = MessageSerializer.deserialize(request.getMessage) match {
          case MobileActorMessage(_, _, msg) => msg
          case msg => msg
        }
        // TODO Descomentar
        /*val sender =
          // FIXME classloader sempre como None? 
          if (request.hasSender) Some(RemoteActorSerialization.fromProtobufToRemoteActorRef(request.getSender, None))
          else None*/

        actor.!(message)(None)

      case null =>
        log.debug("Actor with UUID [%s] not found at theater [%s:%d].", uuid, theater.hostname, theater.port)
        handleActorNotFound(request)
    }
  }

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

        // Notifying the sender theater
        if (senderNode.isDefined) {
          log.debug("Notifying the sender of the message at [%s:%d] the new location of the actor.", 
            senderNode.get.hostname, senderNode.get.port)
            
          // TODO Gambiarra monstro
          (new Thread() {
            override def run(): Unit = {
              TheaterHelper.sendToTheater(ActorNewLocationNotification(uuid, node.hostname, node.port), senderNode.get)
            }
          }).start()
        }

      case None =>
        log.debug("The actor with UUID [%s] not found in the cluster.", uuid)
        ()
    }
    // procurar no serviço de nomes
  }

}

