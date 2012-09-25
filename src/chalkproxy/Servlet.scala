package chalkproxy

import javax.servlet.http._
import java.io.File
import scala.collection.mutable.LinkedList
import scala.xml.Node
import org.eclipse.jetty.server.handler.AbstractHandler
import org.eclipse.jetty.server.Request
import org.json.{JSONObject, JSONArray}
import org.eclipse.jetty.http.HttpHeaders

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
        registry.instances._1.foreach( (instance) => {
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

class ListHandler(registry:Registry) extends AbstractHandler {
  override def handle(target:String, request:Request, httpRequest:HttpServletRequest, response:HttpServletResponse) {
    response.setContentType("text/plain")
    registry.instances._1.foreach { entry => {
      if (!entry.isClosed) {
    	  response.getWriter.println(entry.instance.prefix)
      }
    }}
    request.setHandled(true)
  }
}

class PageHandler(registry:Registry) extends AbstractHandler {
  override def handle(target:String, request:Request, httpRequest:HttpServletRequest, response:HttpServletResponse) {
    val (homePage, view) =
      if (request.getParameter("groupBy") == null && request.getParameter("filter")==null) {
        (true, registry.defaultView)
      } else {
        (false,
            View.create(request.getParameter("groupBy"), request.getParameter("filter"), request.getParameter("design"), request.getParameter("showDisconnected")))
      }
    val firebugLite = request.getParameter("debug") == "firebuglite"
    val (instances,state) = registry.instances
    val html = Page.listing(instances, view)
    val props = instances.flatMap(_.propNames).toSet.toList.sorted
    val page = Page.fullPage(registry.name, homePage, html, props, state, registry.serverStartId, view, firebugLite)
    response.setContentType("text/html")
    response.getWriter.println("<!DOCTYPE html>")
    response.getWriter.println(page)
    request.setHandled(true)
  }
}


class EmbeddedAssetsHandler extends AbstractHandler {
  val l = "/assets/".length
  override def handle(target:String, request:Request, httpRequest:HttpServletRequest, response:HttpServletResponse) {
    val url = ClassLoader.getSystemResource(request.getPathInfo().substring(l))
    val extension = request.getPathInfo.substring(request.getPathInfo.lastIndexOf(".")+1)
    response.setContentType(contentTypeFor(extension))
    if (url != null) {
      val connection = url.openConnection()
      val lastModified = connection.getLastModified()
      
      dateHeader(httpRequest, HttpHeaders.IF_MODIFIED_SINCE) match {
        case Some(d) if (d >= lastModified) => {
          response.setStatus(304)
        }
        case _ => {
          response.setDateHeader(HttpHeaders.LAST_MODIFIED, lastModified)
          Utils.copy(connection.getInputStream(), response.getOutputStream())
        }
      }
    }
    request.setHandled(true)
  }
  
  def dateHeader(httpRequest:HttpServletRequest, name:String) = {
    try {
      val r = httpRequest.getDateHeader(name)
      if (r == -1) None else Some(r)
    } catch {
      case e:IllegalStateException => None
    }
  }
  
  private def contentTypeFor(extension:String) = {
    extension match {
      case "js" => "text/javascript"
      case "css" => "text/css"
      case "jpg" => "image/jpeg"
      case "png" => "image/png"
    }
  }
}
