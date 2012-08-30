package chalkproxy
import scala.xml.Text
import scala.xml.NodeSeq

case class Group(name:Option[String], instances:List[InstanceSnapshot])
/**
 * Holds all the html page generation code
 */
object Page {
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
        group.name.toList.map(g => groupHtml(g)) ::: group.instances.map(instanceHtml(_))
      } }
    }
    <div id="groups" class="container-fluid">
         { main }
    </div>
  }
  
  def groupHtml(groupName:String) = {
    <div class="row-fluid group" id={groupId(groupName)}><h2>{groupName}</h2></div>
  }
  
  private def addPrefix(instance:Instance, url:String) = {
    if (url.startsWith("/") || url.contains("://")) url else "/" + instance.prefix + "/" + url
  }
  
  def iconHtml(instance:Instance, icon:Icon) = {
    <a class="icon" href={instance.key + icon.url}>{
      if (icon.image == "") {
        icon.text
      } else {
        <img class="iconimage" src={addPrefix(instance, icon.image)} alt={icon.text}/>
      }
    }</a>
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
        <div class={"span3 main-link" + disable}><a href={"/"+instance.prefix}>{instance.name}</a></div>
        <div class={"span1 icons" + disable}>{ instance.icons.map { icon => iconHtml(instance, icon) } }</div>
        <div class={"span8 props" + disable}>{instance.props.map { case prop@Prop(name, _, _) => {
          <span class="prop" id={propId(instance.key, name)}>{propHtml(instance,prop)}</span>++ Text(" ")}
        } }</div>
      </div>
  }
  def fullPage(title:String, body:NodeSeq, props:List[String], state:Int, rootView:View, view:View) = {
    val link = if (view.showLinks) {
      val groupByText = {
        (None :: props.map(Some(_))).map { p => {
          if (p == view.groupBy) {
            <span class="groupby-selected">{p.getOrElse("None").capitalize}</span> ++ Text(" ")
          } else {
            <a class="groupby-option" href={view.by(p).href}>{p.getOrElse("None").capitalize}</a> ++ Text(" ")
          }
        } }
      }
      <span>[<a href={view.hide.href} title="Hide options">hide options</a>]</span> ++ 
      <div class="options">
        <div><span>Group by:</span> {groupByText} </div>
        <div>Disconnected: {if (view.showDisconnected) <a href={view.hideDisconnected.href}>Hide</a> else <a href={view.showDisconnectedX.href}>Show</a>}</div>
      </div>
    } else {
      <span>
        {view.groupBy match { case None => ""; case Some(v) => "Grouped By " + v.capitalize}}
        [<a href={view.design.href} title="Change group by or show/hide disabed options">options</a>]
      </span>
    }
<html>
    <head debug="true">
        <title>{title}</title>
        <link rel="stylesheet" media="screen" href="/assets/bootstrap.css"/>
        <link rel="stylesheet" media="screen" href="/assets/main.css"/>
        <link rel="shortcut icon" type="image/png" href="/assets/favicon.png"/>
        <!-- <script type="text/javascript" src="https://getfirebug.com/firebug-lite-debug.js"></script> -->
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
            <div class="span3">{link}</div>
            <h1  class="span6">{title}</h1>
            <div class="span3"> [<a href='/About'>About</a>] <span id='status'></span> </div>
          </div>
        </div>
        { body }
        <script type="text/javascript">
          window.STATE = {state};
          window.WEB_SOCKET_SWF_LOCATION = '/assets/WebSocketMain.swf';
          window.PARAMS = '{view.params}'
          window.PATH = '{view.asPath}'
          window.SHOW_DISCONNECTED={view.showDisconnected}
        </script>
        <script src="/assets/jquery-1.7.1.min.js" type="text/javascript"></script>
        <script src="/assets/jquery-ui-1.8.18.custom.min.js" type="text/javascript"></script>
        <script src="/assets/json2.js" type="text/javascript"></script>
        <script type="text/javascript" src="/assets/swfobject.js"></script>
        <script type="text/javascript" src="/assets/web_socket.js"></script>
        <script type="text/javascript" charset="utf-8" src="/assets/functions.js"></script>  
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
  def propId(instanceKey:String, propName:String) = "prop-" + clean(instanceKey + "-" + propName)
  def groupId(group:String) = "group-"+clean(group)
  def instanceId(key:String) = "instance-" + clean(key)
}