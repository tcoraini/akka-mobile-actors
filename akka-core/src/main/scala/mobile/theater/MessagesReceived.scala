package se.scalablesolutions.akka.mobile.theater

case class MessagesReceived(uuid: String, from: TheaterNode) extends Comparable[MessagesReceived] {
  private var _count: Int = 0

  def increment(): Unit = 
    _count = _count + 1
  
  def count = _count

  override def toString = "MessagesReceived(To [" + uuid + "] from " + from + " -> " + count + ")"

  override def hashCode = uuid.hashCode * from.hashCode

  override def equals(that: Any): Boolean = that match {
    case mr: MessagesReceived => 
      mr.uuid == this.uuid && mr.from == this.from

    case _ => false
  }

  // Comparable interface, needed to insert in the PriorityQueue
  def compareTo(o: MessagesReceived): Int = o.count - _count
}

/*object MessagesReceived {
  implicit val ordering = new Ordering[MessagesReceived] {
    def compare(x: MessagesReceived, y: MessagesReceived): Int = x.count - y.count
  }
}*/
