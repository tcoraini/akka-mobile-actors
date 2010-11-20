package se.scalablesolutions.akka.mobile

import scala.collection.mutable.HashMap

class NamingService {
  
  val actors = new HashMap[String, TheaterNode]

  def register(actor: MobileActorRef, node: TheaterNode): Unit = {
    register(actor.uuid, node)
  }

  def register(uuid: String, node: TheaterNode): Unit = {
    actors.put(uuid, node)
  }

  def update(actor: MobileActorRef, node: TheaterNode): Option[TheaterNode] = {
    update(actor.uuid, node)
  }

  def update(uuid: String, node: TheaterNode): Option[TheaterNode] = {
    val oldValue = actors.get(uuid)
    actors.update(uuid, node)
    oldValue
  }

  def get(actor: MobileActorRef): Option[TheaterNode] = {
    get(actor.uuid)
  }

  def get(uuid: String): Option[TheaterNode] = {
    actors.get(uuid)
  }

  def unregister(actor: MobileActorRef): Option[TheaterNode] = {
    unregister(actor.uuid)
  }

  def unregister(uuid: String): Option[TheaterNode] = {
    actors.remove(uuid)
  }
}
