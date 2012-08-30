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
case class InstanceSnapshot(instance:Instance, isClosed:Boolean, versionX:Int) {
  def propNames:Iterable[String] = instance.props.map(_.name)
}
case class RegistrationToken(id:Int)
case class View(groupBy:Option[String], filter:Option[List[String]], showLinks:Boolean=false, showDisconnected:Boolean=false) {
  def design = copy(showLinks=true)
  def hide = copy(showLinks=false)
  def showDisconnectedX = copy(showDisconnected=true)
  def hideDisconnected = copy(showDisconnected=false)
  def by(name:Option[String]) = copy(groupBy=name)
  def href = "/?"+ params
  def params = {
    (
      List("groupBy="+URLEncoder.encode(groupBy.getOrElse("None"))) ::: filter.map(v => "filter="+v).toList :::
      (if(showDisconnected) List("showDisconnected=true") else Nil) :::
      (if(showLinks) List("design=show") else Nil)
    ).mkString("&")
  }
  def asPath = {
    "/"+URLEncoder.encode(groupBy.getOrElse("None")) + "/" + {
      filter match {
        case None => "All"
        case Some(values) => values.map(URLEncoder.encode(_)).mkString(":")
      }
    } + "/" + (if(showLinks) "Show" else "Hide") + "/" + (if (showDisconnected) "true" else "false")
  }
}
object View {
  def create(groupBy:String, filter:String, design:String, showDisabled:String) = {
    val groupByX = groupBy match {
      case null => None
      case "" | "None"|"none" => None
      case other => Some(other)
    }
    val filterX = filter match { 
      case null => None
      case "All"|"all" => None
      case "" => None
      case v => Some(v.split(":").toList)
    }
    View(groupByX, filterX, "show"==design, showDisabled=="true")
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
  var stateSequence = 0
  var registerSessions = Map[String,Instance]()
  var closed = Map[String,java.util.Date]()
  var versions = Map[String,Int]()
  val watchers = new java.util.concurrent.ConcurrentLinkedQueue[Watcher]()
  
  def lookup(name:String) = {
    readWriteLock.readLock.lock()
    val instance = registerSessions.get(name.toLowerCase())
    readWriteLock.readLock.unlock()
    instance
  }
  
  private def write(action: => Unit) {
    readWriteLock.writeLock().lock()
    try {
      action
    } finally {
      readWriteLock.writeLock().unlock()      
    }
  }
  
  def cleanup() {
    write {
      val cutOff = System.currentTimeMillis() - (28*60*60*1000)
      val expired = closed.toList.filter { case (key,date) => date.getTime < cutOff}
      if (expired.nonEmpty) {
        val instances = expired.map(x=>registerSessions(x._1))
        expired.foreach { case (key, _) => {
          closed = closed - key
          registerSessions = registerSessions - key
          versions = versions - key
        } }
        update(expired=instances)
        stateSequence += 1
      }
    }
  }
  
  def register(instance:Instance) {
    write {
	  stateSequence += 1
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
	  versions = versions + (instance.key -> stateSequence)
	  if (isNew) {
	  	update(added=List(instance.key))
	  } else {
	  	update(enabled=List(instance.key))      
	  }
    }
  }
  
  def update(instanceKey:String, prop:Prop) {
    write {
	  stateSequence += 1
      registerSessions.get(instanceKey) match {
	    case None =>
	    case Some(instance) => {
	      val correctedProps = instance.props.map {
	        p => if (p.name == prop.name) prop else p
	      }
	      registerSessions = registerSessions + (instanceKey -> instance.copy(props=correctedProps))
	      versions = versions + (instanceKey -> stateSequence)
	      update(props = List( instance -> prop ) )
	    }
	  }
    }
  }

  def unregister(instance:Instance) {
    write {
      stateSequence += 1
      closed = closed + (instance.key -> new java.util.Date())
      versions = versions + (instance.key -> stateSequence)
      update(disabled=List(instance))
    }
  }
  
  private def update(
      enabled:List[String]=Nil,
      disabled:List[Instance]=Nil,
      added:List[String]=Nil,
      props:List[(Instance,Prop)]=Nil,
      expired:List[Instance]=Nil) {
    watchers.toArray(Array[Watcher]()).groupBy(_.view).foreach { case (view, w) => {
      val json = createJson(enabled, disabled, added, props, expired, view).toString
      w.foreach(_.notify(json))
    } }
  }
  
  private def createInstanceJson(groups:List[Group], key:String, view:View) = {
    def createJson(elementId:String, after:String, html:NodeSeq) = {
      val json = new JSONObject()
      json.put("key", elementId)
      json.put("html", html.toString)
      json.put("after", after)
      json
    }
    groups.zipWithIndex.flatMap { case (Group(name, instances), groupIndex) => {
      instances.zipWithIndex.find(_._1.instance.key == key) match {
        case None => Nil
        case Some( (instanceSnapshot,position) ) => {
          val group = if (instances.size > 1 || name.isEmpty) Nil else {
            val after = if (groupIndex == 0) "" else Page.groupId(groups(groupIndex-1).name)
            List(createJson(Page.groupId(name), after, Page.groupHtml(name)))
          }
          val ins = {
            val after = if (position > 0) {
              Page.instanceId(instances(position-1).instance.key)
            } else {
              Page.groupId(name)
            }
            createJson(Page.instanceId(key), after, Page.instanceHtml(instanceSnapshot))
          }
          group ::: List(ins)
        }
      }
    } }
  }
  
  private def createPropsJson(instance:Instance, prop:Prop) = {
    val json = new JSONObject()
    json.put("key", Page.propId(instance.key, prop.name))
    json.put("html", Page.propHtml(instance, prop))
    json
  }
  
  private def calculateRemoves(groups:List[Group], view:View, instance:Instance) = {
    view.groupBy match {
      case None => List(Page.instanceId(instance.key))
      case Some(g) => {
        val groupValue = Some(instance.valueFor(g))
        if (!groups.exists(_.name == groupValue)) {
          List(Page.groupId(groupValue), Page.instanceId(instance.key)) //the group no longer exists
        } else {
          List(Page.instanceId(instance.key))
        }
      }
    }
  }
  
  private def createJson(enabled:List[String], disabled:List[Instance], added:List[String], props:List[(Instance,Prop)], expired:List[Instance], view:View) = {
    import scala.collection.JavaConversions
    val (ii, state) = instances
    val groups = Page.groups(ii, view)
    val (addActions,enableActions) = if (view.showDisconnected) (added,enabled) else (added:::enabled,Nil) 
    val (removeActions,disableActions) = if (view.showDisconnected) (expired,disabled) else (expired:::disabled,Nil) 
    val json = new JSONObject()
    json.put("messageType", "partial")
    json.put("state", state)
    json.put("enable", new JSONArray(JavaConversions.asJavaCollection(enableActions.map(Page.instanceId))))
    json.put("disable", new JSONArray(JavaConversions.asJavaCollection(disableActions.map(i=>Page.instanceId(i.key)))))
    json.put("add", new JSONArray(JavaConversions.asJavaCollection(addActions.flatMap { key => {
      createInstanceJson(groups, key, view)
    } })))
    json.put("remove", new JSONArray(JavaConversions.asJavaCollection(removeActions.flatMap( instance => {
      calculateRemoves(groups, view, instance)
    }))))
    json.put("updateProperties", new JSONArray(JavaConversions.asJavaCollection(props.map( t => createPropsJson(t._1, t._2) ))))
    json
  }
  
  private def toJson(map:Map[String,String]) = {
    val json = new JSONObject()
    map.foreach { case (name, value) => json.put(name, value) }
    json
  }
  
  def instances:(List[InstanceSnapshot],Int) = {
    readWriteLock.readLock().lock()
    val rs = registerSessions
    val c = closed
    val state = stateSequence
    val v = versions
    readWriteLock.readLock().unlock()
    (rs.values.toList.map { instance => InstanceSnapshot(instance, closed.contains(instance.key), v(instance.key))}, state)
  }
  
  def addWatcher(watcher:Watcher) {
    watchers.add(watcher)
  }
  
  def removeWatcher(watcher:Watcher) {
    watchers.remove(watcher)
  }
}