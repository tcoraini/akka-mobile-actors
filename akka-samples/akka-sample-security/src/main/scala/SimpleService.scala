/**
 * Copyright (C) 2009-2010 Scalable Solutions AB <http://scalablesolutions.se>
 */

package sample.security

import se.scalablesolutions.akka.actor.{SupervisorFactory, Transactor, Actor}
import se.scalablesolutions.akka.actor.Actor._
import se.scalablesolutions.akka.config.ScalaConfig._
import se.scalablesolutions.akka.util.Logging
import se.scalablesolutions.akka.security.{BasicAuthenticationActor,BasicCredentials,SpnegoAuthenticationActor,DigestAuthenticationActor, UserInfo}
import se.scalablesolutions.akka.stm.TransactionalMap
import se.scalablesolutions.akka.actor.ActorRegistry.actorFor

class Boot {
  val factory = SupervisorFactory(
    SupervisorConfig(
      RestartStrategy(OneForOne, 3, 100, List(classOf[Exception])),
      // Dummy implementations of all authentication actors
      // see akka.conf to enable one of these for the AkkaSecurityFilterFactory
      Supervise(
        actorOf[BasicAuthenticationService],
        LifeCycle(Permanent)) ::
     /**
      Supervise(
        actorOf[DigestAuthenticationService],
        LifeCycle(Permanent)) ::
      Supervise(
        actorOf[SpnegoAuthenticationService],
        LifeCycle(Permanent)) ::
      **/
      Supervise(
        actorOf[SecureTickActor],
        LifeCycle(Permanent)):: Nil))

  val supervisor = factory.newInstance
  supervisor.start
}

/*
 * In akka.conf you can set the FQN of any AuthenticationActor of your wish, under the property name: akka.rest.authenticator
 */
class DigestAuthenticationService extends DigestAuthenticationActor {
  //If you want to have a distributed nonce-map, you can use something like below,
  //don't forget to configure your standalone Cassandra instance
  //
  //makeTransactionRequired
  //override def mkNonceMap = Storage.newMap(CassandraStorageConfig()).asInstanceOf[scala.collection.mutable.Map[String,Long]]

  //Use an in-memory nonce-map as default
  override def mkNonceMap = new scala.collection.mutable.HashMap[String, Long]

  //Change this to whatever you want
  override def realm = "test"

  //Dummy method that allows you to log on with whatever username with the password "bar"
  override def userInfo(username: String): Option[UserInfo] = Some(UserInfo(username, "bar", "ninja" :: "chef" :: Nil))
}

class BasicAuthenticationService extends BasicAuthenticationActor {

  //Change this to whatever you want
  override def realm = "test"

  //Dummy method that allows you to log on with whatever username
  def verify(odc: Option[BasicCredentials]): Option[UserInfo] = odc match {
    case Some(dc) => userInfo(dc.username)
    case _ => None
  }

  //Dummy method that allows you to log on with whatever username with the password "bar"
  def userInfo(username: String): Option[UserInfo] = Some(UserInfo(username, "bar", "ninja" :: "chef" :: Nil))

}

class SpnegoAuthenticationService extends SpnegoAuthenticationActor {
  def rolesFor(user: String) = "ninja" :: "chef" :: Nil

}

/**
 * a REST Actor with class level paranoia settings to deny all access
 *
 * The interesting part is
 * @RolesAllowed
 * @PermitAll
 * @DenyAll
 */
import java.lang.Integer
import javax.annotation.security.{RolesAllowed, DenyAll, PermitAll}
import javax.ws.rs.{GET, Path, Produces}

@Path("/secureticker")
class SecureTickService {

  /**
   * allow access for any user to "/secureticker/public"
   */
  @GET
  @Produces(Array("text/xml"))
  @Path("/public")
  @PermitAll
  def publicTick = tick

  /**
   * restrict access to "/secureticker/chef" users with "chef" role
   */
  @GET
  @Path("/chef")
  @Produces(Array("text/xml"))
  @RolesAllowed(Array("chef"))
  def chefTick = tick

  /**
   * access denied for any user to default Path "/secureticker/"
   */
  @GET
  @Produces(Array("text/xml"))
  @DenyAll
  def paranoiaTick = tick

  def tick = {
        //Fetch the first actor of type PersistentSimpleServiceActor
        //Send it the "Tick" message and expect a NdeSeq back
        val result = for{a <- actorFor[SecureTickActor]
                         r <- (a !! "Tick").as[Integer]} yield r
        //Return either the resulting NodeSeq or a default one
        result match {
      case (Some(counter)) => (<success>Tick: {counter}</success>)
      case _ => (<error>Error in counter</error>)
    }
  }
}

class SecureTickActor extends Transactor with Logging {
  private val KEY = "COUNTER"
  private var hasStartedTicking = false
  private lazy val storage = TransactionalMap[String, Integer]()
  def receive = {
    case "Tick" => if (hasStartedTicking) {
      val counter = storage.get(KEY).get.intValue
      storage.put(KEY, counter + 1)
      self.reply(new Integer(counter + 1))
    } else {
      storage.put(KEY, 0)
      hasStartedTicking = true
      self.reply(new Integer(0))
    }
  }
}
