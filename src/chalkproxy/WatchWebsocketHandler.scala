package chalkproxy 

import java.io.IOException
import java.util.Set
import java.util.concurrent.CopyOnWriteArraySet
import java.net.URLDecoder
import javax.servlet.http.HttpServletRequest
import org.eclipse.jetty.websocket.WebSocket
import org.eclipse.jetty.websocket.WebSocketHandler;
import org.eclipse.jetty.websocket.WebSocket.Connection

class WatchWebsocketHandler(registry:Registry) extends WebSocketHandler {

	def doWebSocketConnect(request:HttpServletRequest, protocol:String) = {
	  val slashes = request.getPathInfo().split("/")
	  val groupBy = URLDecoder.decode(slashes(2))
	  val filter = slashes(3).split(":").map(URLDecoder.decode(_)).mkString(":")
	  val design = slashes(4)
	  val view = View.create(groupBy, filter, design)
      new WatcherWebSocket(view)
	}

	class WatcherWebSocket(view:View) extends WebSocket.OnTextMessage {
		var connection : Connection = null
		val watcher = new Watcher {
		  def view = WatcherWebSocket.this.view
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