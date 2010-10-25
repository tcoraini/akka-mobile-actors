package se.scalablesolutions.akka.mobile

import se.scalablesolutions.akka.actor.ActorRef
import se.scalablesolutions.akka.actor.Actor
import se.scalablesolutions.akka.actor.Actor._
import se.scalablesolutions.akka.actor.RemoteActorSerialization

import se.scalablesolutions.akka.remote.RemoteNode
import se.scalablesolutions.akka.remote.RemoteServer
import se.scalablesolutions.akka.remote.protocol.RemoteProtocol._
import se.scalablesolutions.akka.remote.MessageSerializer
import se.scalablesolutions.akka.remote.protocol.RemoteProtocol._

import se.scalablesolutions.akka.mobile.Mobile._

import java.net.InetSocketAddress
import java.util.Map
import java.util.concurrent.{ConcurrentHashMap, Executors}

import org.jboss.netty.bootstrap.ServerBootstrap
import org.jboss.netty.channel._
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory
import org.jboss.netty.handler.codec.frame.{LengthFieldBasedFrameDecoder, LengthFieldPrepender}
import org.jboss.netty.handler.codec.protobuf.{ProtobufDecoder, ProtobufEncoder}
import org.jboss.netty.handler.codec.compression.{ZlibEncoder, ZlibDecoder}

case class MovingActor(bytes: Array[Byte])
case class MobileActorRegistered(actorId: String)

class Theater {
  
  val server = new RemoteServer

  // FIXME melhor colocar MobileActorRef?
  private val mobileActors = new ConcurrentHashMap[String, MobileActorRef]

  //private val actors = new ConcurrentHashMap[String, ActorRef]

  def start(hostname: String, port: Int) = {
    server.setPipelineFactoryCreator(new TheaterPipelineFactoryCreator(mobileActors))
    server.start(hostname, port)

    val agentName = "theater@" + hostname + ":" + port
    server.register(agentName, actorOf(new TheaterAgent(this)))
  }

  def migrate(uuid: String) = new {
    def to(destination: Tuple2[String, Int]): Boolean = {
      mobileActors.get(uuid) match {
        case actorRef: MobileActorRef => 
          actorRef.migrateTo(destination._1, destination._2)

        case null => false // Actor not found
      }
    }
  }

  def register(actor: MobileActorRef) = {
    mobileActors.put(actor.mobid, actor)
  }

  def receiveActor(bytes: Array[Byte], senderOption: Option[ActorRef] = None): ActorRef = {
    println("[THEATER] Received actor")
    val actor = mobileFromBinary(bytes)(DefaultActorFormat)
    register(new MobileActorRef(actor))
    actor
  }

}

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

/*
 * OLD
 */
 
object Theater {
  // FIXME Pq lazy?
  lazy val agent = actorOf(new TheaterAgent(new Theater))

  def start(hostname: String, port: Int): Unit = {
    RemoteNode.start(hostname, port)
    val agentName = "theater@" + hostname + ":" + port
    RemoteNode.register(agentName, agent)
  }

  def receiveActor(bytes: Array[Byte], senderOption: Option[ActorRef] = None): ActorRef = {
    println("[THEATER] Received actor")
    val actor = mobileFromBinary(bytes)(DefaultActorFormat)
    RemoteNode.register(actor)
    //senderOption.foreach { sender => sender ! MobileActorRegistered(actor.id) }
    actor
  }
} 

class TheaterAgent(val theater: Theater) extends Actor {
  
  def receive = {
    case MovingActor(bytes) =>
      val actor = theater.receiveActor(bytes, self.sender)
      self.reply(MobileActorRegistered(actor.id))
      
    case msg => 
      println("TheaterAgent received something unknown: " + msg)
  }
}
