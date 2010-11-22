package se.scalablesolutions.akka.mobile.nameservice

import se.scalablesolutions.akka.mobile.theater.TheaterNode
import se.scalablesolutions.akka.mobile.actor.MobileActorRef

object NameService {
  private var isRunning = false
  
  private var service: NameService = _

  def init(service: NameService) = {
    this.service = service
    isRunning = true
  }

  def put(actor: MobileActorRef, node: TheaterNode): Unit = {
    put(actor.uuid, node)
  }

  def put(uuid: String, node: TheaterNode): Unit = ifRunning {
      service.put(uuid, node)
  }
    
  def get(actor: MobileActorRef): Option[TheaterNode] = {
    get(actor.uuid)
  }

  def get(uuid: String): Option[TheaterNode] = ifRunning {
    service.get(uuid)
  }

  def remove(actor: MobileActorRef): Unit = {
    remove(actor.uuid)
  }
  
  def remove(uuid: String): Unit = ifRunning {
    service.remove(uuid)
  }

  private def ifRunning[T](execute: => T): T = {
    if (isRunning)
      execute
    else 
      throw new RuntimeException("The name service is not running. You have to call NameService.init() first.")
  }
}

trait NameService {
  def init(): Unit

  def put(uuid: String, node: TheaterNode): Unit

  def get(uuid: String): Option[TheaterNode] 

  def remove(uuid: String): Unit
}
