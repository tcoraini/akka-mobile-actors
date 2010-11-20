/**
 * Copyright (C) 2009-2010 Scalable Solutions AB <http://scalablesolutions.se>
 */

package akka.util

import org.slf4j.{Logger => SLFLogger,LoggerFactory => SLFLoggerFactory}

import java.io.StringWriter
import java.io.PrintWriter
import java.net.InetAddress
import java.net.UnknownHostException

/**
 * Base trait for all classes that wants to be able use the logging infrastructure.
 *
 * @author <a href="http://jonasboner.com">Jonas Bon&#233;r</a>
 */
trait Logging {
  @transient val log = Logger(this.getClass.getName)
}

/**
 * Scala SLF4J wrapper
 *
 * Example:
 * <pre>
 * class Foo extends Logging {
 *   log.info("My foo is %s","alive")
 *   log.error(new Exception(),"My foo is %s","broken")
 * }
 * </pre>
 *
 * The logger uses String.format:
 * http://download-llnw.oracle.com/javase/6/docs/api/java/lang/String.html#format(java.lang.String,%20java.lang.Object...)
 */
class Logger(val logger: SLFLogger) {
  final def name      = logger.getName

  final def trace_?   = logger.isTraceEnabled
  final def debug_?   = logger.isDebugEnabled
  final def info_?    = logger.isInfoEnabled
  final def warn_? = logger.isWarnEnabled
  final def error_?   = logger.isErrorEnabled

  protected final def message(fmt: String, arg: Any, argN: Any*) : String = {
    if ((argN eq null) || argN.isEmpty) fmt.format(arg)
    else fmt.format((arg +: argN):_*)
  }

  protected final def toArray(arg1: Any, arg2: Any, arg3: Any, rest: Seq[Any] = Nil): Array[AnyRef] = if ((rest eq null) || rest.isEmpty) {
    Array[AnyRef](arg1.asInstanceOf[AnyRef],arg2.asInstanceOf[AnyRef],arg3.asInstanceOf[AnyRef])
  } else {
    val restLength = rest.length
    val a = new Array[AnyRef](3 + restLength)
    a(0) = arg1.asInstanceOf[AnyRef]
    a(1) = arg2.asInstanceOf[AnyRef]
    a(2) = arg3.asInstanceOf[AnyRef]
    var i = 0
    while(i < restLength) {
      a(i+3) = rest(i).asInstanceOf[AnyRef]
      i = i + 1
    }
    a
  }

  //Trace
  def trace(t: Throwable, fmt: => String, arg: Any, argN: Any*) {
    trace(t,message(fmt,arg,argN:_*))
  }

  def trace(t: Throwable, msg: => String) {
    if (trace_?) logger.trace(msg,t)
  }

  def trace(msg: => String) {
     if (trace_?) logger trace msg
  }

  def trace(fmt: => String, arg: Any) {
    if (trace_?) logger.trace(fmt,arg)
  }

  def trace(fmt: => String, arg1: Any, arg2: Any) {
    if (trace_?) logger.trace(fmt,arg1,arg2)
  }

  def trace(fmt: => String, arg1: Any, arg2: Any, arg3: Any, rest: Any*) {
    if (trace_?) logger.trace(fmt,toArray(arg1,arg2,arg3,rest))
  }

  //Debug
  def debug(t: Throwable, fmt: => String, arg: Any, argN: Any*) {
    debug(t,message(fmt,arg,argN:_*))
  }

  def debug(t: Throwable, msg: => String) {
    if (debug_?) logger.debug(msg,t)
  }

  def debug(msg: => String) {
     if (debug_?) logger debug msg
  }

  def debug(fmt: => String, arg: Any) {
    if (debug_?) logger.debug(fmt,arg)
  }

  def debug(fmt: => String, arg1: Any, arg2: Any) {
    if (debug_?) logger.debug(fmt,arg1,arg2)
  }

  def debug(fmt: => String, arg1: Any, arg2: Any, arg3: Any, rest: Any*) {
    if (debug_?) logger.debug(fmt,toArray(arg1,arg2,arg3,rest))
  }

  //Info
  def info(t: Throwable, fmt: => String, arg: Any, argN: Any*) {
    info(t,message(fmt,arg,argN:_*))
  }

  def info(t: Throwable, msg: => String) {
    if (info_?) logger.info(msg,t)
  }

  def info(msg: => String) {
     if (info_?) logger info msg
  }

  def info(fmt: => String, arg: Any) {
    if (info_?) logger.info(fmt,arg)
  }

  def info(fmt: => String, arg1: Any, arg2: Any) {
    if (info_?) logger.info(fmt,arg1,arg2)
  }

  def info(fmt: => String, arg1: Any, arg2: Any, arg3: Any, rest: Any*) {
    if (info_?) logger.info(fmt,toArray(arg1,arg2,arg3,rest))
  }

  //Warning
  def warn(t: Throwable, fmt: => String, arg: Any, argN: Any*) {
    warn(t,message(fmt,arg,argN:_*))
  }

  def warn(t: Throwable, msg: => String) {
    if (warn_?) logger.warn(msg,t)
  }

  def warn(msg: => String) {
     if (warn_?) logger warn msg
  }

  def warn(fmt: => String, arg: Any) {
    if (warn_?) logger.warn(fmt,arg)
  }

  def warn(fmt: => String, arg1: Any, arg2: Any) {
    if (warn_?) logger.warn(fmt,arg1,arg2)
  }

  def warn(fmt: => String, arg1: Any, arg2: Any, arg3: Any, rest: Any*) {
    if (warn_?) logger.warn(fmt,toArray(arg1,arg2,arg3,rest))
  }

  //Error
  def error(t: Throwable, fmt: => String, arg: Any, argN: Any*) {
    error(t,message(fmt,arg,argN:_*))
  }

  def error(t: Throwable, msg: => String) {
    if (error_?) logger.error(msg,t)
  }

  def error(msg: => String) {
     if (error_?) logger error msg
  }

  def error(fmt: => String, arg: Any) {
    if (error_?) logger.error(fmt,arg)
  }

  def error(fmt: => String, arg1: Any, arg2: Any) {
    if (error_?) logger.error(fmt,arg1,arg2)
  }

  def error(fmt: => String, arg1: Any, arg2: Any, arg3: Any, rest: Any*) {
    if (error_?) logger.error(fmt,toArray(arg1,arg2,arg3,rest))
  }
}

/**
 * Logger factory
 *
 * ex.
 *
 * val logger = Logger("my.cool.logger")
 * val logger = Logger(classOf[Banana])
 * val rootLogger = Logger.root
 *
 */
object Logger {

  /* Uncomment to be able to debug what logging configuration will be used
  {
  import org.slf4j.LoggerFactory
  import ch.qos.logback.classic.LoggerContext
  import ch.qos.logback.core.util.StatusPrinter

  // print logback's internal status
  StatusPrinter.print(LoggerFactory.getILoggerFactory.asInstanceOf[LoggerContext])
  }*/

  def apply(logger: String)  : Logger = new Logger(SLFLoggerFactory getLogger logger)
  def apply(clazz: Class[_]) : Logger = apply(clazz.getName)
  def root                   : Logger = apply(SLFLogger.ROOT_LOGGER_NAME)
}
