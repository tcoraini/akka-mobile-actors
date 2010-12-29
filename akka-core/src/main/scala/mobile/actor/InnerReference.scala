package se.scalablesolutions.akka.mobile.actor

import se.scalablesolutions.akka.util.Logging

import se.scalablesolutions.akka.actor.ActorRef
import se.scalablesolutions.akka.actor.ScalaActorRef

import se.scalablesolutions.akka.mobile.theater.LocalTheater

trait InnerReference extends ActorRef with ScalaActorRef with Logging {
  private var _outerRef: MobileActorRef = _

  protected[mobile] def outerRef: MobileActorRef = _outerRef

  protected[mobile] def outerRef_=(ref: MobileActorRef) = { _outerRef = ref }
  
  def isMigrating = 
    if (_outerRef != null) _outerRef.isMigrating 
    else false

  def homeTheater = 
    if (_outerRef != null) _outerRef.homeTheater
    else LocalTheater

  def isLocal: Boolean

}
