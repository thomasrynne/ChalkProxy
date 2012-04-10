package chalkproxy 

import java.io.IOException
import java.util.Set
import java.util.concurrent.CopyOnWriteArraySet
import javax.servlet.http.HttpServletRequest
import org.eclipse.jetty.websocket.WebSocket
import org.eclipse.jetty.websocket.WebSocketHandler;
import org.eclipse.jetty.websocket.WebSocket.Connection

class WatchWebsocketHandler(registry:Registry) extends WebSocketHandler {

	def doWebSocketConnect(request:HttpServletRequest, protocol:String) = {
	  val group = request.getPathInfo().substring(request.getPathInfo().indexOf('/', 1)+1)
	  val groupFilter = group match { 
	    case "all" => None
	    case v => Some(v.split(":").toList)
	  }
      new WatcherWebSocket(groupFilter)
	}

	class WatcherWebSocket(groupFilter:Option[List[String]]) extends WebSocket.OnTextMessage {
		var connection : Connection = null
		val watcher = new Watcher {
		  def groupFilter = WatcherWebSocket.this.groupFilter
		  def notify(html:String) {
		    connection.sendMessage(html)
		  }
		}

		def onOpen(connection:Connection) {
			this.connection = connection;
			registry.addWatcher(watcher)
		}

		def onMessage(data:String) {
		}
		
		def onClose(closeCode:Int, message:String) {
			registry.removeWatcher(watcher)
		}
	}
}