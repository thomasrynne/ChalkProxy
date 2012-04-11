package chalkproxy

import java.net.URI
import de.roderick.weberknecht.WebSocketConnection
import de.roderick.weberknecht.WebSocketEventHandler
import de.roderick.weberknecht.WebSocketMessage
import org.json.JSONObject
import org.json.JSONArray
      import scala.collection.JavaConversions
import java.net.Socket
/**
 * The server registration code to be used by servers
 * Creates a socket or websocket connection, sends the details as json, leaves the socket / websocket open
 */
case class ChalkIcon(text:String, image:String, url:String)
case class ChalkProperty(name:String, value:String, url:Option[String]=None)
class ChalkProxy(wpHost:String, wpPort:Int, serverHost:String, serverPort:Int, val name:String, val group:String,
    icons:List[ChalkIcon],
    properties:List[ChalkProperty]) {
  private def createInstanceJson() = {
	def iconToJson(icon:ChalkIcon) = {
      val json = new JSONObject()
      json.put("text", icon.text)
      json.put("image", icon.image)
      json.put("url", icon.url)
      json
    }
	def propertyToJson(attribute:ChalkProperty) = {
      val json = new JSONObject()
      json.put("name", attribute.name)
      json.put("value", attribute.value)
      attribute.url.foreach { url => {
    	  json.put("url", url)
      }}
      json
    }
    val json = new JSONObject()
    json.put("name", name)
    json.put("group", group)
    json.put("hostname", serverHost)
    json.put("port", serverPort.toString)
    json.put("icons", {
      new JSONArray(JavaConversions.asJavaCollection(icons.map { icon => iconToJson(icon) }))
    })
    json.put("props", {
      new JSONArray(JavaConversions.asJavaCollection(properties.map { property => propertyToJson(property) }))
    })
    json.toString
  }
  
  val json = createInstanceJson()
  
  trait ChalkConnection {
    def stop()
  }
  class ChalkSocketConnection extends ChalkConnection {
    val socket = new Socket(wpHost, wpPort)
    socket.getOutputStream().write((json + "\n").getBytes("utf8"))
    def stop() {
      socket.close()
    }
  }
  class ChalkWebSocketConnection extends ChalkConnection {
    val websocket = new WebSocketConnection(new URI("ws://" + wpHost + ":" + wpPort + "/register"))
    websocket.setEventHandler(new WebSocketEventHandler() {
      def onOpen() { }
      def onMessage(message:WebSocketMessage) { }
      def onClose() { }
    })
	websocket.connect()
	websocket.send(json.toString())
    def stop() {
      websocket.close()
    }
  }
  var connection:Option[ChalkConnection] = None
  def start() {
    connection match {
      case Some(_) => throw new Exception("Already started")
      case None => { connection=Some(new ChalkSocketConnection) }
    }
  }
  def isStarted() = {
    connection.isDefined
  }
  def stop() {
    connection match {
      case None => throw new Exception("Not started")
      case Some(c) => { c.stop; connection = None }
    }
  }
}