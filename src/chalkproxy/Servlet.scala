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
    val groupFilter = request.getParameter("groups") match { 
      case null => None
      case "all" => None
      case "" => None
      case v => Some(v.split(":").toList)
    }
    val html = Page.listing(registry.instances, groupFilter)
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
    val (groupFilter, link) = target match {
      case "/" => (registry.rootGroupFilter, if (registry.rootGroupFilter.isDefined) <a id='others' href='/all'>Show all</a> else <span/>)
      case "/all" => (None, if (registry.rootGroupFilter.isDefined) <a id='others' href='/'>Show Special</a> else <span/>)
    }
    val html = Page.listing(registry.instances, groupFilter)
    val groupsFilter = groupFilter match {
      case Some(g) => g.mkString(":")
      case None => "all"
    }
    val message = ""
    val page =
<html>
    <head debug="true">
        <title>{registry.name}</title>
        <link rel="stylesheet" media="screen" href="/assets/bootstrap.css"/>
        <link rel="stylesheet" media="screen" href="/assets/main.css"/>
        <link rel="shortcut icon" type="image/png" href="/assets/favicon.png"/>
        <script type="text/javascript">
          window.WEB_SOCKET_SWF_LOCATION = '/assets/WebSocketMain.swf';
          window.GROUPS = '{groupsFilter}'; 
        </script>
        <!-- <script type="text/javascript" src="https://getfirebug.com/firebug-lite-debug.js"></script> -->
        <script src="/assets/jquery-1.7.1.min.js" type="text/javascript"></script>
        <script src="/assets/json2.js" type="text/javascript"></script>
        <script type="text/javascript" src="/assets/swfobject.js"></script>
        <script type="text/javascript" src="/assets/web_socket.js"></script>
    </head>
    <body>
        <div class="container">
            <div class="content">
                <img id="logo" src="/assets/chalks.jpg"/>
                <div id="status"></div>
                { link }
                <h1>{registry.name}</h1>
                { html }
            </div>
        </div>
        <script type="text/javascript" charset="utf-8" src="/assets/functions.js"></script>  
    </body>
</html>
    response.setContentType("text/html")
    response.getWriter.println("<!DOCTYPE html>")
    response.getWriter.println(page)
    request.setHandled(true)
  }
}

