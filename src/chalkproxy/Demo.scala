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
    val hostname = args(0)
    val port = args(1).toInt
    start(hostname, port)
  }
  def start(hostname:String, port:Int) {
    new Thread(new Runnable() { def run() { go(hostname, port)}}, "Demo runner").start()
  }
  private def go(hostname:String, port:Int) {
    val foo = List("UAT" -> List("Red", "House"), "Prod" -> List("James", "Cloud"), "Dev" -> List("D1", "D9"))
    val registers = foo.flatMap { case (group, names) => {
      val props = List(
          ChalkProperty("branch", "master", Some("http://git/branches/master")),
          ChalkProperty("users", "2"),
          ChalkProperty("pwd", "/home/thomas/code"), 
          ChalkProperty("user", "thomas"))
      names.map { name => {
        new ChalkProxy(hostname, port, "alt", 8080, name, group, "launch.jnlp", ChalkIcon("Launch", "/assets/blowfish.png", "/go.jnlp") :: Nil, props)
      } }
    }}.toArray
    val random = new java.util.Random(0)
    while (true) {
      val next = random.nextInt(registers.size)
      val register = registers(next)
      if (register.isStarted()) {
        register.stop()
      } else {
        register.start()
      }
      Thread.sleep(3000)
    }
  }
}