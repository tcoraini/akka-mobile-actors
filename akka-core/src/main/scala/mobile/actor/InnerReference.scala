package se.scalablesolutions.akka.mobile.actor

import se.scalablesolutions.akka.util.Logging

import se.scalablesolutions.akka.actor.ActorRef
import se.scalablesolutions.akka.actor.ScalaActorRef

trait InnerReference extends ActorRef with ScalaActorRef with Logging {
  private var _outerRef: MobileActorRef = _

  protected[mobile] def outerRef: MobileActorRef = _outerRef

  protected[mobile] def outerRef_=(ref: MobileActorRef) = { _outerRef = ref }
  
  def isMigrating = _outerRef.isMigrating

  def homeTheater = _outerRef.homeTheater

  def isLocal: Boolean

}
