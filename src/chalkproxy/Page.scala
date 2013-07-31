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

import scala.xml.Text
import scala.xml.NodeSeq

case class Group(name:Option[String], instances:List[InstanceSnapshot]) {
  def lastItemId(page:Page) = {
    if (instances.isEmpty) {
      page.groupId(name.get)
    } else {
      page.instanceId(instances.last.instance.key)
    }
  }
}
/**
 * Holds all the html page generation code
 */
class Page(val assetsHandler:EmbeddedAssetsHandler, links:List[(String,View)]) {
  def groups(instances:List[InstanceSnapshot], view:View) = {
    def sortAndFilter(instances:List[InstanceSnapshot]) = {
     instances.sortBy(_.instance.key).filter { instance => {
       if (view.showDisconnected) true else !instance.isClosed
     }} 
    }
    val g = view.groupBy match {
      case None => Group(None, sortAndFilter(instances)) :: Nil
      case Some(prop) => {
        val allGroups = instances.groupBy(_.instance.valueFor(prop))
        val groupNames = view.filter match {
          case Some(filter) => filter
          case None => allGroups.keySet.toList.sortBy(_.toLowerCase())
        }
        groupNames.map { groupName => {
          val instances = allGroups.getOrElse(groupName, Nil)
          Group(Some(groupName), sortAndFilter(instances))
        } }
      }
    }
    g.filter(_.instances.nonEmpty)    
  }
  def listing(instances:List[InstanceSnapshot], view:View) = {
    val gs = groups(instances, view)
    val main = {
      gs.flatMap { group => {
        group.name.toList.map(g => groupHtml(view, g)) ::: group.instances.map(instanceHtml(_))
      } }
    }
    <div id="groups" class="container-fluid">
         { main }
    </div>
  }
  
  def groupHtml(view:View, groupName:String) = {
    val filterView = view.withFilter(groupName)
    val href = if (view == filterView) None else Some(filterView.href)
    <div class="row-fluid group" id={groupId(groupName)}><h2>{a(href){Text(groupName)}}</h2></div>
  }

  def a(href:Option[String])(content: => NodeSeq) = {
    href match {
      case Some(_href) => <a href={_href}>{content}</a>
      case _ => content
    }
  }
  
  private def addAssetsPrefix(instance:Instance, url:String) = {
    if (url.startsWith("/")) {
      assetsHandler.url(url)
    } else if (url.contains("://")) {
      url
    } else {
      "/" + instance.prefix + "/" + url
    }
  }

  private def addPrefix(instance:Instance, url:String) = {
    if (url.startsWith("/")) url else
    if (url.contains("://")) url else
      "/" + instance.prefix + "/" + url
  }
  
  def iconHtml(instance:Instance, icon:Icon) = {
    val textOrImage = icon.image match {
      case Some(image) if (image.trim.nonEmpty) =>
          <img class="iconimage" src={addAssetsPrefix(instance, image)}
             alt={icon.text} title={icon.text}/>
      case _ => Text(icon.text)
    }
    icon.url match {
      case Some(url) if (url.trim.nonEmpty) =>
        <a class="icon" href={addPrefix(instance, url)}>{textOrImage}</a>
      case _ => textOrImage
    }
  }
  
  def propHtml(instance:Instance, prop:Prop):NodeSeq = {
   <b>{prop.name}:</b> ++ {
     prop.url match {
       case None => Text(prop.value)
       case Some(u) => <a href={addPrefix(instance, u)}>{prop.value}</a>
     }
   }
  }
  
