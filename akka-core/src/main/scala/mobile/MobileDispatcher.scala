package se.scalablesolutions.akka.mobile

import se.scalablesolutions.akka.dispatch.ExecutorBasedEventDrivenDispatcher

object MobileDispatcher {
  
  object globalMobileExecutorBasedEventDrivenDispatcher extends MobileExecutorBasedEventDrivenDispatcher("global")

}
