package se.scalablesolutions.akka.mobile.theater

case class TheaterNode(hostname: String, port: Int) {
  def isLocal: Boolean = LocalTheater.isLocal(hostname, port)
}

object TheaterNode {
  // Defining some implicit conversions, just for convenience

  // From TheaterNode to Tuple2 (hostname, port)
  implicit def theaterNodeToTuple2(node: TheaterNode): Tuple2[String, Int] = {
    (node.hostname, node.port)
  }

}

case class TheaterNodeInformation(name: String, node: TheaterNode, hasNameServer: Boolean)
