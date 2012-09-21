package chalkproxy

import java.net.URI
import de.roderick.weberknecht.WebSocketConnection
import de.roderick.weberknecht.WebSocketEventHandler
import de.roderick.weberknecht.WebSocketMessage
import org.json.JSONObject
import org.json.JSONArray

/**
 * Demonstrates many (6) clients registering and unregistering
 */
object Demo {
  def main(args:Array[String]) {
    val hostname = if (args.length > 0) args(0) else "localhost"
    val port = if (args.length > 1) args(1).toInt else  4000
    val mode = if (args.length > 2) args(2) else "start-stop"
    val demo = new Demo(hostname, port, "localhost")
    demo.go(mode)
  }
  def start(port:Int, mode:String) {
    val demo = new Demo("localhost", port, "localhost")
    new Thread(new Runnable() { def run() {
      demo.go(mode)
    }}, "Demo runner").start()
  }
  val modes = List("start-all", "start-one", "start-stop-all", "update", "start-stop")
}
class Demo(chalkHostname:String, chalkPort:Int, localHostname:String) {
  
  class SampleServer(port:Int, name:String, group:String) {
    val props = List(
          ChalkProperty("branch", if ((name.hashCode % 2) == 1) "master" else "release-1.0", Some("http://git/branches/master")),
          ChalkProperty("commit", "fjfiwj5osj32"),
          ChalkProperty("pwd", "/home/thomas/code/server/main"), 
          ChalkProperty("&b=1", "url escaping test"),
          ChalkProperty("started", "20Apr2012 14:54"),
          ChalkProperty("Status", "starting"),
          ChalkProperty("headers", "here", Some("headers")),
          ChalkProperty("pwd", "/home/thomas/code/server/main"), 
          ChalkProperty("user", "t intentional spaces"),
          ChalkProperty("started", "20Apr2012 14:54"),
          ChalkProperty("pid", "2543"))
    val chalkProxy = new ChalkProxy(
          chalkHostname, chalkPort, localHostname, port, name, group,
          ChalkIcon("Launch 2", "/assets/blowfish.png", "/go.jnlp") :: 
          ChalkIcon("ws", "/assets/blowfish.png", "/go.jnlp") :: 
          ChalkIcon("p", "", "/go.jnlp") :: Nil,
          props
    )
    val webServer = new TestServer(name, port)
    def start() {
        webServer.start()
    	chalkProxy.start()
    }
    def isStarted() = chalkProxy.isStarted()
    def update(prop:ChalkProperty) {
      chalkProxy.update(prop)
    }
    def stop() {
      webServer.stop()
      chalkProxy.stop()
    }
  } 
  val foo = List("UAT" -> List("Red u@ thomas roo", "House u also quite long"), "Prod" -> List("James p", "Cloud p"), "Dev" -> List("D1", "D9"))
  val baseServerPort = 5000
  val samples = foo.flatMap { case (group, names) => { names.map { name => (name,group) } } }.zipWithIndex.map { case ((name,group), index) => {
    new SampleServer(baseServerPort+index, name, group)
  } }.toArray
  val colors = Array("Red", "Amber", "Green")
  val random = new java.util.Random(0)
  def go(mode:String) {
    mode match {
      case "start-all" => { samples.foreach(_.start) }
      case "start-one" => samples.head.start
      case "start-stop-all" => { samples.foreach(_.start); samples.foreach(_.stop) }
      case "start-stop-repeat" => {
        while (true) {
          samples.foreach(_.start);
          Thread.sleep(3000)
          samples.foreach(_.stop)
          Thread.sleep(4000)
        }
      }
      case "slow-start" => { samples.foreach{ s => s.start; Thread.sleep(5*60*1000) } }
      case "one-busy" => {
        samples.foreach(_.start);
	    val register = samples(4)
	    while (true) {
	      Thread.sleep(500)
	      register.stop()
	      Thread.sleep(500)
	      register.start()
	      //Thread.sleep(3000)
	    }
      }
      case "update" => {
        samples.foreach(_.start);
	    while (true) {
	      val next = random.nextInt(samples.size)
	      val register = samples(next)
	      register.update(ChalkProperty("Status", colors(random.nextInt(colors.size))))
	      Thread.sleep(500)
	    }
      }
      case "start-stop" => {
	    while (true) {
	      //val next = random.nextInt(samples.size)
	      //val register = samples(next)
	      samples.foreach { register => {
	        if (register.isStarted()) {
	          register.stop()
	        } else {
	          register.start()
	        }
	        Thread.sleep(1000)
	      } }
	    }
      }
    }
  }
}