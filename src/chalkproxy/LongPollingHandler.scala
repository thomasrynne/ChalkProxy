package chalkproxy

import javax.servlet.http._
import scala.collection.mutable.LinkedList
import scala.xml.Node
import org.eclipse.jetty.server.handler.AbstractHandler
import org.eclipse.jetty.server.Request
import org.json.{JSONObject, JSONArray}
import scala.collection.JavaConversions._
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class PollSessions(registry:Registry) {
  class Session(val view:View) {
    private val queue = new java.util.concurrent.LinkedBlockingQueue[JSONObject]()
    var lastUsed = new AtomicReference(System.currentTimeMillis)
    val inUse = new AtomicBoolean(false)
    val watcher = new Watcher() {
      def notify(json:JSONObject) { queue.add(json) }
      def view:View = Session.this.view
      def isActive = inUse.get
    }
    registry.addWatcher(watcher)
    def isActive = inUse.get || lastUsed.get < (System.currentTimeMillis() - (10*60*1000))
    def next:List[JSONObject] = {
      lastUsed.set(System.currentTimeMillis())
      inUse.set(true)
      try {
	      val first = queue.take()
	      val rest = new java.util.LinkedList[JSONObject]()
	      queue.drainTo(rest)
	      first :: rest.toList
      } finally {
        inUse.set(false)
      }
    }
    def unregister {
      registry.removeWatcher(watcher)
    }
  }
  val sessions = new java.util.concurrent.ConcurrentHashMap[String,Session]
  def cleanup = {
    val timeout = System.currentTimeMillis() - (5*60*1000)
    for ((id, session) <- sessions.iterator) {
      if (!session.isActive) {
        session.unregister
        sessions.remove(id)
      }
    }
  }
  def awaitNextUpdates(id:String, view:View, state:Int) = {
    var session = sessions.get(id)
    if (session == null) {
      session = new Session(view)
      sessions.put(id, session)
      val (ii, currentState) = registry.instances
      val json = new JSONObject()
  	  json.put("messageType", "init")
      json.put("state", currentState)
      if (state != currentState) {
        val html = registry.page.listing(ii, view)
        json.put("html", html)
      }
      List(json)
    } else {
      session.next
    }
  }
}

class LongPollingHandler(registry:Registry) extends AbstractHandler {
  val pollSessions = new PollSessions(registry)
  def cleanup() = pollSessions.cleanup
  override def handle(target:String, request:Request, httpRequest:HttpServletRequest, response:HttpServletResponse) {
    val json = if (request.getParameter("serverStartId").toInt != registry.serverStartId) {
      new JSONArray(Array(registry.refreshJson))
    } else {
      val browserState = request.getParameter("state").toInt
      val view = View.create(request.getParameter("groupBy"), request.getParameter("filter"), request.getParameter("design"), request.getParameter("showDisconnected"))
      val sessionId = request.getParameter("sessionid")
      val updates = pollSessions.awaitNextUpdates(sessionId, view, browserState)
      new JSONArray(updates.toArray)
    }
    response.setContentType("text/json")
    response.setHeader("Cache-control", "no-cache")
    response.getWriter.println(json)
    request.setHandled(true)
  }
}