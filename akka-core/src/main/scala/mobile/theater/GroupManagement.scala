package se.scalablesolutions.akka.mobile.theater

import se.scalablesolutions.akka.mobile.actor.MobileActorRef
import se.scalablesolutions.akka.mobile.util.messages._
import se.scalablesolutions.akka.util.UUID
import collection.mutable.SynchronizedMap
import collection.mutable.HashMap
import collection.mutable.ArrayBuilder
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.ConcurrentHashMap

object GroupManagement {

  val TIMEOUT: Long = 5000
  private val timer = new Timer("Group Management Timer")

  /* TODO private */ val groups = new ConcurrentHashMap[String, List[MobileActorRef]]
  val migrationTasks = new HashMap[String, GroupMigrationTask] with SynchronizedMap[String, GroupMigrationTask]
  
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
      } else {
	groups.remove(groupId)
      }
    }
  }
  
  private[mobile] def startGroupMigration(groupId: String, destination: TheaterNode): Unit = this.synchronized {
    val group = groups.get(groupId)
    val task = migrationTasks.get(groupId)
    if (group != null && !task.isDefined) {
      val task = new GroupMigrationTask(groupId, destination)
      migrationTasks.put(groupId, task)
      group.foreach(actor => actor ! PrepareToMigrate)
      timer.schedule(task, TIMEOUT)
    }
  }

  private[mobile] def readyToMigrate(actor: MobileActorRef): Unit = {
    val task = migrationTasks.get(actor.groupId.get)
    if (task.isDefined) {
      task.get.addActorBytes(actor.startMigration())
    }
  }
  
  private[mobile] def migrationPerformed(groupId: String): Unit = {
    val group = groups.get(groupId)
    if (group != null) {
      // If the actor didn't respond, it gets behind and is removed from group
      group.foreach(actor => actor.groupId = None)
    }
    groups.remove(groupId)
  }
}

class GroupMigrationTask(groupId: String, destination: TheaterNode) extends TimerTask {
  var done = false
  var builder: ArrayBuilder[Array[Byte]] = null
  private val _lock = new Object
  
  override def run(): Unit = {
    _lock.synchronized { done = true }
    LocalTheater.migrateGroup(builder.result, destination)
    GroupManagement.migrationPerformed(groupId)
  }

  def addActorBytes(bytes: => Array[Byte]): Unit = {
    if (builder == null) {
      builder = ArrayBuilder.make[Array[Byte]]
    }
    _lock.synchronized { if (!done) builder += bytes }
  }
}
