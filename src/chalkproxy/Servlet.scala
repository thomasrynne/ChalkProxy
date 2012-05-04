package chalkproxy

import javax.servlet.http._
import scala.collection.mutable.LinkedList
import scala.xml.Node
import org.eclipse.jetty.server.handler.AbstractHandler
import org.eclipse.jetty.server.Request

class ProxyHandler(registry:Registry) extends AbstractHandler {
  override def handle(target:String, request:Request, httpRequest:HttpServletRequest, response:HttpServletResponse) {
    request.setHandled(true)
    val path = request.getRequestURI
    val slash = path.substring(1).indexOf("/")
    val firstPath =
      if (slash.equals(-1)) {
        path.substring(1)
      } else {
        path.substring(1,slash+1)
      }
    val portOrNone = registry.lookup(firstPath)
    portOrNone match {
      case None =>
        response.setStatus(404)
        response.setContentType("text/plain")
        response.getWriter.println("404 page not found")
        response.getWriter.println()
        response.getWriter.println("Valid prefixes:")
        registry.instances.foreach( (instance) => {
          response.getWriter.println(" " + instance.instance.prefix)
        })
            
      case Some(instance) => {
        if (path.equals("/" + firstPath)) {
          response.sendRedirect(firstPath + "/")
        } else {
          new DoForward().forward(
            request,
            response,
            instance.host,
            instance.port,
            firstPath)
           }
        }
    }
  }
}

class PartialHandler(registry:Registry) extends AbstractHandler {
  override def handle(target:String, request:Request, httpRequest:HttpServletRequest, response:HttpServletResponse) {
    val view = View.create(request.getParameter("groupBy"), request.getParameter("filter"), request.getParameter("filter"))
    val html = Page.listing(registry.instances, view)
    response.setContentType("text/html")
    response.getWriter.println(html)
    request.setHandled(true)
  }
}

class ListHandler(registry:Registry) extends AbstractHandler {
  override def handle(target:String, request:Request, httpRequest:HttpServletRequest, response:HttpServletResponse) {
    response.setContentType("text/plain")
    registry.instances.foreach { entry => {
      if (!entry.isClosed) {
    	  response.getWriter.println(entry.instance.name)
      }
    }}
    request.setHandled(true)
  }
}

class PageHandler(registry:Registry) extends AbstractHandler {
  override def handle(target:String, request:Request, httpRequest:HttpServletRequest, response:HttpServletResponse) {
    val view =
      if (request.getParameter("groupBy") == null && request.getParameter("filter")==null) {
        registry.defaultView
      } else {
        View.create(request.getParameter("groupBy"), request.getParameter("filter"), request.getParameter("design"))
      }
    val showDisconnected = request.getParameter("showDisconnected") == "true"
    val instances = registry.instances
    val html = Page.listing(instances, view)
    val props = instances.flatMap(_.propNames).toSet.toList.sorted
    val page = Page.fullPage(registry.name, html, props, registry.defaultView, view.copy(showDisconnected = showDisconnected))
    response.setContentType("text/html")
    response.getWriter.println("<!DOCTYPE html>")
    response.getWriter.println(page)
    request.setHandled(true)
  }
}

