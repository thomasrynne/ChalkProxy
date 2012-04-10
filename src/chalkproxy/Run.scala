package chalkproxy

import java.io._
import java.util._
import javax.servlet.http._
import org.apache.commons.cli._
import scala.Option
import scala.Some
import scala.None
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.server.nio.SelectChannelConnector
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.ServletHolder
import org.eclipse.jetty.server.handler.HandlerList
import org.eclipse.jetty.server.handler.DefaultHandler
import org.eclipse.jetty.server.handler.ContextHandlerCollection
import org.eclipse.jetty.server.Handler
import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.handler.AbstractHandler
import org.eclipse.jetty.servlet.ServletHandler
import org.eclipse.jetty.server.handler.ResourceHandler
import java.lang.management.ManagementFactory

/**
 * This is the entry point to ChalkProxy.
 * This class reads command line and chalkproxy.conf parameters
 * and starts the server.
 */
object Run {
  def main(args:Array[String]) = {
    
    val options = new Options
    options.addOption("p", "port", true, "the http port to listen on (default 8080)")    
    options.addOption("f", "file", true, "read settings from a properties file")
    options.addOption("s", "flash", true, "the port for the flash socket server (default 8430)")
    options.addOption("d", "demo", false, "run in demo mode with built in servers")
    options.addOption("n", "name", true, "The name for this instance of ChalkProxy")
    options.addOption("h", "help", false, "print this help message")
    
    val commandLine = new BasicParser().parse(options, args)
    var propertiesFile = new File("chalkproxy.conf")
    if (commandLine.hasOption("file")) {
      propertiesFile = new File(commandLine.getOptionValue("file"))
      if (!propertiesFile.exists()) {
        println("Not reading properties file. File not found: " + propertiesFile)
      }
    }
    val properties = new Properties()
    if (propertiesFile.exists()) {
      properties.load(new FileInputStream(propertiesFile));
    }
    commandLine.getOptions.foreach( (option) => {
      if (option.hasArg) {
        val value = commandLine.getOptionValue(option.getLongOpt)
        properties.setProperty(option.getLongOpt, value)
      } else {
        if (commandLine.hasOption(option.getLongOpt)) {
          properties.setProperty(option.getLongOpt, "true")
        }
      } 
    })
    if (commandLine.hasOption("help")) {
        new HelpFormatter().printHelp(
          "chalkproxy",
          "",
          options,
          "   if present properties are read from chalkproxy.conf\n" +
          "   this should be a properties file with property names matching\n" +
          "   the long command line names",
          false)
      } else {
        writePID()
        val httpPort = Integer.parseInt(properties.getProperty("port", "8080"))
        val flashPort = Integer.parseInt(properties.getProperty("flash", "8430"))
        val name = properties.getProperty("name", "Chalk Proxy")
        println("Running ChalkProxy")
        try {
	      val registry = new Registry()
	      startWebserver(registry, name, httpPort)
	      startFlashSocketServer(flashPort)
	      startCleanupTimer(registry)
	      if (properties.containsKey("demo") && properties.get("demo").toString.equalsIgnoreCase("true")) {
	        Demo.start("localhost", httpPort)
	        println("Starting in demo mode")
	      }
        } catch {
          case e:java.net.BindException => println("port " + httpPort + " already in use")
        }
      }
  }
  
  private def writePID() {
	val jvmName = ManagementFactory.getRuntimeMXBean().getName()
	val at = jvmName.indexOf('@')
	val pid = jvmName.substring(0, at)
    val writer = new FileWriter(new File("pid.txt"))
    writer.write(pid)
    writer.write("\n")
    writer.close()
  }
  
  private def startCleanupTimer(registry:Registry) {
    val timer = new Timer(true)
	val ONE_HOUR = 60*60*1000
	timer.scheduleAtFixedRate(new TimerTask() { def run() { registry.cleanup()} }, ONE_HOUR, ONE_HOUR)
  }
  
  private def startFlashSocketServer(port:Int) {
    new FlashSocketServer(port).start()
  }
  
  private def startWebserver(registry:Registry, name:String, port:Int) {

	val server = new Server(port)
    
	val rootHandler = new RootHandler(registry, name)
	val partialHandler = new PartialHandler(registry)
	val listHandler = new ListHandler(registry)
	val proxy = new ProxyHandler(registry)
    val watchWebSocketHandler = new WatchWebsocketHandler(registry)
    val registerWebSocketHandler = new RegisterWebSocketHandler(registry)
    val resourceHandler = new ResourceHandler()
	resourceHandler.setResourceBase(".")
	
	server.setHandler(new AbstractHandler() {
		override def handle(target:String, request:Request, httpRequest:HttpServletRequest, response:HttpServletResponse) {
		  val handler = target match {
  		    case "/" => rootHandler
  		    case "/partial" => partialHandler
  		    case "/list" => listHandler
		    case "/register" => registerWebSocketHandler
		    case "/watch" => watchWebSocketHandler
		    case _ => if (target.startsWith("/assets")) resourceHandler else proxy
		  }
		  handler.handle(target, request, httpRequest, response)
		}
      })
    println("Starting web server on port " + port)
    server.start
  }
}