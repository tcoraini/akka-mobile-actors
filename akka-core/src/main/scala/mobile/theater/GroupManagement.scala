package se.scalablesolutions.akka.mobile.theater

import se.scalablesolutions.akka.mobile.actor.MobileActorRef

import se.scalablesolutions.akka.util.UUID
import java.util.concurrent.ConcurrentHashMap

object GroupManagement {

  /* TODO private */ val groups = new ConcurrentHashMap[String, List[MobileActorRef]]
  
  def newGroupId = UUID.newUuid.toString

  def insert(ref: MobileActorRef, groupId: String): Unit = groups.get(groupId) match {
    case null => groups.put(groupId, List(ref))
    
    case list => groups.put(groupId, ref :: list)
  }
  
  def remove(ref: MobileActorRef, groupId: String): Unit = {
    val group = groups.get(groupId)
    if (group != null) {
      val newGroup = group.filter(_ != ref)
      if (newGroup.size > 0) {
	groups.put(groupId, newGroup)
      }
    }
  }
}
