package chalkproxy

import org.json.JSONObject
import org.json.JSONArray
import scala.concurrent.SyncVar
import scala.xml.NodeSeq
import scala.actors.threadpool.locks.ReentrantReadWriteLock
import java.net.URLEncoder

class RegisterSession

/* Case classes which form part of the api to Registry */
case class Icon(text:String, image:String, url:String)
case class Prop(name:String, value:String, url:Option[String])
case class Instance(name:String, group:String, host:String, port:Int, icons:List[Icon], props:List[Prop]) {
  lazy val prefix = {
    val builder = new StringBuilder()
    for (c <- name) {
      if (c.isLetterOrDigit || c == '@' || c == '-' || c == '.') {
        builder.append(c)
      } else {
        builder.append('_')
      }
    }
    builder.toString
  }
  lazy val key = prefix.toLowerCase()
  def valueFor(name:String) = {
    name match {
      case "group" => group
      case _ => props.find(_.name.equalsIgnoreCase(name)).map(_.value).getOrElse("Undefined")
    }
  }
  def groupKey = group.replaceAll(" ", "_").toLowerCase()
}
case class InstanceSnapshot(instance:Instance, isClosed:Boolean) {
  def propNames:Iterable[String] = instance.props.map(_.name)
}
case class RegistrationToken(id:Int)
case class View(groupBy:String, filter:Option[List[String]], showLinks:Boolean=false) {
  def design = copy(showLinks=true)
  def hide = copy(showLinks=false)
  def by(name:String) = copy(groupBy=name)
  def href = "/?"+ params
  def params = {
    (List("groupBy="+URLEncoder.encode(groupBy)) ::: filter.map(v => "filter="+v).toList :::
    (if(showLinks) List("design=show") else Nil)).mkString("&")
  }
  def asPath = {
    "/"+URLEncoder.encode(groupBy) + "/" + {
      filter match {
        case None => "All"
        case Some(values) => values.map(URLEncoder.encode(_)).mkString(":")
      }
    } + "/" + (if(showLinks) "Show" else "Hide")
  }
}
object View {
  def create(groupBy:String, filter:String, design:String) = {
    val groupByX = groupBy match {
      case null => "None"
      case "" => "None"
      case "None"|"none" => "None"
      case other => other
    }
    val filterX = filter match { 
      case null => None
      case "All"|"all" => None
      case "" => None
      case v => Some(v.split(":").toList)
    }
    View(groupByX, filterX, "show"==design)
  }
}
trait Watcher {
  def notify(html:String)
  def view:View
}
class DuplicateRegistrationException(message:String) extends Exception(message)
/**
 * Represents the state of the chalk board
 */
class Registry(val name:String, val defaultView:View) {

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
      val groups = expired.map { case (key, _) => { registerSessions(key).group } }.toSet
      expired.foreach { case (key, _) => {
        closed = closed - key
        registerSessions = registerSessions - key
      } }
      update(groups)
    }
    readWriteLock.writeLock().unlock()
  }
  
  def register(instance:Instance) {
    try {
	  readWriteLock.writeLock().lock()
	  registerSessions.get(instance.key) match {
	    case Some(instance) => {
	      if(!closed.contains(instance.key)) {
	    	throw new DuplicateRegistrationException(instance.key + " is already registered by " + instance)
	      }
	    }
	    case _ =>
	  }
	  val isNew = !closed.contains(instance.key)
	  closed = closed - instance.key
	  registerSessions = registerSessions + (instance.key -> instance)
	  if (isNew) {
	  	update(Set(instance.group), added=List(Page.instanceId(instance.key)))
	  } else {
	  	update(Set(instance.group), enabled=List(Page.instanceId(instance.key)))      
	  }
    } finally {
	  readWriteLock.writeLock().unlock()
    }
  }
  
  def update(instanceKey:String, prop:Prop) {
    try {
	  readWriteLock.writeLock().lock()
      registerSessions.get(instanceKey) match {
	    case None =>
	    case Some(instance) => {
	      val correctedProps = instance.props.map {
	        p => if (p.name == prop.name) prop else p
	      }
	      registerSessions = registerSessions + (instanceKey -> instance.copy(props=correctedProps))
	      update(Set(instance.group), props = List( Page.propId(instance.key, prop.name) ) )
	    }
	  }
    } finally {
	  readWriteLock.writeLock().unlock()
    }
  }

  def unregister(instance:Instance) {
    readWriteLock.writeLock().lock()
    closed = closed + (instance.key -> new java.util.Date())
    update(Set(instance.group), disabled=List(Page.instanceId(instance.key)))
    readWriteLock.writeLock().unlock()
  }
  
  private def update(groups:Set[String], enabled:List[String]=Nil, disabled:List[String]=Nil, added:List[String]=Nil, props:List[String]=Nil) {
    watchers.toArray(Array[Watcher]()).groupBy(_.view).foreach { case (view, w) => {
      val json = createJson(enabled, disabled, added, props, view).toString
      w.foreach(_.notify(json))
    } }
  }
  
  private def createJson(enabled:List[String], disabled:List[String], added:List[String], props:List[String], view:View) = {
    import scala.collection.JavaConversions
    val html = Page.listing(instances, view)
    val json = new JSONObject()
    json.put("html", html.toString)
    json.put("enable", new JSONArray(JavaConversions.asJavaCollection(enabled)))
    json.put("disable", new JSONArray(JavaConversions.asJavaCollection(disabled)))
    json.put("add", new JSONArray(JavaConversions.asJavaCollection(added)))
    json.put("updatedProperties", new JSONArray(JavaConversions.asJavaCollection(props)))
    json
  }
  
  private def toJson(map:Map[String,String]) = {
    val json = new JSONObject()
    map.foreach { case (name, value) => json.put(name, value) }
    json
  }
  
  def instances:List[InstanceSnapshot] = {
    readWriteLock.readLock().lock()
    val rs = registerSessions
    val c = closed
    readWriteLock.readLock().unlock()
    rs.values.toList.map { instance => InstanceSnapshot(instance, closed.contains(instance.key))}
  }
  
  def addWatcher(watcher:Watcher) {
    watchers.add(watcher)
  }
  
  def removeWatcher(watcher:Watcher) {
    watchers.remove(watcher)
  }
}