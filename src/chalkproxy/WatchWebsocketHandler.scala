package chalkproxy 

import java.io.IOException
import org.json.JSONObject
import java.util.Set
import java.util.concurrent.CopyOnWriteArraySet
import java.net.URLDecoder
import javax.servlet.http.HttpServletRequest
import org.eclipse.jetty.websocket.WebSocket
import org.eclipse.jetty.websocket.WebSocketHandler;
import org.eclipse.jetty.websocket.WebSocket.Connection
import org.json.JSONObject
import org.json.JSONTokener

class WatchWebsocketHandler(registry:Registry) extends WebSocketHandler {

	def doWebSocketConnect(request:HttpServletRequest, protocol:String) = {
      new WatcherWebSocket()
	}

	class WatcherWebSocket() extends WebSocket.OnTextMessage {
		var connection : Connection = null
		var watcher:Option[Watcher] = None

		def onOpen(connection:Connection) {
		  this.connection = connection;
		}
		
		private def sendInit(view:View, state:Int) {
          val (ii, currentState) = registry.instances
          val json = new JSONObject()
  		  json.put("messageType", "init")
          json.put("state", currentState)
          if (state != currentState) {
            val html = Page.listing(ii, view)
        	json.put("html", html)
		  }
		  connection.sendMessage(json.toString)
		}
		
		private def createWatcher(viewX:View) = new Watcher {
		  def view = viewX
		  def notify(json:JSONObject) {
		    if (connection.isOpen()) {
		      try {
		        connection.sendMessage(json.toString)
		      } catch {
		        case e:Exception => {
		          println("Failed while sending notification to websocket")
		          e.printStackTrace()
		        }
		      }
		    }
		  }
		  def isActive = connection.isOpen
		}

		def onMessage(data:String) {
		  val json = new JSONObject(new JSONTokener(data))
		  val serverStartId = json.getInt("serverStartId")
		  if (serverStartId != registry.serverStartId) {
		    connection.sendMessage(registry.refreshJson.toString)
		  } else {
  		    val state = json.getInt("state")
		    val viewPath = json.getString("path")
		    val view = createView(viewPath)
		    watcher = Some(createWatcher(view))
		    registry.addWatcher(watcher.get)
		    sendInit(view, state)
		  }
		}
		
		private def createView(path:String) = {
	      val slashes = path.split("/")
	      val groupBy = URLDecoder.decode(slashes(1))
	      val filter = slashes(2).split(":").map(URLDecoder.decode(_)).mkString(":")
	      val design = slashes(3)
	      val showDisabled = slashes(4)
	      View.create(groupBy, filter, design, showDisabled)
		}
		
		def onClose(closeCode:Int, message:String) {
			watcher.foreach(registry.removeWatcher(_))
		}
	}
}