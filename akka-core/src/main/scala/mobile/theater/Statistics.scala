package se.scalablesolutions.akka.mobile.theater

import se.scalablesolutions.akka.mobile.util.messages._

import se.scalablesolutions.akka.util.Logging
import se.scalablesolutions.akka.config.Config

import scala.collection.mutable.HashMap

import java.util.PriorityQueue
import java.util.concurrent.ConcurrentHashMap

object Statistics {
  val MESSAGES_RECEIVED_THRESHOLD: Int = 5
}

class Statistics(private val localNode: TheaterNode) extends Logging {

  /*private*/ val incomingMessagesRecord = new ConcurrentHashMap[String, HashMap[TheaterNode, MessagesReceived]]
  
  private val messagesReceivedThreshold = 
    Config.config.getInt("cluster.statistics.messages-received-threshold", Statistics.MESSAGES_RECEIVED_THRESHOLD)

  /*private*/ val priorityQueue = new PriorityQueue[MessagesReceived]

  def localMessageArrived(uuid: String): Unit = {
    messageArrived(uuid, localNode, false)
  }

  def remoteMessageArrived(uuid: String, message: MobileActorMessage): Unit = {
    messageArrived(uuid, TheaterNode(message.senderHostname, message.senderPort), true)
  }

  def messageArrived(uuid: String, from: TheaterNode, usePriorityQueue: Boolean): Unit = {  
    val innerMap: HashMap[TheaterNode, MessagesReceived] = incomingMessagesRecord.get(uuid) match {
      case null => {
        val map = new HashMap[TheaterNode, MessagesReceived]
        incomingMessagesRecord.put(uuid, map)
        map
      }

      case map => map
    }

    val messagesReceived = innerMap.get(from) match {
      case Some(messages) => messages
      
      case None => {
        val msgReceived = MessagesReceived(uuid, from)
        innerMap.put(from, msgReceived)
        msgReceived
      }
    }
    messagesReceived.increment
    
    if (usePriorityQueue) {
      updatePriorityQueue(messagesReceived)
    }
    
    if (from == localNode) {
      log.debug("Registering arrival of local message to actor with UUID [%s].", uuid)
    } else {
      log.debug("Registering arrival of remote message to actor with UUID [%s] from node [%s:%d].",
		uuid, from.hostname, from.port)
    }
  }

  private def updatePriorityQueue(messagesReceived: MessagesReceived): Unit = {
    if (messagesReceived.count == messagesReceivedThreshold) {
      priorityQueue.add(messagesReceived)
    } else if (messagesReceived.count > messagesReceivedThreshold) {
      priorityQueue.remove(messagesReceived)
      priorityQueue.add(messagesReceived)
    }
  }
  
  def getFirstInQueue: MessagesReceived = priorityQueue.peek

  def getMessagesCount(uuid: String): HashMap[TheaterNode, MessagesReceived] = incomingMessagesRecord.get(uuid)

}
