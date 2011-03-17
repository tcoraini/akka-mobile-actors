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

  protected[actor] def isLocal: Boolean

  protected[actor] def node: TheaterNode

}
