package se.scalablesolutions.akka.mobile.theater

import se.scalablesolutions.akka.mobile.util.messages._

import se.scalablesolutions.akka.util.Logging

import scala.collection.mutable.HashMap
import scala.collection.mutable.PriorityQueue

case class MessagesReceived(uuid: String, from: TheaterNode) {
  var count: Int = 0

  def increment = { count = count + 1 }

  override def toString = "MessagesReceived(To [" + uuid + "] from " + from + " -> " + count + ")"

  override def equals(that: Any): Boolean = that match {
    case mr: MessagesReceived => 
      mr.uuid == this.uuid && mr.from == this.from

    case _ => false
  }
}


class Statistics extends Logging {

  private val messagesCounter = new HashMap[String, HashMap[TheaterNode, MessagesReceived]]

  private val priorityQueue = new PriorityQueue[MessagesReceived]

  private var minimumInQueue: MessagesReceived = null

  def messageArrived(uuid: String, message: MobileActorMessage): Unit = {  
    val innerMap = messagesCounter.get(uuid) match {
      case Some(map) => map

      case None => {
        val map = new HashMap[TheaterNode, MessagesReceived]
        messagesCounter.put(uuid, map)
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

  def updatePriorityQueue(messagesReceived: MessagesReceived): Unit = {
    if (messagesReceived.count > minimumCount) {
      biggestCounts.enqueue(messagesReceived)
      minimumCount = messagesReceived.

  def getMessagesCount(uuid: String): Option[HashMap[TheaterNode, MessagesReceived]] = 
    messagesCounter.get(uuid)
}
