package tests

import se.scalablesolutions.akka.actor.Actor
import se.scalablesolutions.akka.actor.Actor._

@serializable class HotSwapActor extends Actor {
   def show(str: String): Unit = println("[" + this + "] " + str)

   def receive = {
      case Ack =>
         show("Received 'Ack'")
      case any =>
         //show("Mensagem desconhecida. Recolocando na caixa de mensagens: " + any)
         self ! any
   }
}
