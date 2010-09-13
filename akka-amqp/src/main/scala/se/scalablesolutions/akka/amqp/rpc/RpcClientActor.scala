/**
 * Copyright (C) 2009-2010 Scalable Solutions AB <http://scalablesolutions.se>
 */

package se.scalablesolutions.akka.amqp

import com.rabbitmq.client.{Channel, RpcClient}
import rpc.RPC.RpcClientSerializer
import se.scalablesolutions.akka.amqp.AMQP.{ChannelParameters, ExchangeParameters}

class RpcClientActor[I,O](
    exchangeParameters: ExchangeParameters,
    routingKey: String,
    serializer: RpcClientSerializer[I,O],
    channelParameters: Option[ChannelParameters] = None)
    extends FaultTolerantChannelActor(exchangeParameters, channelParameters) {

  import exchangeParameters._

  var rpcClient: Option[RpcClient] = None

  log.info("%s started", this)

  def specificMessageHandler = {
    case payload: I => {
      rpcClient match {
        case Some(client) =>
          val response: Array[Byte] = client.primitiveCall(serializer.toBinary.toBinary(payload))
          self.reply(serializer.fromBinary.fromBinary(response))
        case None => error("%s has no client to send messages with".format(this))
      }
    }
  }

  protected def setupChannel(ch: Channel) = rpcClient = Some(new RpcClient(ch, exchangeName, routingKey))

  override def preRestart(reason: Throwable) = {
    rpcClient = None
    super.preRestart(reason)
  }


  override def shutdown = {
    rpcClient.foreach(rpc => rpc.close)
    super.shutdown
  }

  override def toString = "AMQP.RpcClient[exchange=" +exchangeName + ", routingKey=" + routingKey+ "]"
}
