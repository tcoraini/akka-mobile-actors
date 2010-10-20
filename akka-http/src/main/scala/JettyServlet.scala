/**
 * Copyright 2010 Autodesk, Inc.  All rights reserved.
 * Licensed under Apache License, Version 2.0 (the "License"); you may not use this software except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0.
 */

package se.scalablesolutions.akka.http.mist


import se.scalablesolutions.akka.util.Logging
import javax.servlet.http.HttpServlet

/**
 * @author Garrick Evans
 */

class JettyServlet extends HttpServlet
                   with Logging
{
  import java.util. {Date, TimeZone}
  import java.text.SimpleDateFormat
  import javax.servlet.ServletConfig
  import javax.servlet.http.{HttpServletRequest, HttpServletResponse}
  import org.eclipse.jetty.continuation._
  import org.eclipse.jetty.server._
  import se.scalablesolutions.akka.actor.ActorRegistry

  final val NotJetty = "Request is not a Jetty request object -- wrong container for servlet."

  //
  // the root endpoint for this servlet will have been booted already
  //  use the system property to find out the actor id and cache him
  //  TODO: currently this is hardcoded but really use a property
  //
  protected val _root = ActorRegistry.actorsFor("DefaultGridRoot").head

  /**
   * Handles the HTTP request method on the servlet, suspends the connection and sends the asynchronous context
   * along to the root endpoint in a SuspendedRequest message
   */
  protected def _do(request: HttpServletRequest, response: HttpServletResponse)(builder: (()=>Option[AsyncContext], ()=>Option[ContinuationListener]) => SuspendedRequest) =
    {
      request.asInstanceOf[Request] match
      {
        case null =>
          {
            log error NotJetty

            response.setStatus(HttpServletResponse.SC_NOT_IMPLEMENTED)
            response.getWriter.write(NotJetty)
          }

        case req =>
          {
            def suspend:Option[AsyncContext] =
              {
                  //
                  // set to effectively "already expired"
                  //
                val gmt = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss z")
                    gmt.setTimeZone(TimeZone.getTimeZone("GMT"))

                response.setHeader("Expires", gmt.format(new Date))
                response.setHeader("Cache-Control", "no-cache, must-revalidate")
                response.setHeader("Connection","close")

                req.getAsyncContinuation match
                {
                  case null =>
                    {
                      response.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED, "Container does not support necessary asynchronous context.")
                      log.error("Cannot suspend request with unsupported asynchronous context type. REQUEST ("+request.toString+")")
                      None
                    }
                  case continuation => Some(continuation)
                }
              }

            def hook:Option[ContinuationListener] = None

            //
            // shoot the message to the root endpoint for processing
            // IMPORTANT: the suspend method is invoked on the jetty thread not in the actor
            //
            val msg = builder(suspend _, hook _)
            if (msg.context ne None) {_root ! msg}
          }
      }
    }

  //
  // HttpServlet API
  //

  override def init(config: ServletConfig) =
    {
      super.init(config)
      log.info("Mist for Akka - Jetty servlet initialized.")
    }

  
  protected override def doDelete(request: HttpServletRequest, response: HttpServletResponse) = _do(request, response)((f:(()=>Option[AsyncContext]), g:(()=>Option[ContinuationListener])) => Delete(f,g))
  protected override def doGet(request: HttpServletRequest, response: HttpServletResponse) = _do(request, response)((f:(()=>Option[AsyncContext]), g:(()=>Option[ContinuationListener])) => Get(f,g))
  protected override def doHead(request: HttpServletRequest, response: HttpServletResponse) = _do(request, response)((f:(()=>Option[AsyncContext]), g:(()=>Option[ContinuationListener])) => Head(f,g))
  protected override def doOptions(request: HttpServletRequest, response: HttpServletResponse) = _do(request, response)((f:(()=>Option[AsyncContext]), g:(()=>Option[ContinuationListener])) => Options(f,g))
  protected override def doPost(request: HttpServletRequest, response: HttpServletResponse) = _do(request, response)((f:(()=>Option[AsyncContext]), g:(()=>Option[ContinuationListener])) => Post(f,g))
  protected override def doPut(request: HttpServletRequest, response: HttpServletResponse) = _do(request, response)((f:(()=>Option[AsyncContext]), g:(()=>Option[ContinuationListener])) => Put(f,g))
  protected override def doTrace(request: HttpServletRequest, response: HttpServletResponse) = _do(request, response)((f:(()=>Option[AsyncContext]), g:(()=>Option[ContinuationListener])) => Trace(f,g))
}

