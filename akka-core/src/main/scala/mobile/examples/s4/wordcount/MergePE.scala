package se.scalablesolutions.akka.mobile.examples.s4.wordcount

import se.scalablesolutions.akka.mobile.examples.s4.ProcessingElement

import java.io.FileWriter
import scala.collection.mutable.HashMap

@serializable class MergePE extends ProcessingElement[PartialTopKEvent] with Runnable {
  val words = new HashMap[String, Int]

  private var writer: FileWriter = _
  
  protected override def start(prototype: PartialTopKEvent) = {
    super.start(prototype)
    writer = new FileWriter(WordCounter.partialResultsFile)
    // Starts this as a new Thread
    new Thread(this).start()
  }

  def process(event: PartialTopKEvent) = {
    val listOfWords = event.attribute
    listOfWords.foreach(wordTuple => words.put(wordTuple._1, wordTuple._2))
  }

  override def run(): Unit = {
    while (isRunning) {
      Thread.sleep(WordCounter.mergePEInterval)

      if (!words.isEmpty) {
	var listOfWords = words.toList
	// Sorts the list of entries in descending order related to the count (second member of the tuple)
	listOfWords = listOfWords.sortWith((wordTupleA, wordTupleB) => wordTupleA._2 > wordTupleB._2)
	// Take first K elements
	listOfWords = listOfWords.take(WordCounter.K)
	// Print the top K words
	printPartialResult(listOfWords)
      }
    }
    writer.close()
  }

  private def printPartialResult(listOfWords: List[Tuple2[String, Int]]): Unit = {
    println("-" * 100)
    println("Date: " + new java.util.Date)
    println("Number of words: " + listOfWords.size + "\n")
    for ((word, count) <- listOfWords) {
      writer.write(word + ": " + count + "\n")
    }
    println("-" * 100)
    println()
  }
}
