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

import se.scalablesolutions.akka.mobile.Mobile
import se.scalablesolutions.akka.mobile.actor._
import se.scalablesolutions.akka.mobile.theater._
import se.scalablesolutions.akka.mobile.tools.mobtrack.MobTrackGUI
import se.scalablesolutions.akka.mobile.util.messages._
import se.scalablesolutions.akka.mobile.serialization.DefaultActorFormat
import se.scalablesolutions.akka.mobile.theater.protocol.protobuf.ProtobufTheaterMessages._

import com.google.protobuf.ByteString

import se.scalablesolutions.akka.dispatch.FutureTimeoutException

import se.scalablesolutions.akka.serialization.Serializer

import se.scalablesolutions.akka.config.Config.config

object BinaryFormatMyActor {
   implicit object MyActorFormat extends StatelessActorFormat[MyActor]
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
case class TalkTo(ref: ActorRef, msg: Any)

case class MsgFunction(func: Function0[String])

case object ShowCount

@serializable class HeavyActor extends MobileActor {
  var load = Array[Char]()

  private def show(str: String): Unit = println("[" + this + "] " + str)
  
  def this(n: Int) = {
    this()
    load = (for (i <- 1 to n) yield 't').toArray
  }

  def receive = {
    case s: String if s == "Size" =>
      show("Load size: " + load.size)
    
    case msg => show("Received something: " + msg)
  }
}
     

@serializable class MyActor extends MobileActor {
  //self.makeRemote("localhost", 9999)
  
  private def show(str: String): Unit = println("[" + this + "] " + str)

  override def receive = {
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

    case TalkTo(ref, msg) =>
      ref ! msg

    case msg =>
      show("Received unknown message: " + msg)
   }

  override def beforeMigration() {
    show("MyActor starting a migration")
  }

  override def afterMigration() {
    show("MyActor finalizing a migration")
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

  override def beforeMigration() {
    show("### StatefulActor starting a migration ###")
  }

  override def afterMigration() {
    show("### StatefulActor finalizing a migration ###")
  }
}

object GeneralTests {

   def main(args: Array[String]) {

   }