  def instanceHtml(instanceSnapshot:InstanceSnapshot) = {
    val instance = instanceSnapshot.instance
    val active = !instanceSnapshot.isClosed
    val disable= if (!active) " disable" else ""
    val disconnected = if (!active) " disconnected" else ""
      <div class={"row-fluid instance" + disconnected} id={instanceId(instance.key)}>
        <div class={"span3 main-link" + disable}><a href={"/"+instance.prefix}>{instance.name}</a>
          <input type="checkbox" class="checkbox"/></div>
        <div class={"span1 icons" + disable}>{ instance.icons.map { icon => 
          <span id={iconId(instance.key, icon)}>{iconHtml(instance, icon) }</span> } }</div>
        <div class={"span8 props" + disable}>{instance.props.map { case prop@Prop(name, _, _) => {
          <span class="prop" id={propId(instance.key, name)}>{propHtml(instance,prop)}</span>++ Text(" ")}
        } }</div>
      </div>
  }
  def fullPage(title:String, homePage:Boolean, body:NodeSeq, props:List[String], state:Int, serverStartId:Int, view:View, firebugLite:Boolean) = {
    val home = if (homePage) NodeSeq.Empty else Text("[") ++ <a href="/">Home</a> ++ Text("] ")
    val linksHtml = links.map { case (linkName,linkView) => {
      if (linkView == view) {
        Text(" ") ++ <span>{linkName}</span>
      } else {
        Text(" ") ++ <a href={linkView.href}>{linkName}</a>
      }
    } }
    val topLeft = if (view.showLinks) {
      val groupByText = {
        (None :: props.map(Some(_))).map { p => {
          if (p == view.groupBy) {
            <span class="groupby-selected">{p.getOrElse("None").capitalize}</span> ++ Text(" ")
          } else {
            <a class="groupby-option" href={view.by(p).href}>{p.getOrElse("None").capitalize}</a> ++ Text(" ")
          }
        } }
      }
      val filterText = view.filter match {
        case None => Text("None")
        case Some(values) => Text("[") ++ <a href={view.clearFilter.href}>X</a> ++ Text("] " + values.mkString(" "))
      }
      home ++ <span>[<a href={view.hide.href} title="Hide options">hide options</a>]</span> ++
      <div class="options">
        <div><span>Group by:</span> {groupByText} </div>
        <div><span>Filter:</span> {filterText}</div>
        <div>Disconnected: {if (view.showDisconnected) <a href={view.hideDisconnected.href}>Hide</a> else <a href={view.showDisconnectedX.href}>Show</a>}</div>
      </div>
    } else {
      <div class="option">{home} [<a href={view.design.href} title="Change group by or show/hide disabed options">options</a>]</div>
      <div class="option">{view.groupBy match { case None => ""; case Some(v) => "Group By: " + v.capitalize}}</div>
      <div class="option">{view.filter match { case None => ""; case Some(v) => "Filter: " + v.mkString(" ") } }</div>
    }
<html>
    <head>
        <title>{title}</title>
        <link rel="stylesheet" media="screen" href="/assets/bootstrap-2.0.2.css"/>
        <link rel="stylesheet" media="screen" href={assetsHandler.url("/assets/main.css")}/>
        <link rel="shortcut icon" type="image/png" href="/assets/favicon.png"/>
        { if (firebugLite) <script type="text/javascript" src="https://getfirebug.com/firebug-lite-debug.js"></script> else NodeSeq.Empty }
        <style>{if (view.showDisconnected)"""
.main-link.disable {
  opacity: 0.4; /*grey out off-line instances */
  zoom: 1; filter: alpha(opacity=40) /* needed for IE */
}
.icons.disable {
  opacity: 0.15; /*grey out off-line instances */
  zoom: 1; filter: alpha(opacity=15) /* needed for IE */
}
.props.disable {
  opacity: 0.4; /*grey out off-line instances */
  zoom: 1; filter: alpha(opacity=40) /* needed for IE */
}""" else """.disconnected{display: none;}"""}</style>
    </head>
    <body>
        <div class="container-fluid">
          <div class="row-fluid title">
            <div class="span2">{topLeft}</div>
            <div class="span2">{linksHtml}</div>
            <h1  class="span4">{title}</h1>
            <div class="span2">&nbsp;</div>
            <div class="span2"> [<a href='/About'>About</a>] <span id='status'></span> </div>
          </div>
        </div>
        { body }
        <script type="text/javascript">
          window.SERVER_START_ID = {serverStartId}
          window.STATE = {state};
          window.PARAMS = '{scala.xml.Unparsed(view.params)}'
          window.PATH = '{view.asPath}'
          window.SHOW_DISCONNECTED={view.showDisconnected}
        </script>
        <script src="/assets/jquery-1.7.1.min.js" type="text/javascript"></script>
        <script src="/assets/jquery-ui-1.8.18.custom.min.js" type="text/javascript"></script>
        <script src="/assets/json-2.0.js" type="text/javascript"></script>
        <script type="text/javascript" charset="utf-8" src={assetsHandler.url("/assets/functions.js")}></script>  
    </body>
</html>
  }
  private def clean(text:String) = {
    val builder = new StringBuilder()
    for (c <- text.toCharArray()) {
      if (c.isLetterOrDigit) builder.append(c) else builder.append('_')
    }
    builder.toString.toLowerCase
  }
  def iconId(instanceKey:String, icon:Icon) = "icon-" + clean(instanceKey + "-" + icon.id)
  def propId(instanceKey:String, propName:String) = "prop-" + clean(instanceKey + "-" + propName)
  def groupId(group:String) = "group-"+clean(group)
  def instanceId(key:String) = "instance-" + clean(key)
}