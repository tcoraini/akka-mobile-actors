package se.scalablesolutions.akka.mobile

import se.scalablesolutions.akka.util.Logging

import java.net.InetAddress

object MobileActorInfrastructure extends Logging {
  
  def main(args: Array[String]) {
    log.info("Starting the mobile actors infrastructure...Deploying the local Theater")

    val myHostname = // TODO gambiarra temporaria
      if (args.size > 0)
        args(0)
      else
        InetAddress.getLocalHost.getHostName

    ClusterConfiguration.nodes.get(myHostname) match {
      case Some(nodeInfo) =>
        Theater.start(nodeInfo.hostname, nodeInfo.port)

      case None =>
        throw new RuntimeException("There is no node description for this hostname (" + myHostname + ") on your configuration file")
    }
  }
}