   def execute() {
      import BinaryFormatMyActor._
      
      val actor1 = actorOf(new MyActor)
      //val actor1 = actorOf[MyActor]
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
      //println("* * * Start of serialization * * *")
      //actor1 ! Migrate
      //val bytes = actor1.serializedActor
      //actor1 ! Message("Após seriação")
      
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

  def testMigration(): Unit = {
    Mobile.startTheater(node1)

    val ref = Mobile.spawn[MyActor] here

    //ref ! Wait(20)
    //ref ! Message("APÓS MIGRAÇÃO")
    //LocalTheater.migrate(ref.uuid) to ("localhost", 2312)
    ref ! MoveTo(node2.hostname, node2.port)
    ref ! Message("RETIDA")

  }
  
  val actors = new scala.collection.mutable.Queue[ActorRef]

  def start(x: Int) = {
    for (count <- 1 to x)
      actors.enqueue(Actor.actorOf[TestActor].start)
  }

  def stop(x: Int) = {
    for (count <- 1 to x) {
      val ref = actors.dequeue()
      ref stop
    }
  }

  def stopAll() = {
    stop(actors size)
  }
  
  val node1 = TheaterNode("ubuntu-tcoraini", 1810)
  val node2 = TheaterNode("localhost", 2312)

  def testServer1(migrate: Boolean = false) = {
    //LocalTheater.start("localhost", 2312)
    Mobile.startTheater(node1.hostname, node1.port)
    val ref = Mobile.spawn[StatefulActor].here
    ref ! Ping
    ref ! ShowCount
    if (migrate) {
      ref ! MoveTo(node2.hostname, node2.port)
    }
    ref ! Ping
    ref ! ShowCount
    ref
  }

  def testColocated() = {
    Mobile.startTheater(node1.hostname, node1.port)
    val refs = Mobile.spawn[StatefulActor](10).here
    refs(4) ! Ping
    refs(4) ! ShowCount
    refs(7) ! MoveGroupTo(node2.hostname, node2.port)
    refs(4) ! Ping
    refs(4) ! ShowCount
    refs
  } 

  def testServer2 = {
    LocalTheater.start(node2.hostname, node2.port)
  }

  def testClient(uuid: String) = {
    LocalTheater.start("localhost", 2222)
    val refOpt = MobileActorRef(uuid) //, node1.hostname, node1.port)
    val ref = refOpt.getOrElse(throw new RuntimeException("Could not find an actor with UUID [" + uuid + "] running in the cluster."))
    ref ! Ping
    ref ! ShowCount
    ref
  }
  
  def testRemoteSpawn(): MobileActorRef = {
    LocalTheater.start(node2) // localhost:2312
//    val node = TheaterNode("ubuntu-tcoraini", 1810)
    val ref = Mobile.spawn[StatefulActor] at node1
    ref ! Ping
    ref ! ShowCount
    ref
  }

  def testDelayedMigration() =  {
    LocalTheater.start("ubuntu-tcoraini", 1810)
    val ref = Mobile.spawn[MyActor].here
//    ref ! Wait(3)
//    ref ! Wait(3)
    ref ! MoveTo("localhost", 2312)
    Thread.sleep(2000)
    println("[" + Thread.currentThread.getName + "] ###### ENVIANDO MENSAGENS 'WAIT' E 'MIGRATION DONE' ######")
    ref ! Wait(3)
    ref ! Message("Migration done!")
    ref
  }
  
  // Depende de 'migrationTasks' e 'status' sem private
  // def testGroupMigrationStatus() = {
  //   Mobile.startTheater("node_1")
  //   val refs = Mobile.spawn[SleepyActor](3) here
  //   val groupId = refs(0).groupId.get
  //   refs(1) ! Sleep(60000) // Dormindo por 5 minutos
  //   refs(0) ! MoveGroupTo("localhost", 2312)
    
  //   (groupId, () => GroupManagement.migrationTasks.get(groupId).get.status)
  // }
  
  def testGUI() = {
    new Thread {
      override def run() {
	MobTrackGUI.main(new Array[String](0))
      }
    } start

    val node1 = TheaterNode("ubuntu-tcoraini", 1810)
    val node2 = TheaterNode("localhost", 2312)

    Thread.sleep(3000)
    MobTrackGUI.arrive("MIGRATE_1", node1)
    Thread.sleep(1000)
    MobTrackGUI.arrive("B_00000", node1)
    Thread.sleep(1000)
    MobTrackGUI.migrate("MIGRATE_1", node1, node2)
    Thread.sleep(1000)
    MobTrackGUI.migrate("A_12345", node2, node1)
  }

  // def compareSerializations() {
  //   Mobile.startTheater("node_1")
  //   val sizes = List(0, 1024, 10240, 102400, 1048576, 10485760)
    
  //   println("ARRAY SIZE\t\tWITH REF\t\tWITHOUT REF\t\tDIFFERENCE")
  //   for (size <- sizes) {
  //     val (size1, size2) = createAndSerializeActor(size)
  //     println(size + "\t\t\t" + size1 + "\t\t\t" + size2 + "\t\t\t" + (size1 - size2))
  //   }
  // }

  // def createAndSerializeActor(size: Int): Tuple2[Int, Int] = {
  //   val format = DefaultActorFormat

  //   lazy val actor = new HeavyActor(size)
  //   val ref = Mobile.spawnHere(actor)

  //   val bytes_1 = ref.startMigration()
    
  //   val builder = MovingActorProtocol.newBuilder
  //     .setActorBytes(ByteString.copyFrom(format.toBinary(actor)))
  //     .build
  //   val bytes_2 = builder.toByteArray

  //   (bytes_1.size, bytes_2.size)
  // }
}

class TestActor extends Actor {
  def receive = {
    case msg =>
      println("Received: " + msg)
  }
}

