package se.scalablesolutions.akka.mobile.examples.s4

import se.scalablesolutions.akka.mobile.actor.MobileActor

case class StartPEFor[T <: Event[_, _]](eventPrototype: T)

@serializable abstract class ProcessingElement[T <: Event[_, _] : Manifest] extends MobileActor {
  

  private var _isRunning = false
  def isRunning = _isRunning
  
  protected var eventPrototype: T = _

  val eventClass = manifest[T].erasure.asInstanceOf[Class[_ <: Event[_, _]]]

  lazy val key = eventPrototype.key
  
  protected def start(prototype: T) = {
    eventPrototype = prototype
    self.uuid = eventPrototype.uniqueName
    _isRunning = true
  }

  def receive = {
    if (!_isRunning) {
      case StartPEFor(prototype) if (prototype.getClass == eventClass) => println("EEEEEE") // start(prototype.asInstanceOf[T])

      case StartPEFor(prototype) => println(prototype.getClass() + " (" + prototype.getClass().getName() + ") == " +
					     eventClass + " (" + eventClass.getName() + ") ? " + 
					     (prototype.getClass() == eventClass) + " e " +
					     (prototype.getClass().getName() == eventClass.getName()))
      
    } else {
      case event: T if event.key == key => process(event)
      
      case event: Event[_, _] => log.warning("Processing element received an Event with an incompatible key: " + event)
      
      case msg => log.warning("Processing element received an unknown message: " + msg)
    }
  }

  override def shutdown {
    _isRunning = false
  }
  
  protected def emit(event: Event[_, _]): Unit = {
    S4.dispatch(event)
  }
  
  def process(event: T): Unit
  
}
