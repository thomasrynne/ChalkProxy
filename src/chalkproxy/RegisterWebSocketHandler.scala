package chalkproxy 

import java.io.IOException
import java.util.concurrent.CopyOnWriteArraySet
import javax.servlet.http.HttpServletRequest
import org.eclipse.jetty.websocket.WebSocket
import org.eclipse.jetty.websocket.WebSocketHandler
import org.eclipse.jetty.websocket.WebSocket.Connection
import org.eclipse.jetty.server.Request
import javax.servlet.http.HttpServletResponse
import org.json.JSONObject
import org.json.JSONTokener
import org.json.JSONArray

/**
 * Handles connections from servers which want to be registered on the whiteboard
 */
class RegisterWebSocketHandler(registry:Registry) extends WebSocketHandler {
  
	def doWebSocketConnect(request:HttpServletRequest, protocol:String) = {
		new RegisterWebSocket();
	}

	class RegisterWebSocket extends WebSocket.OnTextMessage {

		var instance:Instance = null

		def onOpen(connection:Connection) {
		}

		def onMessage(data:String) {
		  val json = new JSONObject(new JSONTokener(data))
		  val name = json.getString("name")
		  val group = json.getString("group")
		  val hostname = json.getString("hostname")
		  val port = json.getInt("port")
		  val icons = {
		    val iconsJson = if (json.has("icons")) json.getJSONArray("icons") else new JSONArray()
		    for (i <- 0 until iconsJson.length()) yield {
		      val iconJson = iconsJson.getJSONObject(i)
		      Icon(iconJson.getString("text"), iconJson.getString("image"), iconJson.getString("url"))
		    }
		  }.toList
		  val props = {
		    val jsonProps = if (json.has("props")) json.getJSONArray("props") else new JSONArray()
		    for (i <- 0 until jsonProps.length()) yield {
		      val propJson = jsonProps.getJSONObject(i)
		      Prop(propJson.getString("name"), propJson.getString("value"), if (propJson.has("url")) Some(propJson.getString("url")) else None)
		    }
		  }.toList
		  instance = Instance(name, group, hostname, port, icons, props)
		  registry.register(instance)
		}

		def onClose(closeCode:Int, message:String) {
		  if (instance != null) {
		    registry.unregister(instance)
		  }
		}
	}
}