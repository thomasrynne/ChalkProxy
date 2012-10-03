package chalkproxy

import javax.servlet.http._
import java.io.File
import java.net.URLConnection
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

class PageHandler(registry:Registry, assets:EmbeddedAssetsHandler) extends AbstractHandler {
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
    val html = registry.page.listing(instances, view)
    val props = instances.flatMap(_.propNames).toSet.toList.sorted
    val page = registry.page.fullPage(registry.name, homePage, html, props, state, registry.serverStartId, view, firebugLite)
    response.setContentType("text/html")
    response.setHeader("Cache-control", "no-cache")
    response.getWriter.println("<!DOCTYPE html>")
    response.getWriter.println(page)
    request.setHandled(true)
  }
}

class EmbeddedAssetsHandler extends AbstractHandler {
  private val ONE_YEAR = 365L*24*60*60*1000
  private val l = "/assets/".length
  private val hashesCache = new java.util.concurrent.ConcurrentHashMap[String,(Long,String)]()
  def url(fullUrl:String) = {
    val name = fullUrl.substring(l)
    buildHash(name) match {
      case Left(msg) => throw new Exception("There is no asset " + name)
      case Right(hash) => "/assets/hash-" + hash + "-" + name
    }
    
  }
  
  def buildHash(name:String) = {
    readResource(name) match {
      case Left(msg) => Left(msg)
      case Right(connection) => Right(hashFor(name, connection))
    }
  }
  override def handle(target:String, request:Request, httpRequest:HttpServletRequest, response:HttpServletResponse) {
    val name = request.getPathInfo().substring(l)
    readName(name) match {
      case Left(msg) => {
        response.setStatus(404, msg)
        request.setHandled(true)
      }
      case Right(connection) => {
        writeIfNeeded(name, connection, request, response)
        request.setHandled(true)
      }
      case _ =>
    }
  }
    
  private def writeIfNeeded(name:String, connection:URLConnection, request:HttpServletRequest, response:HttpServletResponse) {
    if (request.getHeader(HttpHeaders.IF_MODIFIED_SINCE) != null) {
      //on a forced refresh the browser asks again, there's no need to check the last modified as these never change
	  response.setStatus(304)
      response.setHeader("Cache-control", "public, max-age=31536000")
      response.setDateHeader("Expires", System.currentTimeMillis() + ONE_YEAR)
    } else {
	  val lastModified = connection.getLastModified()
      response.setHeader("Cache-control", "public, max-age=31536000")
      response.setDateHeader("Expires", System.currentTimeMillis() + ONE_YEAR)
      response.setContentType(contentTypeFor(name))
	  response.setDateHeader(HttpHeaders.LAST_MODIFIED, lastModified)
	  Utils.copy(connection.getInputStream(), response.getOutputStream())
    }
  }
  
  def readName(name:String) = {
    if (name.startsWith("hash-")) {
      val hash = name.substring(5, 21)
      val realName = name.substring(22)
      buildHash(realName) match {
        case Left(msg) => Left(msg)
        case Right(generatedHash) => {
          if (generatedHash == hash) {
            readResource(realName)
          } else {
            Left("file has changed since hash was generated")
          }
        }
      }
    } else if (isVersioned(name)) {
      readResource(name)
    } else {
      Left("Not a hash url and not versioned")
    }
  }
  
  def readResource(name:String) = {
	val url = ClassLoader.getSystemResource(name)
    if (url != null) {
      Right(url.openConnection())
    } else {
      Left(name + " not found")
    }
  }
  
  def dateHeader(httpRequest:HttpServletRequest, name:String) = {
    try {
      val r = httpRequest.getDateHeader(name)
      if (r == -1) None else Some(r)
    } catch {
      case e:IllegalStateException => None
    }
  }
  
  def isVersioned(name:String) = {
    val numbers = name.filter(_.isDigit).size
    val dots = name.filter(_ == '.').size
    numbers > 1 && dots > 1
  }
  
  private def contentTypeFor(name:String) = {
    val extension = name.substring(name.lastIndexOf(".")+1)
    extension match {
      case "js" => "text/javascript"
      case "css" => "text/css"
      case "jpg" => "image/jpeg"
      case "png" => "image/png"
    }
  }
  
  def hashFor(name:String, connection:URLConnection):String = {
    val result = hashesCache.get(name)
    val lastModified = connection.getLastModified()
    if (result != null && result._1 == lastModified) {
      result._2
    } else {
      val h = Utils.calculateHash(connection.getInputStream()).substring(0, 16)
      hashesCache.put(name, (lastModified, h))
      h
    }
  }  
}
