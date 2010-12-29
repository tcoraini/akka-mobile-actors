package se.scalablesolutions.akka.mobile.actor

import se.scalablesolutions.akka.util.Logging

import se.scalablesolutions.akka.actor.ActorRef
import se.scalablesolutions.akka.actor.ScalaActorRef

trait MobileReference extends ActorRef with ScalaActorRef with Logging {
  private var _externalReference: MobileActorRef = _

  protected[mobile] def externalReference: MobileActorRef = _externalReference

  protected[mobile] def externalReference_=(ref: MobileActorRef) = { _externalReference = ref }
  
  def isLocal: Boolean

}
