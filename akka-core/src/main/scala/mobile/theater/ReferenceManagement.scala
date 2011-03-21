package se.scalablesolutions.akka.mobile.theater

import se.scalablesolutions.akka.mobile.actor.MobileActorRef
import se.scalablesolutions.akka.mobile.actor.RemoteMobileActor
import se.scalablesolutions.akka.mobile.actor.AttachRefToActor
import se.scalablesolutions.akka.mobile.util.ClusterConfiguration

import se.scalablesolutions.akka.actor.Actor
import se.scalablesolutions.akka.actor.ActorRef
import se.scalablesolutions.akka.remote.RemoteClientLifeCycleEvent
import se.scalablesolutions.akka.remote.RemoteClient

import java.util.concurrent.ConcurrentHashMap

object ReferenceManagement {
  
  val references = new ConcurrentHashMap[String, MobileActorRef]
  val detachedReferences = new ConcurrentHashMap[String, MobileActorRef]
  val remoteClients = new ConcurrentHashMap[TheaterNode, ActorRef](ClusterConfiguration.numberOfNodes)

  private[mobile] def put(uuid: String, reference: MobileActorRef, detached: Boolean = false): Unit = {
    if (!detached) 
      references.put(uuid, reference)
    else
      detachedReferences.put(uuid, reference)
  }

  def get(uuid: String, detached: Boolean = false): Option[MobileActorRef] = {
    val map = 
      if (detached) detachedReferences
      else references

    map.get(uuid) match {
      case null => None

      case reference => Some(reference)
    }
  }

  private[mobile] def remove(uuid: String, detached: Boolean = false): Unit = {
    if (!detached)
      references.remove(uuid)
    else
      detachedReferences.remove(uuid)
  }

  private[mobile] def attachRefToActor(temporaryId: String, actorUuid: String): Unit = {
    get(temporaryId, true).foreach { ref => 
      ref ! AttachRefToActor(actorUuid)
      remove(temporaryId, true)
      put(actorUuid, ref)
    }
  }

  private[mobile] def registerForRemoteClientEvents(reference: RemoteMobileActor, client: RemoteClient): Unit = {
    var listener = remoteClients.get(TheaterNode(client.hostname, client.port))
  
    if (listener == null) {
      listener = Actor.actorOf[RemoteClientEventsListener].start
      remoteClients.put(TheaterNode(client.hostname, client.port), listener)
      client.addListener(listener)
    }

    listener ! AddReference(reference)
  }

  private[mobile] def unregisterForRemoteClientEvents(reference: RemoteMobileActor, client: RemoteClient): Unit = {
    val listener = remoteClients.get(TheaterNode(client.hostname, client.port))
    
    if (listener != null) {
      listener ! RemoveReference(reference)
    }
  }
  
  /*
   * Listener for the remote client events
   */
  private case class AddReference(ref: RemoteMobileActor)
  private case class RemoveReference(ref: RemoteMobileActor)
  private class RemoteClientEventsListener extends Actor {
    private var references: List[RemoteMobileActor] = Nil
    
    def receive = {
      case event: RemoteClientLifeCycleEvent =>
	references.foreach(ref => ref.handleRemoteClientEvent(event))
      
      case AddReference(ref) =>
	references = ref :: references
      
      case RemoveReference(ref) =>
	references = references.filter(_ != ref)
    }
  }
}


