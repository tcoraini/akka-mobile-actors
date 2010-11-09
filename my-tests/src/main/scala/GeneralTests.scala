package tests

import se.scalablesolutions.akka.actor.Actor
import se.scalablesolutions.akka.actor.Actor._
import se.scalablesolutions.akka.actor.ActorRef
import se.scalablesolutions.akka.actor.HotSwap
import se.scalablesolutions.akka.actor.ActorSerialization._
import se.scalablesolutions.akka.actor.Format
import se.scalablesolutions.akka.actor.StatelessActorFormat
import se.scalablesolutions.akka.actor.SerializerBasedActorFormat

import se.scalablesolutions.akka.remote.RemoteClient

import se.scalablesolutions.akka.mobile.MobileLocalActorRef
import se.scalablesolutions.akka.mobile.MobileActor
import se.scalablesolutions.akka.mobile.Mobile._
import se.scalablesolutions.akka.mobile.Migrate
import se.scalablesolutions.akka.mobile.MovingActor

import se.scalablesolutions.akka.dispatch.FutureTimeoutException

import se.scalablesolutions.akka.serialization.Serializer

import se.scalablesolutions.akka.config.Config.config

object BinaryFormatMyStatelessActor {
   implicit object MyStatelessActorFormat extends StatelessActorFormat[MyStatelessActor]
}

object BinaryFormatMyJavaSerializableActor {
  implicit object SpecificActorFormat extends SerializerBasedActorFormat[StatefulActor] {
    val serializer = Serializer.Java
  }

  object DefaultActorFormat extends SerializerBasedActorFormat[Actor] {
    val serializer = Serializer.Java
  }

}

case class Wait(seconds: Int)
case object Ack
case object Ping
case object Pong
case class Garbage(what: String)
case class Message(what: String)
case object Identify

case class MsgFunction(func: Function0[String])

case object ShowCount

class MyStatelessActor extends MobileActor {
  //self.makeRemote("localhost", 9999)
  
  private def show(str: String): Unit = println("[" + this + "] " + str)

  def receive = {
    case Wait(x) => 
      Thread.sleep(x * 1000)
      show("Just slept for " + x + " seconds.")

    case Ack =>
      show("Received 'Ack'.")
    
    case Ping =>
      show("Received 'Ping'...Replying 'Pong'")
      self.reply(Pong)

    case Message(msg) =>
      show("Received 'Message': " + msg)

    case Identify =>
      val senderId = 
        if (self.sender.isEmpty) "NAO IDENTIFICADO"
        else self.sender.get.id
      show("Sender ID: " + senderId)

    case MsgFunction(func) =>
      show("Received function. Applying it: " + func())

    case msg =>
      show("Received unknown message: " + msg)
   }
}

class SenderActor(destination: ActorRef) extends Actor {
  self.id = self.uuid

  def receive = {
    case Ack =>
      destination ! Identify
  }
}

@serializable class StatefulActor extends MobileActor {
  private var count = 0
  
  private def show(str: String): Unit = println("[" + this + "] " + str)

  def this(init: Int) = {
    this()
    count = init
  }

  def receive = {
    case Ping =>
      count = count + 1

    case ShowCount =>
      show("Current count: " + count)    
  }
}

object GeneralTests {

   def main(args: Array[String]) {

   }

   def execute() {
      import BinaryFormatMyStatelessActor._
      
      val actor1 = actorOf(new MyStatelessActor)
      //val actor1 = actorOf[MyStatelessActor]
      actor1.start
      
      val sender = actorOf(new SenderActor(actor1)).start
      println("ID of the SenderActor created: " + sender.id)

      actor1 ! Message("Inicializando")
      actor1 ! Wait(2)
      sender ! Ack
      actor1 ! Message("Após espera")
      //val future = (actor1 !!! Ping)

      println("[1] Tamanho do mailbox do ator 1: " + actor1.mailboxSize)

      // Fazendo a seriacao
      println("* * * Start of serialization * * *")
      actor1 ! Migrate
      //val bytes = actor1.serializedActor
      actor1 ! Message("Após seriação")
      
      //val actor2 = fromBinary(bytes)
      //val actor2 = mobileFromBinary(bytes)
      //actor1.forwardRetainedMessages(actor2)
      println("* * * End of serialization * * *")
      
      
      println("[2] Tamanho do mailbox do ator 1: " + actor1.mailboxSize)
      //println("[2] Tamanho do mailbox do ator 2: " + actor2.mailboxSize)
      //println("% % % Retained messages: " + actor1.retainedMessagesQueue)
      //println("% % % Retained messages with future: " + actor1.retainedMessagesWithFutureQueue)

      /*println("Aguardando pela resolução do Futuro [" + future + "]...")
      try {
        future.await
      } catch {
        case e: FutureTimeoutException =>
          println("Exceção de TIMEOUT lançada!")
      }
      if (future.exception.isDefined) {
        println("Future preenchido com exceção! Lançando:")
        throw future.exception.get
      }
      else
        println("Future preenchido com resultado: " + future.result)*/

      actor1.stop
      //actor2.stop
   }

  def testStatefulActor(): Unit = {
    val counter = actorOf[StatefulActor].start
    val format = BinaryFormatMyJavaSerializableActor.DefaultActorFormat
    
    counter ! ShowCount // Show 0

    counter ! Ping
    counter ! Ping
    counter ! Ping

    counter ! ShowCount // Show 3

    // Seriando o ator com o formatador (type class) default
    val bytes = toBinary(counter)(format)
    // Enviando para um teatro remoto
    val theaterAgent = RemoteClient.actorFor("theater@localhost:1810", "localhost", 1810)
    //theaterAgent ! MovingActor(bytes)
    Thread.sleep(3000) 
    // Obtendo uma referencia remota (RemoteActorRef) para o ator migrado, agora no teatro remoto
    val newCounter = RemoteClient.actorFor(counter.id, "localhost", 1810)

    newCounter ! ShowCount // Show 3

    newCounter ! Ping
    newCounter ! Ping

    newCounter ! ShowCount // Show 5

    counter.stop
    newCounter.stop
  }   
}
