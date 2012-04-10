package chalkproxy

import java.net.URI
import de.roderick.weberknecht.WebSocketConnection
import de.roderick.weberknecht.WebSocketEventHandler
import de.roderick.weberknecht.WebSocketMessage
import org.json.JSONObject
import org.json.JSONArray
      import scala.collection.JavaConversions

/**
 * The server registration code to be used by servers
 * Creates a websocket connection, sends the details as json, leaves the websocket open
 */
case class ChalkIcon(text:String, image:String, url:String)
case class ChalkProperty(name:String, value:String, url:Option[String]=None)
class ChalkProxy(wpHost:String, wpPort:Int, serverHost:String, serverPort:Int, val name:String, val group:String,
    icons:List[ChalkIcon],
    properties:List[ChalkProperty]) {
  class Connection {
    val websocket = new WebSocketConnection(new URI("ws://" + wpHost + ":" + wpPort + "/register"))
    websocket.setEventHandler(new WebSocketEventHandler() {
      def onOpen() { }
      def onMessage(message:WebSocketMessage) { }
      def onClose() { }
    })
	websocket.connect()
	def toJson(icon:ChalkIcon) = {
      val json = new JSONObject()
      json.put("text", icon.text)
      json.put("image", icon.image)
      json.put("url", icon.url)
      json
    }
	def toJson(attribute:ChalkProperty) = {
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
      new JSONArray(JavaConversions.asJavaCollection(icons.map { icon => toJson(icon) }))
    })
    json.put("props", {
      new JSONArray(JavaConversions.asJavaCollection(properties.map { property => toJson(property) }))
    })
	websocket.send(json.toString())
    def stop() {
      websocket.close()
    }
  }
  var connection:Option[Connection] = None
  def start() {
    connection match {
      case Some(_) => throw new Exception("Already started")
      case None => { connection=Some(new Connection) }
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