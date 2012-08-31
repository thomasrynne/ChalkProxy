package chalkproxy

import java.io._
import java.util._
import org.eclipse.jetty.server.Server
import javax.servlet.http._
import org.eclipse.jetty.server.nio.SelectChannelConnector
import org.eclipse.jetty.server.handler.ResourceHandler
import org.eclipse.jetty.server.handler.HandlerList
import org.eclipse.jetty.server.handler.DefaultHandler
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.servlet.ServletHolder
import org.eclipse.jetty.util.log.Logger;

/**
 * This is a simple test webserver which has two test pages:
 *  -headers which lists the client headers
 *  -form/page which has a form which makes a post which then redirects
 */
object TestServer {
  def main(args:Array[String]) = {
    new TestServer("Test server", 8090).start()
  }
}
class TestServer(name:String, port:Int) {
	val server = new Server();
    val connector = new SelectChannelConnector();
    connector.setPort(port);
    server.addConnector(connector);
 
    val resource_handler = new ResourceHandler()
    resource_handler.setDirectoriesListed(true)
    resource_handler.setWelcomeFiles(Array("index.html"))
    resource_handler.setResourceBase(".") 
    
    val context = new ServletContextHandler(ServletContextHandler.SESSIONS);
    context.setContextPath("/");
    
    context.addServlet(new ServletHolder(new RootServlet()), "/")
    context.addServlet(new ServletHolder(new HeadersServlet()), "/headers")
    context.addServlet(new ServletHolder(new PostServlet()), "/form/*")
    //server.addHandler(root)

    val handlers = new HandlerList()
    handlers.setHandlers(Array(context, resource_handler, new DefaultHandler()))
    server.setHandler(handlers)

    def start() {
    	server.start()
    }
    def stop() {
      server.stop()
    }

  class RootServlet extends HttpServlet {
  override def doGet(request:HttpServletRequest, response:HttpServletResponse) {
    if (!request.getRequestURI.equals("/")) {
      response.setStatus(404)
      response.getWriter.println("404 expected /")
    } else {
      response.getWriter.println(
      <html><head></head><body>
        <h1>Test webserver: {name}</h1>
        <a href="headers">view client headers</a> <br/>
        <a href="form/page">test form with post</a> <br/>
      </body></html>
      )
    }
  }  
  }
}
class HeadersServlet() extends HttpServlet {
  override def doGet(request:HttpServletRequest, response:HttpServletResponse) {
    response.getWriter.println("Request headers...")
    val headers = request.getHeaderNames
    while (headers.hasMoreElements) {
      val header = headers.nextElement
      val value = request.getHeader(header.asInstanceOf[String])
      response.getWriter.println( header + ": " + value )
    }
  }
 }
class PostServlet extends HttpServlet {
  var value = "abc"
  override def doGet(request:HttpServletRequest, response:HttpServletResponse) {
    if (!request.getRequestURI.equals("/form/page")) {
      response.setStatus(404)
      response.getWriter.println("404 did not expect " + request.getRequestURI)
    } else {
      val page = 
        <html><body>
        <p>{value}</p>
        <form action="formpost" method="post">
         <input type="text" name="formfield" value={value}/>
         <input type="submit" value="post"/>
        </form></body></html>
      response.getWriter.println( page )
    }
  }  
  override def doPost(request:HttpServletRequest, response:HttpServletResponse) {
    if (!request.getRequestURI.equals("/form/formpost")) {
      response.setStatus(404)
      response.getWriter.println("404 did not expect " + request.getRequestURI)
    } else {
      value = request.getParameter("formfield")
      response.sendRedirect("page")
    }
  }
}
class NullLogger extends Logger {
  def debug(t:Throwable) { }
  def debug(arg:String, arg1:Object*) { }
  def debug(arg:String, t:Throwable) { }
  def getLogger(arg:String) = { this }
  def getName() = { "NullLogger" }
  def ignore(arg:Throwable) { }
  def info(t:Throwable) { }
  def info(arg:String, args:Object*) { }
  def info(arg:String, t:Throwable) { }
  def isDebugEnabled() = { false }
  def setDebugEnabled(e:Boolean) { }
  def warn(t:Throwable) { println("Jetty warn"); t.printStackTrace() }
  def warn(arg:String, arg1:Object*) { println("Jetty warn:" + arg + arg1.toList) }
  def warn(arg:String, t:Throwable) { println("Jetty warn: " + arg); t.printStackTrace() }
}
