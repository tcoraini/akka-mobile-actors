package tests

import se.scalablesolutions.akka.actor.Actor
import se.scalablesolutions.akka.actor.Actor._

import se.scalablesolutions.akka.actor.ActorSerialization._
import se.scalablesolutions.akka.actor.Format
import se.scalablesolutions.akka.actor.StatelessActorFormat

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

class MyActor extends Actor {
   var count = 0

      def receive = {
         case "hello" =>
            count = count + 1
            self.reply("world " + count)
      }
}

/*object BinaryFormatMyActor {
   implicit object MyActorFormat extends Format[MyActor] {
      def fromBinary(bytes: Array[Byte], act: MyActor) = {
         val p = Serializer.Protobuf.fromBinary(bytes, Some(classOf[ProtobufProtocol.Counter])).asInstanceOf[ProtobufProtocol.Counter]
            act.count = p.getCount
            act
      }
      def toBinary(ac: MyActor) =
         ProtobufProtocol.Counter.newBuilder.setCount(ac.count).build.toByteArray
   }
}*/

case class Wait(seconds: Int)
case object Ack

class MyStatelessActor extends Actor {
   self.makeRemote("localhost", 9999)

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

object GeneralTests {
   import BinaryFormatMyStatelessActor._

   def main(args: Array[String]) {

   }

   def execute() {
      val actor1 = actorOf[MyStatelessActor].start

      (actor1 ! Ack)
      //(actor1 ! Wait(5))
      //(actor1 ! Ack)
      //(actor1 ! Ack)

      //val reply1: String = (actor1 !! Ack).getOrElse("_").asInstanceOf[String]
      //println("First reply: " + reply1)

      // Fazendo a seriacao
      println("Start of serialization")
      val bytes = toBinary(actor1)
      val actor2 = fromBinary(bytes)
      println("End of serialization")

      actor2 ! Ack

      // Novo ator, deve ser igual ao anterior
      //actor2.start
      //println("Actor 2 (copy deserialized) started")
      //val reply2: String = (actor2 !! Wait(2)).getOrElse("_").asInstanceOf[String]
      //println("Second reply (after serialization): " + reply2)
   }

      
}
