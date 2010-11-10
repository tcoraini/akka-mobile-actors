package se.scalablesolutions.akka.mobile

import se.scalablesolutions.akka.actor.Actor

trait MobileActor extends Actor {
  self.id = self.uuid

  def beforeMigration(): Unit

  def afterMigration(): Unit
}
