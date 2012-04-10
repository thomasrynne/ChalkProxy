package chalkproxy

import org.json.JSONObject
import org.json.JSONArray
import scala.concurrent.SyncVar
import scala.xml.NodeSeq
import scala.actors.threadpool.locks.ReentrantReadWriteLock

class RegisterSession

/* Case classes which form part of the api to Registry */
case class Icon(text:String, image:String, url:String)
case class Prop(name:String, value:String, url:Option[String])
case class Instance(name:String, group:String, host:String, port:Int, icons:List[Icon], props:List[Prop]) {
  def prefix = name.replaceAll(" ", "_")
  def key = prefix.toLowerCase()
  def groupKey = group.replaceAll(" ", "_").toLowerCase()
}
case class InstanceSnapshot(instance:Instance, isClosed:Boolean)
case class RegistrationToken(id:Int)
trait Watcher {
  def notify(html:String)
}

/**
 * Represents the state of the chalk board
 */
class Registry {

  val readWriteLock = new ReentrantReadWriteLock
  var registerSessions = Map[String,Instance]()
  var closed = Map[String,java.util.Date]()
  val watchers = new java.util.concurrent.ConcurrentLinkedQueue[Watcher]()
  
  def lookup(name:String) = {
    readWriteLock.readLock.lock()
    val instance = registerSessions.get(name.toLowerCase())
    readWriteLock.readLock.unlock()
    instance
  }
  
  def cleanup() {
    readWriteLock.writeLock().lock()
    val cutOff = System.currentTimeMillis() - (28*60*60*1000)
    val expired = closed.toList.filter { case (key,date) => date.getTime < cutOff}
    if (expired.nonEmpty) {
      expired.foreach { case (key, _) => {
        closed = closed - key
        registerSessions = registerSessions - key
      } }
      update()
    }
    readWriteLock.writeLock().unlock()
  }
  
  def register(instance:Instance) {
    readWriteLock.writeLock().lock()
    registerSessions.get(instance.key) match {
      case Some(instance) => {
      	if(!closed.contains(instance.key)) {
      	  throw new Exception(instance.key + " is already registered by " + instance)
      	}
      }
      case _ =>
    }
    val isNew = !closed.contains(instance.key)
    closed = closed - instance.key
    registerSessions = registerSessions + (instance.key -> instance)
    if (isNew) {
    	update(added=List(Page.instanceId(instance.key)))
    } else {
    	update(enabled=List(Page.instanceId(instance.key)))      
    }
    readWriteLock.writeLock().unlock()
  }

  def unregister(instance:Instance) {
    readWriteLock.writeLock().lock()
    closed = closed + (instance.key -> new java.util.Date())
    update(disabled=List(Page.instanceId(instance.key)))
    readWriteLock.writeLock().unlock()
  }
  
  private def update(enabled:List[String]=Nil, disabled:List[String]=Nil, added:List[String]=Nil) {
    import scala.collection.JavaConversions
    val html = Page.listing(instances)
    val json = new JSONObject()
    json.put("html", html.toString)
    json.put("enable", new JSONArray(JavaConversions.asJavaCollection(enabled)))
    json.put("disable", new JSONArray(JavaConversions.asJavaCollection(disabled)))
    json.put("add", new JSONArray(JavaConversions.asJavaCollection(added)))
    broadcast(json.toString)
  }
  private def broadcast(msg:String) {
    for (w <- watchers.toArray(Array[Watcher]())) {
      w.notify(msg)
    }
  }
  
  def instances:List[InstanceSnapshot] = {
    readWriteLock.readLock().lock()
    val rs = registerSessions
    val c = closed
    readWriteLock.readLock().unlock()
    rs.values.toList.map { instance => InstanceSnapshot(instance, closed.contains(instance.key))}
  }
  
  def fullPage = Page.listing(instances)
  
  def addWatcher(watcher:Watcher) {
    watchers.add(watcher)
  }
  
  def removeWatcher(watcher:Watcher) {
    watchers.remove(watcher)
  }
}