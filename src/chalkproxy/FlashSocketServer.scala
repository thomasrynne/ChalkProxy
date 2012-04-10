package chalkproxy
import java.net.ServerSocket

/**
 * As only Chrome supports websockets so flash plugin is used
 * to provide websocket support for other browsers
 *  (https://github.com/gimite/web-socket-js)
 * However, Flash requires a policy file served using a flash socket server.
 * This class provides a minimal socket server which just provides a policy
 */
class FlashSocketServer(port:Int) {
  
  private val crossDomainXml = """<?xml version="1.0" ?>
<cross-domain-policy>
<allow-access-from domain="*" to-ports="*"/>
</cross-domain-policy>"""
  
  def start() {
    val thread = new Thread(new Runnable() { def run() { go() }}, "FlashSocketServer")
    thread.start()
  }
  
  private def go() {
    val serverSocket = new ServerSocket(port)
    println("starting flash socket server on port " + port)
    while (true) {
      val socket = serverSocket.accept()
      while(socket.getInputStream().available()==0) Thread.sleep(0)
      //socket.getInputStream().read()
      socket.getOutputStream().write(crossDomainXml.getBytes())
      socket.close()
    }
  }
  

}