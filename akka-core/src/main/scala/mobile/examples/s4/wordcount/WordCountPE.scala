package se.scalablesolutions.akka.mobile.examples.s4.wordcount

import se.scalablesolutions.akka.mobile.examples.s4.ProcessingElement

import scala.util.Random

//@serializable class WordCountPE(eventPrototype: WordEvent) extends ProcessingElement(eventPrototype) {
@serializable class WordCountPE extends ProcessingElement[WordEvent] {

  private val sortPEKey = Random.nextInt(WordCounter.numberOfSortPEs)

  private var count = 0

  private var word: String = _

  def process(event: WordEvent) {
    // This should ALWAYS be true
    if (event.key == word) {
      count = count + 1
      emit(UpdateCountEvent(sortPEKey, (word, count)))
    } else // Should NEVER happen, exception just to make sure it doesn't
      throw new RuntimeException("WordCountPE for word '" + word + "' received a WordEvent with word '" + event.key + "'.")
  }
  
  protected override def start(prototype: WordEvent) = {
    super.start(prototype)
    word = key.asInstanceOf[String]
  }
}
