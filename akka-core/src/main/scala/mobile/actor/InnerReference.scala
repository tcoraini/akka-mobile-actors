package se.scalablesolutions.akka.mobile.actor

import se.scalablesolutions.akka.util.Logging

import se.scalablesolutions.akka.actor.ActorRef
import se.scalablesolutions.akka.actor.ScalaActorRef

import se.scalablesolutions.akka.mobile.theater.LocalTheater
import se.scalablesolutions.akka.mobile.theater.TheaterNode

trait InnerReference extends ActorRef with ScalaActorRef with Logging {
  private var _outerRef: MobileActorRef = null

  protected[mobile] def outerRef: MobileActorRef = _outerRef

  protected[mobile] def outerRef_=(ref: MobileActorRef) = { _outerRef = ref }
  
  def isMigrating = 
    if (_outerRef != null) _outerRef.isMigrating 
    else false
  
  /**
   * Abstract methods, to be overriden by subclasses
   */
  protected[actor] def isLocal: Boolean

  protected[actor] def node: TheaterNode

  /**
   * Should be overriden only by local references.
   *
   * The group ID field only makes sense for local actors, it would be too hard to keep
   * track of group IDs for remote actors
   */
  def groupId: Option[String] = None
  protected[mobile] def groupId_=(id: Option[String]) { }

}
