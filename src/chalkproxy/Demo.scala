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
    val demo = new Demo(hostname, port, "localhost")
    demo.go()
  }
  def start(port:Int) {
    val demo = new Demo("localhost", port, "localhost")
    new Thread(new Runnable() { def run() {
      demo.go()
    }}, "Demo runner").start()
  }
}
class Demo(chalkHostname:String, chalkPort:Int, localHostname:String) {
  
  class SampleServer(port:Int, name:String, group:String) {
    val props = List(
          ChalkProperty("branch", "master", Some("http://git/branches/master")),
          ChalkProperty("commit", "fjfiwj5osj32"),
          ChalkProperty("pwd", "/home/thomas/code/server/main"), 
          ChalkProperty("user", "t homas rynne"),
          ChalkProperty("started", "20Apr2012 14:54"),
          ChalkProperty("Status", "starting"),
          ChalkProperty("pwd", "/home/thomas/code/server/main"), 
          ChalkProperty("user", "t homas rynne"),
          ChalkProperty("started", "20Apr2012 14:54"),
          ChalkProperty("pid", "2543"))
    val chalkProxy = new ChalkProxy(
          chalkHostname, chalkPort, localHostname, port, name, group,
          ChalkIcon("Launch 2", "/assets/blowfish.png", "/go.jnlp") :: Nil,
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
  def go() {
    val foo = List("UAT" -> List("Red u@ thomas roo", "House u also quite long"), "Prod" -> List("James p", "Cloud p"), "Dev" -> List("D1", "D9"))
    val baseServerPort = 5000
    val samples = foo.flatMap { case (group, names) => { names.map { name => (name,group) } } }.zipWithIndex.map { case ((name,group), index) => {
      new SampleServer(baseServerPort+index, name, group)
    } }.toArray
    val startAndStop = true
    if (startAndStop) {
	    val random = new java.util.Random(0)
	    while (true) {
	      val next = random.nextInt(samples.size)
	      val register = samples(next)
	      if (register.isStarted()) {
	        if (random.nextBoolean()) {
	          register.update(ChalkProperty("Status", "started"))
	        } else {
	          register.update(ChalkProperty("Status", "Busy"))
	        }
	      } else {
	        register.start()
	      }
	      Thread.sleep(3000)
	    }
    } else {
      samples.foreach(_.start())
    }
  }
}