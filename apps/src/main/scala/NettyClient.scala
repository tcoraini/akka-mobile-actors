package apps

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

import org.jboss.netty.channel._
import org.jboss.netty.bootstrap.ClientBootstrap
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory

import org.jboss.netty.handler.codec.frame.{LengthFieldBasedFrameDecoder, LengthFieldPrepender}
import org.jboss.netty.handler.codec.compression.{ZlibDecoder, ZlibEncoder}
import org.jboss.netty.handler.codec.protobuf.{ProtobufDecoder, ProtobufEncoder}

object NettyClient {
    
    val factory: ChannelFactory = new NioClientSocketChannelFactory(
            Executors.newCachedThreadPool(),
            Executors.newCachedThreadPool());

    var channel: Channel = _
    
    def connect(hostname: String, port: Int) {

      val bootstrap: ClientBootstrap = new ClientBootstrap(factory);

      bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
          def getPipeline(): ChannelPipeline = {
            val compr = new ZlibEncoder(6)
            val lenPrep     = new LengthFieldPrepender(4)
            val protobufEnc = new ProtobufEncoder
            Channels.pipeline(new StaticChannelPipeline(compr, lenPrep, protobufEnc, new BasicHandler()));
            //Channels.pipeline(new BasicHandler());
          }
      })
      
      bootstrap.setOption("tcpNoDelay", true);
      bootstrap.setOption("keepAlive", true);

      val future = bootstrap.connect(new InetSocketAddress(hostname, port))
      future.awaitUninterruptibly()

      if (!future.isSuccess()) {
          future.getCause().printStackTrace();
      } else {
        channel = future.getChannel
      }
    }

    def close() {
      channel.close().awaitUninterruptibly()
      factory.releaseExternalResources()
    }

    def send(msg: Any) {
      channel.write(msg)
    }
}

class BasicHandler extends SimpleChannelHandler {

    override def messageReceived(ctx: ChannelHandlerContext, e: MessageEvent): Unit = {
    }

    override def exceptionCaught(ctx: ChannelHandlerContext, e: ExceptionEvent): Unit = {
      e.getCause().printStackTrace();
        
      val ch: Channel = e.getChannel();
      ch.close();
    }
}
