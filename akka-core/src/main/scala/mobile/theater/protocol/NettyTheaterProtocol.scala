package se.scalablesolutions.akka.mobile.theater.protocol

import se.scalablesolutions.akka.mobile.theater.Theater
import se.scalablesolutions.akka.mobile.theater.TheaterNode
import se.scalablesolutions.akka.mobile.util.messages._
import se.scalablesolutions.akka.mobile.theater.protocol.protobuf.ProtobufTheaterMessages._

import se.scalablesolutions.akka.util.Logging
import se.scalablesolutions.akka.actor.Actor
import se.scalablesolutions.akka.actor.ActorRef

import java.net.InetSocketAddress
import java.util.concurrent.Executors
import org.jboss.netty.bootstrap.ServerBootstrap
import org.jboss.netty.bootstrap.ClientBootstrap
import org.jboss.netty.channel._
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory
import org.jboss.netty.handler.codec.frame.{LengthFieldBasedFrameDecoder, LengthFieldPrepender}
import org.jboss.netty.handler.codec.protobuf.{ProtobufDecoder, ProtobufEncoder}
// import org.jboss.netty.handler.codec.compression.{ZlibEncoder, ZlibDecoder}
// import org.jboss.netty.handler.ssl.SslHandler

import scala.collection.mutable.HashMap

final case class SendTo(node: TheaterNode, message: TheaterMessageProtocol)

class NettyTheaterProtocol extends ProtobufProtocol {
  
  private val port = 2005 // TODO Parametrizar
  private var hostname: String = _

  private val factory = new NioServerSocketChannelFactory(
    Executors.newCachedThreadPool,
    Executors.newCachedThreadPool)
  private val bootstrap = new ServerBootstrap(factory)

  private val clientChannels = new HashMap[TheaterNode, Channel]

  private[protocol] var upstreamActor: ActorRef = _
  private var downstreamActor: ActorRef = _

  override def init(theater: Theater) {
    super.init(theater)

    startActors()

    hostname = theater.node.hostname
    
    val pipelineFactory = new NettyTheaterProtocolPipelineFactory(this, true)
    bootstrap.setPipelineFactory(pipelineFactory)

    bootstrap.setOption("child.tcpNoDelay", true)
    bootstrap.setOption("child.keepAlive", true)
    bootstrap.setOption("child.reuseAddress", true)
    bootstrap.setOption("child.connectTimeoutMillis", 1000) // TODO Parametrizar
    bootstrap.bind(new InetSocketAddress(hostname, port))
  }
  
  def sendTo(node: TheaterNode, message: TheaterMessageProtocol) {
    //channelFor(node).write(message)
    downstreamActor ! SendTo(node, message)
  }

  private def startActors() {
    upstreamActor = Actor.actor {
      case message: TheaterMessageProtocol => processMessage(message)
      case any => () // discard
    }

    downstreamActor = Actor.actor {
      case SendTo(node, message) => channelFor(node).write(message)
      case any => () // discard
    }
  }

  private def channelFor(node: TheaterNode): Channel = clientChannels.get(node) match {
    case Some(channel) => channel
    
    case None => 
      val channel = connectToTheater(node)
      clientChannels += node -> channel
      channel
  }
   
  private def connectToTheater(node: TheaterNode): Channel = {
    val channelFactory = new NioClientSocketChannelFactory(
      Executors.newCachedThreadPool,
      Executors.newCachedThreadPool)

    val bootstrap = new ClientBootstrap(channelFactory)
    bootstrap.setPipelineFactory(new NettyTheaterProtocolPipelineFactory(null, false))
    bootstrap.setOption("tcpNoDelay", true)
    bootstrap.setOption("keepAlive", true)
    val connection = bootstrap.connect(new InetSocketAddress(node.hostname, port))
    connection.awaitUninterruptibly.getChannel
  }
}

class NettyTheaterProtocolPipelineFactory(val protocol: NettyTheaterProtocol, upstream: Boolean) extends ChannelPipelineFactory {
  def getPipeline: ChannelPipeline = {
    def join(ch: ChannelHandler*) = Array[ChannelHandler](ch:_*)

    // lazy val engine = {
    //   val e = RemoteServerSslContext.server.createSSLEngine()
    //   e.setEnabledCipherSuites(e.getSupportedCipherSuites) //TODO is this sensible?
    //   e.setUseClientMode(false)
    //   e
    // }

//    val ssl         = if(RemoteServer.SECURE) join(new SslHandler(engine)) else join()
    val lenDec      = new LengthFieldBasedFrameDecoder(1048576, 0, 4, 0, 4)
    val lenPrep     = new LengthFieldPrepender(4)
    val protobufDec = new ProtobufDecoder(TheaterMessageProtocol.getDefaultInstance)
    val protobufEnc = new ProtobufEncoder
    // val (enc,dec)   = RemoteServer.COMPRESSION_SCHEME match {
    //   case "zlib"  => (join(new ZlibEncoder(RemoteServer.ZLIB_COMPRESSION_LEVEL)), join(new ZlibDecoder))
    //   case       _ => (join(), join())
    // }

//    val stages = ssl ++ dec ++ join(lenDec, protobufDec) ++ enc ++ join(lenPrep, protobufEnc, remoteServer)
    val stages = 
      if (upstream) {
	val protocolHandler = new NettyTheaterProtocolHandler(protocol)
	join(lenDec, protobufDec, protocolHandler)
      } else join(lenPrep, protobufEnc)

    new StaticChannelPipeline(stages: _*)
  }
}

@ChannelHandler.Sharable
class NettyTheaterProtocolHandler(val protocol: NettyTheaterProtocol) extends SimpleChannelUpstreamHandler with Logging {
  override def messageReceived(ctx: ChannelHandlerContext, event: MessageEvent) = {
    val message = event.getMessage
    if (message eq null) throw new /*IllegalActorState*/ RuntimeException("Message received by theater through Netty protocol is null: " + event)
    message match {
      case m: TheaterMessageProtocol => //protocol.processMessage(m)
	protocol.upstreamActor ! m
      
      case _ => () // discard
    }
  }

}
