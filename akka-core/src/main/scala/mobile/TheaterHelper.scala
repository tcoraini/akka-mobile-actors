package se.scalablesolutions.akka.mobile

object TheaterHelper {
  
  //def startActor(clazz: Class[_ <: MobileActor]) = startActor(Left(clazz.getName))

  //def startActor(factory: () => MobileActor) = startActor(Right(factory))

  // Starts an actor of class 'clazz' at some theater
  // Syntax: startActor(clazz) at (host, port)
  def startActor(constructor: Either[String, () => MobileActor]) = new {
  //def startActor(clazz: Class[_ <: MobileActor]) = new {
    
    def at(location: Tuple2[String, Int]): MobileActorRef = {
      val hostname = location._1
      val port = location._2

      if (Theater.isLocal(hostname, port)) {
        startLocalActor(constructor)
      } else {
        val agent = agentFor(hostname, port)
        (agent !! StartMobileActorRequest(constructor)) match {
          case Some(StartMobileActorReply(uuid)) => 
            log.debug("Mobile actor with UUID %s started remote theater %s:%d", uuid, hostname, port)
            Mobile.mobileOf(uuid, hostname, port, 5000L) // TODO Timeout hard-coded?

          case None =>
            log.debug("Could not start actor at remote theater %s:%d, request timeout", hostname, port)
            throw new RuntimeException("Remote mobile actor start failed") // devolver algo relevante pra indicar o problema
        }
      }
    }
  }
}
