package se.scalablesolutions.akka.mobile.actor

import se.scalablesolutions.akka.mobile.theater.Theater

import se.scalablesolutions.akka.actor.Actor

import java.net.InetSocketAddress

trait MobileActor extends Actor {
  self.id = self.uuid

  self.homeAddress = new InetSocketAddress(Theater.hostname, Theater.port)

  def beforeMigration(): Unit

  def afterMigration(): Unit
}
