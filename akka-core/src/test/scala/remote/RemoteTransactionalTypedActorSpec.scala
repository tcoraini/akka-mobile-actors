/**
 * Copyright (C) 2009-2010 Scalable Solutions AB <http://scalablesolutions.se>
 */

package se.scalablesolutions.akka.actor

import org.scalatest.Spec
import org.scalatest.Assertions
import org.scalatest.matchers.ShouldMatchers
import org.scalatest.BeforeAndAfterAll
import org.scalatest.junit.JUnitRunner
import org.junit.runner.RunWith
import org.junit.{Test, Before, After}

import se.scalablesolutions.akka.config.Config
import se.scalablesolutions.akka.config.TypedActorConfigurator
import se.scalablesolutions.akka.remote.{RemoteNode, RemoteServer, RemoteClient}

object RemoteTransactionalTypedActorSpec {
  val HOSTNAME = "localhost"
  val PORT = 9988
  var server: RemoteServer = null
}

@RunWith(classOf[JUnitRunner])
class RemoteTransactionalTypedActorSpec extends
  Spec with
  ShouldMatchers with
  BeforeAndAfterAll {

  import RemoteTransactionalTypedActorSpec._
  Config.config

  private val conf = new TypedActorConfigurator
  private var messageLog = ""

  override def beforeAll = {
    server = new RemoteServer()
    server.start(HOSTNAME, PORT)
    Thread.sleep(1000)
  }

  override def afterAll = {
    conf.stop
    try {
      server.shutdown
      RemoteClient.shutdownAll
      Thread.sleep(1000)
    } catch {
      case e => ()
    }
  }

  describe("Remote transactional in-memory TypedActor ") {
/*
    it("map should not rollback state for stateful server in case of success") {
      val stateful =  TypedActor.newRemoteInstance(classOf[TransactionalTypedActor], 1000, HOSTNAME, PORT)
      stateful.setMapState("testShouldNotRollbackStateForStatefulServerInCaseOfSuccess", "init") // set init state
      stateful.success("testShouldNotRollbackStateForStatefulServerInCaseOfSuccess", "new state") // transactionrequired
      stateful.getMapState("testShouldNotRollbackStateForStatefulServerInCaseOfSuccess") should equal("new state")
    }

    it("map should rollback state for stateful server in case of failure") {
      val stateful =  TypedActor.newRemoteInstance(classOf[TransactionalTypedActor], 1000, HOSTNAME, PORT)
      stateful.setMapState("testShouldRollbackStateForStatefulServerInCaseOfFailure", "init") // set init state
      val failer =TypedActor.newRemoteInstance(classOf[TypedActorFailer], 1000, HOSTNAME, PORT) //conf.getInstance(classOf[TypedActorFailer])
      try {
        stateful.failure("testShouldRollbackStateForStatefulServerInCaseOfFailure", "new state", failer) // call failing transactionrequired method
        fail("should have thrown an exception")
      } catch { case e => {} }
      stateful.getMapState("testShouldRollbackStateForStatefulServerInCaseOfFailure") should equal("init")
    }

    it("vector should not rollback state for stateful server in case of success") {
      val stateful =  TypedActor.newRemoteInstance(classOf[TransactionalTypedActor], 1000, HOSTNAME, PORT)
      stateful.setVectorState("init") // set init state
      stateful.success("testShouldNotRollbackStateForStatefulServerInCaseOfSuccess", "new state") // transactionrequired
      stateful.getVectorState should equal("new state")
    }

    it("vector should rollback state for stateful server in case of failure") {
      val stateful =  TypedActor.newRemoteInstance(classOf[TransactionalTypedActor], 1000, HOSTNAME, PORT)
      stateful.setVectorState("init") // set init state
      val failer =TypedActor.newRemoteInstance(classOf[TypedActorFailer], 1000, HOSTNAME, PORT) //conf.getInstance(classOf[TypedActorFailer])
      try {
        stateful.failure("testShouldRollbackStateForStatefulServerInCaseOfFailure", "new state", failer) // call failing transactionrequired method
        fail("should have thrown an exception")
      } catch { case e => {} }
      stateful.getVectorState should equal("init")
    }

    it("ref should not rollback state for stateful server in case of success") {
      val stateful =  TypedActor.newRemoteInstance(classOf[TransactionalTypedActor], 1000, HOSTNAME, PORT)
      stateful.setRefState("init") // set init state
      stateful.success("testShouldNotRollbackStateForStatefulServerInCaseOfSuccess", "new state") // transactionrequired
      stateful.getRefState should equal("new state")
    }

    it("ref should rollback state for stateful server in case of failure") {
      val stateful =  TypedActor.newRemoteInstance(classOf[TransactionalTypedActor], 1000, HOSTNAME, PORT)
      stateful.setRefState("init") // set init state
      val failer =TypedActor.newRemoteInstance(classOf[TypedActorFailer], 1000, HOSTNAME, PORT) //conf.getInstance(classOf[TypedActorFailer])
      try {
        stateful.failure("testShouldRollbackStateForStatefulServerInCaseOfFailure", "new state", failer) // call failing transactionrequired method
        fail("should have thrown an exception")
      } catch { case e => {} }
      stateful.getRefState should equal("init")
    }
    */
  }
}
