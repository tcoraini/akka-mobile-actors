package tests

import se.scalablesolutions.akka.actor.Actor
import se.scalablesolutions.akka.actor.Actor._
import se.scalablesolutions.akka.actor.HotSwap

import se.scalablesolutions.akka.actor.ActorSerialization._
import se.scalablesolutions.akka.actor.Format
import se.scalablesolutions.akka.actor.StatelessActorFormat
import se.scalablesolutions.akka.actor.SerializerBasedActorFormat

import se.scalablesolutions.akka.serialization.Serializer

import se.scalablesolutions.akka.config.Config.config

case object Exit
case class Sum(a: Int, b: Int)
case class Result(x: Int)

class ExampleActor extends Actor {
   def receive = {
      case Sum(a, b) => self.reply(Result(a + b))
      case Exit => self.exit()
      case str: String => println("Recebi uma cadeia de caracteres: " + str)
      case _ => println("Recebi algo desconhecido, descartando...")
   }
}

case class Wait(seconds: Int)
case object Ack
case class Garbage(what: String)

class MyStatelessActor extends Actor {
   //self.makeRemote("localhost", 9999)

   def receive = {
      case Wait(x) => 
         Thread.sleep(x * 1000)
         println("[" + this + "] Just slept for " + x + " seconds.")
         //self.reply("[" + this + "] Just slept for " + x + " seconds.")        
      case Ack =>
         println("[" + this + "] Received 'Ack'.")
         //self.reply("[" + this + "] Received 'Ack'.")
   }
}

object BinaryFormatMyStatelessActor {
   implicit object MyStatelessActorFormat extends StatelessActorFormat[MyStatelessActor]
}

@serializable class MyJavaSerializableActor extends Actor {
  var count = 0
 
  def receive = {
    case "hello" =>
      count = count + 1
      Thread.sleep(1000)
      println("[" + this + "] Received 'hello'. Current mailbox size: " + self.mailboxSize)
  }
}

object BinaryFormatMyJavaSerializableActor {
  implicit object MyJavaSerializableActorFormat extends SerializerBasedActorFormat[HotSwapActor] {
    val serializer = Serializer.Java
  }
}

object GeneralTests {
   import BinaryFormatMyStatelessActor._
   //import BinaryFormatMyJavaSerializableActor._

   def main(args: Array[String]) {

   }

   def execute() {
      val actor1 = actorOf[MyStatelessActor].start
      //val actor1 = actorOf[HotSwapActor].start

      actor1 ! Ack
      //actor1 ! Garbage("Lixo 1")
      //actor1 ! Garbage("Lixo 2")
      //actor1 ! Garbage("Lixo 3")

      (actor1 ! Wait(5))
      actor1 ! Ack
      actor1 ! Ack
      actor1 ! Ack
      actor1 ! Ack

      println("Tamanho do mailbox do ator 1: " + actor1.mailboxSize)
      //val reply1: String = (actor1 !! Ack).getOrElse("_").asInstanceOf[String]
      //println("First reply: " + reply1)
      //Thread.sleep(2000)
      // Fazendo a seriacao
      println("Start of serialization")
      val bytes = toBinary(actor1)
      val actor2 = fromBinary(bytes)
      println("End of serialization")

      println("Tamanho do mailbox do ator 2: " + actor2.mailboxSize)

      //actor2 ! Ack
      //actor2 ! HotSwap(Some({case any => println("## Mensagem recebida (ator 2): " + any)}))
      //actor1 ! HotSwap(Some({case any => println("## Mensagem recebida (ator 1): " + any)}))

      // Novo ator, deve ser igual ao anterior
      //actor2.start
      //println("Actor 2 (copy deserialized) started")
      //val reply2: String = (actor2 !! Wait(2)).getOrElse("_").asInstanceOf[String]
      //println("Second reply (after serialization): " + reply2)
   }

      
}
