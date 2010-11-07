package se.scalablesolutions.akka.mobile

class RoundRobinAlgorithm extends DistributionAlgorithm {
  
  private val indexedTheaters = ClusterConfiguration.nodes.toIndexedSeq

  private val numberOfTheaters = indexedTheaters.size

  private var currentTheater = 0

  def chooseTheater: TheaterNode = {
    // indexedTheaters(currentTheater) is a Tuple2[String, NodeInformation]
    val nodeInfo = indexedTheaters(currentTheater)._2

    currentTheater = (currentTheater + 1) % numberOfTheaters
    
    TheaterNode(nodeInfo.hostname, nodeInfo.port)
  }
}
