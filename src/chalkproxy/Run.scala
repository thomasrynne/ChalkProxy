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
import org.eclipse.jetty.util.log.AbstractLogger
import com.sun.org.apache.bcel.internal.util.ClassLoader

/**
 * This is the entry point to ChalkProxy.
 * This class reads command line and chalkproxy.conf parameters
 * and starts the server.
 */
object Run {
  def main(args:Array[String]) = {
    
    val options = new Options
    options.addOption("p", "http-port", true, "the http port to listen on (default 8080)")    
    options.addOption("f", "file", true, "read settings from a properties file")
    options.addOption("r", "registation-port", true, "the port for socket registrations (default 4000)")
    options.addOption("d", "demo-mode", true, "run in demo mode with built in servers (" + Demo.modes.mkString(",") + ")")
    options.addOption("n", "name", true, "The name for this instance of ChalkProxy")
    options.addOption("g", "group-by", true, "Property to group instances by on the home page (by default there is no grouping)")
    options.addOption("x", "filter", true, "A : separated list of the groups which should appear in the root page (if ommited all groups are shown)")
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
        val httpPort = Integer.parseInt(properties.getProperty("http-port", "8080"))
        val name = properties.getProperty("name", "Chalk Proxy")
        val registrationPort = Integer.parseInt(properties.getProperty("registration-port", "4000"))
        val groupBy = properties.getProperty("group-by", "None").trim match { case "None" => None; case x => Some(x) }
        val filter = {
         val value = properties.getProperty("filter", "").trim
         value match {
           case "" => None
           case _ => Some(value.split(":").toList)
         }
        }
        if (filter.isDefined && groupBy == None) {
          println("You can only specify a filter if group-by is specified.")
          println("The group by defines which properties the filter is applied to")
          System.exit(1)
        }
        val rootView = View(groupBy, filter)
        println("Running ChalkProxy")
        println("Default view: group by: " + rootView.groupBy + " filter: " + rootView.filter.getOrElse("None"))
        try {
          val assetsHandler = new EmbeddedAssetsHandler 
          val page = new Page(assetsHandler)
	      val registry = new Registry(name, page, rootView)
	      val serverProperties = ServerProperties(
	          httpPort, registrationPort, pid,
	          new File(".").getAbsolutePath(),
	          new java.util.Date().toString)
	      
	      start(registry, serverProperties)
	      if (properties.containsKey("demo-mode")) {
	        Demo.start(registrationPort, properties.getProperty("demo-mode"))
	        println("Starting in demo mode")
	      }
        } catch {
          case e:java.net.BindException => println("port " + httpPort + " already in use")
        }
      }
  }

  val pid = {
	val jvmName = ManagementFactory.getRuntimeMXBean().getName()
	val at = jvmName.indexOf('@')
	jvmName.substring(0, at)
  }
  
  private def writePID() {
    val writer = new FileWriter(new File("pid.txt"))
    writer.write(pid)
    writer.write("\n")
    writer.close()
  }
  
  private def startCleanupTimer(registry:Registry, longPollingHandler:LongPollingHandler) {
    val timer = new Timer(true)
	val ONE_HOUR = 60*60*1000
	val FIVE_MINUTES = 5*60*1000
	timer.scheduleAtFixedRate(
	    new TimerTask() { def run() { registry.cleanup()} }, ONE_HOUR, ONE_HOUR)
	timer.scheduleAtFixedRate(
	    new TimerTask() { def run() { longPollingHandler.cleanup()} }, FIVE_MINUTES, FIVE_MINUTES)
  }
  
  private def start(registry:Registry, properties:ServerProperties) {
	
    new SocketRegistrationServer(registry, properties.registrationPort).start()
    
    val longPollingHandler = new LongPollingHandler(registry)

    startCleanupTimer(registry, longPollingHandler)


    //silence info logs (only needed by the TestServer, but must be set early)
    org.eclipse.jetty.util.log.Log.setLog(new NullLogger())
    
	val server = new Server(properties.httpPort)
    val assetsHandler = registry.page.assetsHandler	
	val pageHandler = new PageHandler(registry, assetsHandler)
	val listHandler = new ListHandler(registry)
	val aboutHandler = new About(registry, properties)
	val proxy = new ProxyHandler(registry)
    val watchWebSocketHandler:AbstractHandler = new WatchWebsocketHandler(registry)
	
	server.setHandler(new AbstractHandler() {
		override def handle(target:String, request:Request, httpRequest:HttpServletRequest, response:HttpServletResponse) {
		  val handler:AbstractHandler = target match {
  		    case "/" => pageHandler
  		    case "/About" => aboutHandler
  		    case "/all" => pageHandler
  		    case "/list" => listHandler
  		    case "/poll" => longPollingHandler
		    case _ if (target.startsWith("/watch")) => watchWebSocketHandler
		    case _ => if (target.startsWith("/assets")) assetsHandler else proxy
		  }
		  handler.handle(target, request, httpRequest, response)
		}
      })
    println("Starting web server on port " + properties.httpPort)
    server.start
  }
}