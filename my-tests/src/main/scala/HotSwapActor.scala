package tests

import se.scalablesolutions.akka.actor.Actor
import se.scalablesolutions.akka.actor.Actor._

import java.util.concurrent.ConcurrentLinkedQueue

@serializable class HotSwapActor extends Actor {
   val retainedMessagesQueue = new ConcurrentLinkedQueue[Any]

   val retainMessages: Receive = {
      case msg => retainedMessagesQueue.add(msg)
   }

   def show(str: String): Unit = println("[" + this + "] " + str)

   def receive = {
      case Ack =>
         show("Received 'Ack'")
      case Retain =>
         become(Some(retainMessages))
      case Proceed =>
         become(None)
      case any =>
         //show("Mensagem desconhecida. Recolocando na caixa de mensagens: " + any)
         self ! any
   }
}
