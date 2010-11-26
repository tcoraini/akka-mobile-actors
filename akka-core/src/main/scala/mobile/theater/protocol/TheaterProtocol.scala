package se.scalablesolutions.akka.mobile.theater.protocol

import se.scalablesolutions.akka.mobile.theater.Theater
import se.scalablesolutions.akka.mobile.theater.TheaterNode

import se.scalablesolutions.akka.mobile.util.messages._

abstract class TheaterProtocol(protected val theater: Theater) {
 
  def init()
  
  def sendTo(node: TheaterNode, message: TheaterMessage)

}
