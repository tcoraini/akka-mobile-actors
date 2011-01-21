package se.scalablesolutions.akka.mobile.theater

import se.scalablesolutions.akka.mobile.util.messages._

import se.scalablesolutions.akka.util.Logging
import se.scalablesolutions.akka.config.Config

import scala.collection.mutable.HashMap
import java.util.PriorityQueue

object Statistics {
  val MESSAGES_RECEIVED_THRESHOLD: Int = 5
}

class Statistics extends Logging {

  /*private*/ val incomingMessagesRecord = new HashMap[String, HashMap[TheaterNode, MessagesReceived]]
  
  private val messagesReceivedThreshold = 
    Config.config.getInt("cluster.statistics.messages-received-threshold", Statistics.MESSAGES_RECEIVED_THRESHOLD)

  /*private*/ val priorityQueue = new PriorityQueue[MessagesReceived]

  def messageArrived(uuid: String, message: MobileActorMessage): Unit = {  
    val innerMap: HashMap[TheaterNode, MessagesReceived] = incomingMessagesRecord.get(uuid) match {
      case Some(map) => map

      case None => {
        val map = new HashMap[TheaterNode, MessagesReceived]
        incomingMessagesRecord.put(uuid, map)
        map
      }
    }

    val node = TheaterNode(message.senderHostname, message.senderPort)
    val messagesReceived = innerMap.get(node) match {
      case Some(messages) => messages
      
      case None => {
        val msgReceived = MessagesReceived(uuid, node)
        innerMap.put(node, msgReceived)
        msgReceived
      }
    }
    messagesReceived.increment

    updatePriorityQueue(messagesReceived)

    log.debug("Registering arrival of message to actor with UUID [%s] from node [%s:%d].", 
        uuid, node.hostname, node.port)
  }

  private def updatePriorityQueue(messagesReceived: MessagesReceived): Unit = {
    if (messagesReceived.count == messagesReceivedThreshold) {
      priorityQueue.add(messagesReceived)
    } else if (messagesReceived.count > messagesReceivedThreshold) {
      if (priorityQueue.remove(messagesReceived) == false)
        println("\n\n** DEU FALSO NA REMOÇÃO DE " + messagesReceived + " **\n\n")
      priorityQueue.add(messagesReceived)
    }
  }
  
  def getFirstInQueue: MessagesReceived = priorityQueue.peek

  def getMessagesCount(uuid: String): Option[HashMap[TheaterNode, MessagesReceived]] = incomingMessagesRecord.get(uuid)

}
