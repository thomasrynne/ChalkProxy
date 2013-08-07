/* 
    ChalkProxy - a directory for a team's test web servers
    Copyright (C) 2012 Thomas Rynne

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    See http://www.gnu.org/copyleft/gpl.html

*/
package chalkproxy

import org.json.JSONObject
import org.json.JSONArray
import scala.concurrent.SyncVar
import scala.xml.NodeSeq
import scala.actors.threadpool.locks.ReentrantReadWriteLock
import java.net.URLEncoder
import scala.collection.JavaConversions._

class RegisterSession

/* Case classes which form part of the api to Registry */
case class Icon(id:String, text:String, url:Option[String], image:Option[String])
case class Prop(name:String, value:String, url:Option[String])
case class Instance(name:String, host:String, port:Int, icons:List[Icon], props:List[Prop]) {
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
    props.find(_.name.equalsIgnoreCase(name)).map(_.value).getOrElse("Undefined")
  }
}
case class InstanceSnapshot(instance:Instance, isClosed:Boolean) {
  def propNames:Iterable[String] = instance.props.map(_.name)
}
case class RegistrationToken(id:Int)
case class View(groupBy:Option[String], filter:Option[List[String]], showLinks:Boolean=false, showDisconnected:Boolean=false) {
  def design = copy(showLinks=true)
  def hide = copy(showLinks=false)
  def showDisconnectedX = copy(showDisconnected=true)
  def hideDisconnected = copy(showDisconnected=false)
  def by(name:Option[String]) = copy(groupBy=name)
  def withFilter(value:String) = copy(filter=Some(List(value)))
  def clearFilter = copy(filter=None)
  def href = "/?"+ params
  def params = {
    (
      List("groupBy="+URLEncoder.encode(
        groupBy.getOrElse("None"), "UTF8")) ::: filter.map(v => "filter="+v.mkString(":")).toList :::
      (if(showDisconnected) List("showDisconnected=true") else Nil) :::
      (if(showLinks) List("design=show") else Nil)
    ).mkString("&")
  }
  def asPath = {
    "/"+URLEncoder.encode(groupBy.getOrElse("None"), "UTF8") + "/" + {
      filter match {
        case None => "All"
        case Some(values) => values.map(URLEncoder.encode(_, "UTF8")).mkString(":")
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
  def notify(html:JSONObject)
  def view:View
  def isActive:Boolean
}
case class ServerProperties(httpPort:Int, registrationPort:Int, v1RegistrationPort:Int,  pid:String, pwd:String, started:String)
class DuplicateRegistrationException(message:String) extends Exception(message)
/**
 * Represents the state of the chalk board
 */
class Registry(val name:String, val page:Page, val defaultView:View) {

  val refreshJson = new JSONObject() {
    put("messageType", "refresh")
  }
  
  val serverStartId = new java.util.Random().nextInt(100000)
  private val readWriteLock = new ReentrantReadWriteLock
  private var stateSequence = 0
  private var registerSessions = Map[String,Instance]()
  private var closed = Map[String,java.util.Date]()
  private val watchers = new java.util.concurrent.ConcurrentLinkedQueue[Watcher]()
  
  def watcherCount = {
    var c = 0
    for (w <- watchers) {
      if (w.isActive) c+=1
    }
    c
  }
  
  def lookup(name:String) = {
    read { registerSessions.get(name.toLowerCase()) }
  }
  
  private def write(action: => Unit) {
    readWriteLock.writeLock().lock()
    try {
      action
    } finally {
      readWriteLock.writeLock().unlock()      
    }
  }
  
  private def read[T](action: => T):T = {
    readWriteLock.readLock().lock()
    try {
      action
    } finally {
      readWriteLock.readLock().unlock()      
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
	      update(props = List( instance -> prop ) )
	    }
	  }
    }
  }

  def update(instanceKey:String, icon:Icon) {
    write {
	  stateSequence += 1
      registerSessions.get(instanceKey) match {
	    case None =>
	    case Some(instance) => {
	      val correctedIcons = instance.icons.map {
	        i => if (i.id == icon.id) icon else i
	      }
	      registerSessions = registerSessions + (instanceKey -> instance.copy(icons=correctedIcons))
	      update(icons = List( instance -> icon ) )
	    }
	  }
    }
  }

  def unregister(instance:Instance) {
    write {
      stateSequence += 1
      closed = closed + (instance.key -> new java.util.Date())
      update(disabled=List(instance))
    }
  }
  
  private def update(
      enabled:List[String]=Nil,
      disabled:List[Instance]=Nil,
      added:List[String]=Nil,
      props:List[(Instance,Prop)]=Nil,
      icons:List[(Instance,Icon)]=Nil,
      expired:List[Instance]=Nil) {
    try {
	  watchers.toArray(Array[Watcher]()).groupBy(_.view).foreach { case (view, w) => {
	    val json = createJson(enabled, disabled, added, props, icons, expired, view)
	    w.foreach(_.notify(json))
	  } }
    } catch {
      case e:Exception => {
        println("Exception while notifying watchers following a registry update")
        println("Registry update is OK but watcher notifications may have failed")
        e.printStackTrace()
      } 
    }
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
          val group = (instances.size > 1, name) match {
            case (true, _) | (_,None) => Nil
            case (_, Some(v))  => {  //change the id of the group to the id of the last instance in the group
              val after = if (groupIndex == 0) "" else groups(groupIndex-1).lastItemId(page)
              List(createJson(page.groupId(v), after, page.groupHtml(view, v)))
            }
          } 
          val ins = {
            val after = if (position > 0) {
              page.instanceId(instances(position-1).instance.key)
            } else {
              name.map(v=>page.groupId(v)).getOrElse("")
            }
            createJson(page.instanceId(key), after, page.instanceHtml(instanceSnapshot))
          }
          group ::: List(ins)
        }
      }
    } }
  }
  
  private def createPropsJson(instance:Instance, prop:Prop) = {
    val json = new JSONObject()
    json.put("key", page.propId(instance.key, prop.name))
    json.put("html", page.propHtml(instance, prop))
    json
  }
  
  private def createIconJson(instance:Instance, icon:Icon) = {
    val json = new JSONObject()
    json.put("key", page.iconId(instance.key, icon))
    json.put("html", page.iconHtml(instance, icon))
    json
  }

  private def calculateRemoves(groups:List[Group], view:View, instance:Instance) = {
    view.groupBy match {
      case None => List(page.instanceId(instance.key))
      case Some(g) => {
        val groupValue = Some(instance.valueFor(g))
        if (!groups.exists(_.name == groupValue)) {
          List(page.groupId(groupValue.get), page.instanceId(instance.key)) //the group no longer exists
        } else {
          List(page.instanceId(instance.key))
        }
      }
    }
  }
  
  private def createJson(enabled:List[String], disabled:List[Instance], added:List[String], props:List[(Instance,Prop)], icons:List[(Instance,Icon)], expired:List[Instance], view:View) = {
    import scala.collection.JavaConversions
    val (ii, state) = instances
    val groups = page.groups(ii, view)
    val (addActions,enableActions) = if (view.showDisconnected) (added,enabled) else (added:::enabled,Nil)
    val (removeActions,disableActions) = if (view.showDisconnected) (expired,disabled) else (expired:::disabled,Nil)
    val json = new JSONObject()
    json.put("messageType", "partial")
    json.put("state", state)
    json.put("enable", new JSONArray(JavaConversions.asJavaCollection(enableActions.map(page.instanceId))))
    json.put("disable", new JSONArray(JavaConversions.asJavaCollection(disableActions.map(i=>page.instanceId(i.key)))))
    json.put("add", new JSONArray(JavaConversions.asJavaCollection(addActions.flatMap { key => {
      createInstanceJson(groups, key, view)
    } })))
    json.put("remove", new JSONArray(JavaConversions.asJavaCollection(removeActions.flatMap( instance => {
      calculateRemoves(groups, view, instance)
    }))))
    json.put("updateProperties", new JSONArray(JavaConversions.asJavaCollection(
        props.map( t => createPropsJson(t._1, t._2) )
    )))
    json.put("updateIcons", new JSONArray(JavaConversions.asJavaCollection(
        icons.map( t => createIconJson(t._1, t._2) )
    )))
    json
  }
  
  private def toJson(map:Map[String,String]) = {
    val json = new JSONObject()
    map.foreach { case (name, value) => json.put(name, value) }
    json
  }
  
  def instances:(List[InstanceSnapshot],Int) = {
    read {
      (registerSessions.values.toList.map { instance => InstanceSnapshot(instance, closed.contains(instance.key))}, stateSequence)
    }
  }
  
  def addWatcher(watcher:Watcher) {
    watchers.add(watcher)
  }
  
  def removeWatcher(watcher:Watcher) {
    watchers.remove(watcher)
  }
}