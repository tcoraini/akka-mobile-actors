package se.scalablesolutions.akka.mobile

import se.scalablesolutions.akka.actor.ActorRef
import se.scalablesolutions.akka.actor.Actor
import se.scalablesolutions.akka.actor.Actor._
import se.scalablesolutions.akka.actor.RemoteActorSerialization

import se.scalablesolutions.akka.remote.RemoteNode
import se.scalablesolutions.akka.remote.RemoteServer
import se.scalablesolutions.akka.remote.protocol.RemoteProtocol._
import se.scalablesolutions.akka.remote.MessageSerializer

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
  // TODO tratar conexoes (ChannelGroup) e fechar todas no final

  private val actors = new ConcurrentHashMap[String, ActorRef]

  private val factory = new NioServerSocketChannelFactory(
    Executors.newCachedThreadPool,
    Executors.newCachedThreadPool)

  private val bootstrap = new ServerBootstrap(factory)

  def start(hostname: String, port: Int) = {
    val pipelineFactory = new TheaterPipelineFactory(actors)
    bootstrap.setPipelineFactory(pipelineFactory)
    bootstrap.setOption("child.tcpNoDelay", true)
    bootstrap.setOption("child.keepAlive", true)
    bootstrap.setOption("child.reuseAddress", true)
    bootstrap.setOption("child.connectTimeoutMillis", RemoteServer.CONNECTION_TIMEOUT_MILLIS.toMillis)

    bootstrap.bind(new InetSocketAddress(hostname, port))
  }
}

class TheaterPipelineFactory(actors: Map[String, ActorRef]) extends ChannelPipelineFactory {
  def getPipeline: ChannelPipeline = {
  
    def join(ch: ChannelHandler*) = Array[ChannelHandler](ch:_*)

    // Strips out the first 4 bytes of messages (the message lenght)
    val lenDec      = new LengthFieldBasedFrameDecoder(1048576, 0, 4, 0, 4)
    // Adds the message lenght as 4 bytes in the beginning of the message
    val lenPrep     = new LengthFieldPrepender(4)
    // Encoder and decoder for the messages in the Protobuf format
    val protobufDec = new ProtobufDecoder(RemoteRequestProtocol.getDefaultInstance)
    val protobufEnc = new ProtobufEncoder
    // Compression methods
    val (compressionEnc, compressionDec) = RemoteServer.COMPRESSION_SCHEME match {
      case "zlib"  => (join(new ZlibEncoder(RemoteServer.ZLIB_COMPRESSION_LEVEL)), join(new ZlibDecoder))
      case       _ => (join(), join())
    }
    // TODO SSL?

    val theaterHandler = new TheaterHandler(actors)
    val stages = compressionDec ++ join(lenDec, protobufDec) ++ compressionEnc ++ join(lenPrep, protobufEnc, theaterHandler)
    // FIXME Pode ser preciso mudar de ChannelPipeline para permitir mudanças on-the-fly dos handlers, caso
    // o mecanismo de monitoramento da entrada de mensagens seja um handler opcional
    new StaticChannelPipeline(stages: _*)  
  }
}

@ChannelHandler.Sharable // FIXME eh mesmo?
class TheaterHandler(val actors: Map[String, ActorRef]) extends SimpleChannelUpstreamHandler {

  override def messageReceived(ctx: ChannelHandlerContext, event: MessageEvent) = {
    val message = event.getMessage
    if (message eq null) throw new RuntimeException("Message in remote MessageEvent is null: " + event)
    if (message.isInstanceOf[RemoteRequestProtocol])
      handleRemoteRequestProtocol(message.asInstanceOf[RemoteRequestProtocol])
  }

  private def handleRemoteRequestProtocol(request: RemoteRequestProtocol) = {
    val actorInfo = request.getActorInfo
    val uuid = actorInfo.getUuid
    
    actors.get(uuid) match {
      case actor: ActorRef =>
        val message = MessageSerializer.deserialize(request.getMessage)
        val sender =
          // FIXME classloader sempre como None? 
          if (request.hasSender) Some(RemoteActorSerialization.fromProtobufToRemoteActorRef(request.getSender, None))
          else None

        actor.!(message)(sender)

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
  lazy val agent = actorOf(new TheaterAgent)

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

class TheaterAgent extends Actor {
  
  def receive = {
    case MovingActor(bytes) =>
      val actor = Theater.receiveActor(bytes, self.sender)
      self.reply(MobileActorRegistered(actor.id))
      
    case msg => 
      println("TheaterAgent received something unknown: " + msg)
  }
}
