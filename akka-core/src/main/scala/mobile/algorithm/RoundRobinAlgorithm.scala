package se.scalablesolutions.akka.mobile.algorithm

import se.scalablesolutions.akka.mobile.util.ClusterConfiguration
import se.scalablesolutions.akka.mobile.theater.TheaterNode

class RoundRobinAlgorithm extends DistributionAlgorithm {
  
  private val indexedTheaters = ClusterConfiguration.nodes.toIndexedSeq

  private val numberOfTheaters = indexedTheaters.size

  private var currentTheater = 0

  def chooseTheater: TheaterNode = {
    // indexedTheaters(currentTheater) is a Tuple2[String, TheaterNodeInformation]
    val nodeInfo = indexedTheaters(currentTheater)._2

    currentTheater = (currentTheater + 1) % numberOfTheaters
    
    nodeInfo.node
  }
}
