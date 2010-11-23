package se.scalablesolutions.akka.mobile.nameservice

import se.scalablesolutions.akka.mobile.theater.TheaterNode
import se.scalablesolutions.akka.mobile.actor.MobileActorRef

import se.scalablesolutions.akka.util.Logging
import se.scalablesolutions.akka.config.Config

object NameService extends Logging {
  private var isRunning = false
  
  private var service: NameService = _

  def init() = {
    this.service = chooseNameService
    service.init()
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

  // Choose the name service to be used, based on the configuration file
  private def chooseNameService: NameService = {
    lazy val defaultNameService = new DistributedNameService

    // The user can specify his own name service through the configuration file
    Config.config.getString("cluster.name-service.name-service-class") match {
      case Some(classname) =>
        try {
          val instance = Class.forName(classname).newInstance.asInstanceOf[NameService]
          log.info("Using '%s' as the name service.", classname)
          instance
        } catch {
          case cnfe: ClassNotFoundException =>
            log.warning("The class '%s' could not be found. Using the default name service (%s) instead.", 
                classname, defaultNameService)
            defaultNameService
          
          case cce: ClassCastException =>
            log.warning("The class '%s' does not extend the NameService trait. Using the default name service (%s) instead.", 
                classname, defaultNameService)
            defaultNameService
        }

      case None =>
        defaultNameService
    }
  }
}

trait NameService {
  def init(): Unit

  def put(uuid: String, node: TheaterNode): Unit

  def get(uuid: String): Option[TheaterNode] 

  def remove(uuid: String): Unit
}
