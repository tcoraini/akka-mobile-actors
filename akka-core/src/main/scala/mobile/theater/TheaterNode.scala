package se.scalablesolutions.akka.mobile.theater

import se.scalablesolutions.akka.mobile.util.NodeInformation

case class TheaterNode(hostname: String, port: Int) {
  def isLocal: Boolean = LocalTheater.isLocal(hostname, port)
}

object TheaterNode {
  // Defining some implicit conversions, just for convenience

  // From TheaterNode to Tuple2 (hostname, port)
  implicit def theaterNodeToTuple2(node: TheaterNode): Tuple2[String, Int] = {
    (node.hostname, node.port)
  }

  // From NodeInformation to TheaterNode
  implicit def nodeInformationToTheaterNode(nodeInfo: NodeInformation) = TheaterNode(nodeInfo.hostname, nodeInfo.port)
}
