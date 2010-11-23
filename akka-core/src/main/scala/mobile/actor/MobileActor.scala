package se.scalablesolutions.akka.mobile.actor

import se.scalablesolutions.akka.mobile.theater.Theater

import se.scalablesolutions.akka.actor.Actor

import java.net.InetSocketAddress

trait MobileActor extends Actor {
  self.id = self.uuid

  def beforeMigration(): Unit

  def afterMigration(): Unit
}
