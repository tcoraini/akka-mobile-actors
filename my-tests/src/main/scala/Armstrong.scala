package tests

import se.scalablesolutions.akka.mobile.Mobile
import se.scalablesolutions.akka.mobile.actor.MobileActor
import se.scalablesolutions.akka.mobile.actor.MobileActorRef
import se.scalablesolutions.akka.mobile.theater.LocalTheater
import se.scalablesolutions.akka.actor.ActorRef
import se.scalablesolutions.akka.actor.Actor
import se.scalablesolutions.akka.util.Logging

case class Next(uuid: String)
case class Start(maxRounds: Int)
case object Token

class ArmstrongActor extends MobileActor {
  import Armstrong._

  private var next: MobileActorRef = null
  private var first = false
  private var before: Long = _
  private var rounds = 0
  private var maxRounds: Int = _

  private var tries = 0
  private var maxTries = 5

  private def actorId: String = "[UUID " + self.uuid + "] "
  
  def receive = {
    case Next(uuid) => 
      var nextOpt = MobileActorRef(uuid)
      while (!nextOpt.isDefined && tries < maxTries) {
	Thread.sleep(1000)
	nextOpt = MobileActorRef(uuid)
	tries = tries + 1
      }

      next = nextOpt.getOrElse(throw new IllegalArgumentException("UUID invalido: " + uuid))
      logger.debug("%s Executando -- NEXT: [UUID %s]", actorId, uuid)

    case Start(max) =>
      logger.info("%s START recebido", actorId)
      first = true
      before = System.currentTimeMillis
      maxRounds = max
      rounds = 1
      next ! Token

    case Token if (next != null) =>
      logger.debug("TOKEN chegando em %s", actorId)
      if (first) {
	var after = System.currentTimeMillis
	var time = after - before
	logger.info("%s Time elapsed: %s ms", actorId, time)
	before = System.currentTimeMillis
	rounds = rounds + 1
	
	if (rounds <= maxRounds) {
	  logger.debug("* * TOKEN saindo para [UUID %s] * *", next.uuid)
	  next ! Token
	} else {
	  logger.info("%s Terminado", actorId)
	}
      } else {
	logger.debug("TOKEN saindo para [UUID %s]", next.uuid)
	next ! Token
      }
  }
}

object Armstrong extends Logging {

  private val NUMBER_OF_ACTORS = 100
  private val MAX_ROUNDS = 3
  private val BATCH_SIZE = 1

  val logger = new Logger("logs/mobile-actors/mobile-actors.log")

  class Configuration() {
    var theaterName: String = _
    var start: Boolean = false
    var numActors: Int = NUMBER_OF_ACTORS
    var maxRounds: Int = MAX_ROUNDS
    var batchSize: Int = BATCH_SIZE
  }

  def main(args: Array[String]) {
    val config = configure(args.toList)

    val number = config.numActors
    val maxRounds = config.maxRounds
    val batchSize = config.batchSize
    
    if (config.theaterName != null && config.theaterName.trim.size > 0) {
      Mobile.startTheater(config.theaterName)
      logger.info("Iniciando um teatro")
    }
    
    if (config.start) {
      logger.info("Executando o desafio de Armstrong com: \n" + 
		"\tNúmero de atores: %s\n" + 
		"\tNúmero de rodadas: %s\n" + 
		"\tTamanho de grupos de atores co-locados: %s",
		number, maxRounds, batchSize)
      
      var remaining = number
      var first = Mobile.launch[ArmstrongActor](batchSize)
      var previous = chainActors(first)
      remaining = remaining - batchSize
      while (remaining > 0) {
	if (remaining % 10000 == 0) {
	  println((number - remaining))
	}
	var actors = Mobile.launch[ArmstrongActor](batchSize)
	previous ! Next(actors.head.uuid)
	previous = chainActors(actors)
	remaining = remaining - batchSize
      }
      previous ! Next(first.head.uuid)
      
      logger.info("Começando...")
      first.head ! Start(maxRounds)
    }
  }

  def configure(args: List[String], _config: Option[Configuration] = None): Configuration = {
    val config = _config.getOrElse(new Configuration)
    val remainingArgs: List[String] = args match {
      case "-t" :: theaterName :: tail =>
	config.theaterName = theaterName
	tail
      case "-start" :: tail =>
	config.start = true
	tail
      case "-n" :: nActors :: tail =>
	config.numActors = nActors.toInt
	tail
      case "-r" :: maxRounds :: tail =>
	config.maxRounds = maxRounds.toInt
	tail
      case "-b" :: batchSize :: tail =>
	config.batchSize = batchSize.toInt
	tail
      case _ :: tail =>
	tail
      case Nil =>
	Nil
    }
    if (remainingArgs == Nil) 
      config
    else 
      configure(remainingArgs, Some(config))
  }
 
  def chainActors(actors: List[MobileActorRef]): MobileActorRef = actors match {
    case first :: second :: tail => 
      first ! Next(second.uuid)
      chainActors(second :: tail)
    
    case last :: Nil =>
      last
    
    case Nil => null
  }
}
