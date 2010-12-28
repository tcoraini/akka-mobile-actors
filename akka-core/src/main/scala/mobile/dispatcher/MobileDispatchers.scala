package se.scalablesolutions.akka.mobile.dispatcher

import se.scalablesolutions.akka.dispatch.ExecutorBasedEventDrivenDispatcher

object MobileDispatchers {
  
  object globalMobileExecutorBasedEventDrivenDispatcher extends MobileExecutorBasedEventDrivenDispatcher("global")

}
