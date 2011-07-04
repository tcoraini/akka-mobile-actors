package se.scalablesolutions.akka.mobile.theater

import se.scalablesolutions.akka.mobile.actor.MobileActorRef
import se.scalablesolutions.akka.mobile.util.messages._
import se.scalablesolutions.akka.config.Config
import se.scalablesolutions.akka.mobile.util.UUID

import collection.mutable.SynchronizedMap
import collection.mutable.HashMap
import collection.mutable.ArrayBuilder
import java.util.Timer
import java.util.TimerTask

object GroupManagement {

  val MIGRATION_TIMEOUT = 5000L
  
  val migrationTimeout = Config.config.getLong("cluster.colocated-actors.migration-timeout", MIGRATION_TIMEOUT)

  private val timer = new Timer("Group Management Timer")

  private val groups = new HashMap[String, List[MobileActorRef]] with SynchronizedMap[String, List[MobileActorRef]]
  private val migrationTasks = new HashMap[String, GroupMigrationTask] with SynchronizedMap[String, GroupMigrationTask]
  
  def newGroupId = UUID.newUuid.toString

  private[mobile] def insert(ref: MobileActorRef, groupId: String): Unit = this.synchronized {
    val list = groups.getOrElseUpdate(groupId, Nil)
    groups.put(groupId, ref :: list)
  }
  
  private[mobile] def remove(ref: MobileActorRef, groupId: String): Unit = this.synchronized {
    groups.get(groupId).foreach { group =>
      val newGroup = group.filter(_ != ref)
      if (newGroup.size > 0) {
	groups.put(groupId, newGroup)
      } else {
	groups.remove(groupId)
      }
    }
  }

  def group(groupId: String): Option[List[MobileActorRef]] = groups.get(groupId)
  
  private[mobile] def startGroupMigration(groupId: String, destination: TheaterNode, nextTo: Option[String]): Unit = this.synchronized {
    val group = groups.get(groupId)
    val task = migrationTasks.get(groupId)
    (group, task) match {
      case (Some(group), None) =>
	val task = new GroupMigrationTask(groupId, group.size, destination, nextTo)
	migrationTasks.put(groupId, task)
	group.foreach(actor => actor ! PrepareToMigrate)
	timer.schedule(task, migrationTimeout)

      case _ => ()
    }
  }

  private[mobile] def readyToMigrate(actor: MobileActorRef): Unit = {
    migrationTasks.get(actor.groupId.get).foreach {
      _.addActorBytes(actor.startMigration())
    }
  }
  
  private[mobile] def migrationPerformed(groupId: String): Unit = {
    val group = groups.get(groupId)
    groups.remove(groupId)
    group.foreach {
      // If the actor didn't respond, it gets behind and is removed from group
      _.foreach(actor => actor.groupId = None)
    }
  }
}

class GroupMigrationTask(groupId: String, groupSize: Int, destination: TheaterNode, nextTo: Option[String]) extends TimerTask {
  var done = false
  private var actorsReady = 0

  var builder: ArrayBuilder[Array[Byte]] = null
  private val _lock = new Object
  
  override def run(): Unit = {
    _lock.synchronized { done = true }
    LocalTheater.migrateGroup(builder.result, destination, nextTo)
    GroupManagement.migrationPerformed(groupId)
  }

  def addActorBytes(bytes: => Array[Byte]): Unit = {
    if (builder == null) {
      builder = ArrayBuilder.make[Array[Byte]]
    }
    _lock.synchronized { if (!done) builder += bytes }
    
    actorsReady = actorsReady + 1
    // If all actors are ready, execute the task immediately. Note that 'cancel()' will
    // return true if the task has not yet run.
    if (actorsReady == groupSize && cancel()) run()
  }
}
