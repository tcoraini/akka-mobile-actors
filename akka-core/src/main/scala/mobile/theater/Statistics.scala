package se.scalablesolutions.akka.mobile.theater

import se.scalablesolutions.akka.mobile.util.messages._

import se.scalablesolutions.akka.util.Logging
import se.scalablesolutions.akka.config.Config

import scala.collection.mutable.HashMap

import java.util.concurrent.ConcurrentHashMap
import java.util.concorrent.PriorityBlockingQueue

object Statistics {
  // Default value for the minimum amount of messages that an actor should receive from a node before
  // entering in the priority queue. Can be set in the configuration file.
  val MESSAGES_RECEIVED_THRESHOLD: Int = 5

}

class Statistics(val localNode: TheaterNode, automaticReset: Boolean = false) extends Logging {

  // TODO privates
  /*private*/ val incomingMessages = new ConcurrentHashMap[String, HashMap[TheaterNode, IMRecord]]
  
  private val messagesReceivedThreshold = 
    Config.config.getInt("cluster.statistics.messages-received-threshold", Statistics.MESSAGES_RECEIVED_THRESHOLD)

  // 11 is the default initial capacity for the PriorityQueue Java class
  /*private*/ val priorityQueue = new PriorityBlockingQueue[IMRecord](11, IMRecord.comparator)

  if (automaticReset) {
    initializeAutomaticResetService()
  }

  private[mobile] def localMessageArrived(uuid: String): Unit = {
    messageArrived(uuid, localNode, false)
  }

  private[mobile] def remoteMessageArrived(uuid: String, message: MobileActorMessage): Unit = {
    messageArrived(uuid, TheaterNode(message.senderHostname, message.senderPort), true)
  }

  private def messageArrived(uuid: String, from: TheaterNode, usePriorityQueue: Boolean): Unit = {  
    val newMap = new HashMap[TheaterNode, IMRecord])
    var innerMap: HashMap[TheaterNode, IMRecord] = incomingMessages.putIfAbsent(uuid, newMap)
    if (innerMap == null)
      innerMap = newMap
    
    synchronized(innerMap) {
      val imRecord = innerMap.get(from) match {
	case Some(record) => record
	
	case None => {
          val record = IMRecord(uuid, from)
          innerMap.put(from, record)
          record
	}
      }
      record.increment
    }
    
    if (usePriorityQueue) {
      updatePriorityQueue(record)
    }
    
    if (from == localNode) {
      log.debug("Registering arrival of local message to actor with UUID [%s].", uuid)
    } else {
      log.debug("Registering arrival of remote message to actor with UUID [%s] from node [%s:%d].",
		uuid, from.hostname, from.port)
    }
  }

  private def updatePriorityQueue(imRecord: IMRecord): Unit = {
    if (imRecord.count == messagesReceivedThreshold) {
      priorityQueue.add(imRecord)
    } else if (imRecord.count > messagesReceivedThreshold) {
      priorityQueue.remove(imRecord)
      priorityQueue.add(imRecord)
    }
  }

  // Removes all the records regarding the actor with UUID 'uuid', from both the Hash Map and 
  // the Priority Queue

  // TODO remover ao migrar ator
  private[mobile] def remove(uuid: String): Unit = {
    val innerMap: HashMap[TheaterNode, IMRecord] = incomingMessages.get(uuid)
    if (innerMap != null) {
      innerMap.values.foreach(record => priorityQueue.remove(record))
      incomingMessages.remove(uuid)
    }
  }

  private[mobile] def reset: Unit = {
    incomingMessages.clear()
    priorityQueue.clear()
  }

  private initializeAutomaticResetService(): Unit = {
    val resetInterval = 60 // In Minutes
    new Thread {
      override def run() {
	while(true) {
	  Thread.sleep(resetInterval * 60 * 1000)
	  log.debug("Resetting all statistics now...")
	  this.reset
	}
      }
    } start()
  }
  
  def firstInQueue: Option[IMRecord] = {
    val first = priorityQueue.peek
    if (first != null) Some(first)
    else None
  }

  def localMessagesCount(uuid: String): Int = incomingMessages.get(uuid) match {
    case null => 0
    
    case innerMap => 
      val record = innerMap.get(localNode)
      if (record.isDefined) 
	record.get.count
      else 0
  }

  def incomingMessagesRecords(uuid: String): HashMap[TheaterNode, IMRecord] = incomingMessages.get(uuid)

}
