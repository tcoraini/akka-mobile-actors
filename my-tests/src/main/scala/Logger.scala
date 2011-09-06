package tests

import se.scalablesolutions.akka.mobile.theater.LocalTheater

import java.util.Date
import java.text.SimpleDateFormat
import java.io.OutputStream
import java.io.FileOutputStream

class Logger(outputStream: OutputStream) {

  private val dateFormat = new SimpleDateFormat("[dd/MM/yyyy - HH:mm:ss.SSS]")
  private val console = System.out

  def this() = {
    this(System.out)
  }

  def this(filename: String) = {
    this(new FileOutputStream(filename, true))
  }

  def debug(message: String, args: Any*) = {
    var result = message + "\n"
    for (arg <- args) {
      result = result.replaceFirst("%s", arg.toString)
    }

    val formatted = format(result)

    outputStream.write(formatted.getBytes)
    outputStream.flush()
  }
  
  def info(message: String, args: Any*) = {
    var result = message + "\n"
    for (arg <- args) {
      result = result.replaceFirst("%s", arg.toString)
    }

    val formatted = format(result)

    outputStream.write(formatted.getBytes)
    outputStream.flush()
    console.print(formatted)
  }

  private def format(message: String): String = {
    dateAndTime + " " + 
    threadName  + " " +
    currentNode + " - " +
    message
  }
    
  private def currentNode: String = {
    try {
      LocalTheater.node.format
    } catch {
      case e:Exception => ""
    }
  }
  
  private def threadName: String = "[" + Thread.currentThread.getName + "]"

  private def dateAndTime: String = dateFormat.format(new Date)
}
