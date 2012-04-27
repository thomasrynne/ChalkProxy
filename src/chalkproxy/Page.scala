package chalkproxy
import scala.xml.Text
import scala.xml.NodeSeq

/**
 * Holds all the html page generation code
 */
object Page {
  def listing(instances:List[InstanceSnapshot], view:View) = {
    val main = {
      view.groupBy match {
        case None => instancesHtml(instances)
        case Some(prop) => {
          val allGroups = instances.groupBy(_.instance.valueFor(prop))
          val groupNames = view.filter match {
            case Some(filter) => filter
            case None => allGroups.keySet.toList.sorted
          }
          groupNames.map { groupName => {
            val instances = allGroups.getOrElse(groupName, Nil)
            groupHtml(groupName, instances)
          } }
        }
      }
    }
    <div id="groups" class="container-fluid">
         { main }
    </div>
  }
  
  def groupHtml(groupName:String, instances:List[InstanceSnapshot]) = {
    <div class="row-fluid group"><h2>{groupName}</h2></div> ++ instancesHtml(instances)
  }
  
  def instancesHtml(instances:Seq[InstanceSnapshot]) = {
    instances.sortBy(_.instance.prefix).map { entry => { instanceHtml(entry.instance, !entry.isClosed) } } 
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
  
  def instanceHtml(instance:Instance, active:Boolean) = {
    val disable= if (!active) " disable" else ""
      <div class="row-fluid instance" id={instanceId(instance.key)}>
        <div class={"span3 main-link" + disable}><a href={"/"+instance.prefix}>{instance.name}</a></div>
        <div class={"span1 icons" + disable}>{ instance.icons.map { icon => iconHtml(instance, icon) } }</div>
        <div class={"span8 props" + disable}>{instance.props.map { case Prop(name, value, url) => { <span class="prop" id={propId(instance.key, name)}> <b>{name}:</b> {
          url match {
            case None => value
            case Some(u) => <a href={addPrefix(instance, u)}>{value}</a>
          }
        }</span> ++ Text(" ")} } }</div>
      </div>
  }
  def fullPage(title:String, body:NodeSeq, props:List[String], rootView:View, view:View) = {
    val groupBy = view.groupBy.getOrElse("None")
    val filter = view.filter match {
      case Some(g) => g.mkString(":")
      case None => "all"
    }
    val design = if (view.showLinks) "show" else "hide"
    val link = if (view.showLinks) {
      val groupByText = {
        ("None" :: props).map { p => {
          if (p == groupBy) {
            <span class="groupby-selected">{p.capitalize}</span> ++ Text(" ")
          } else {
            <a class="groupby-option" href={view.by(p).href}>{p.capitalize}</a> ++ Text(" ")
          }
        } }
      }
      <span>Group by:</span> ++ groupByText ++ <a href={view.hide.href}>(hide)</a>
    } else {
      <span>{view.groupBy.map(v => "Group By " + v).getOrElse("")} <a href={view.design.href}>...</a></span>
    }
    
<html>
    <head debug="true">
        <title>{title}</title>
        <link rel="stylesheet" media="screen" href="/assets/bootstrap.css"/>
        <link rel="stylesheet" media="screen" href="/assets/main.css"/>
        <link rel="shortcut icon" type="image/png" href="/assets/favicon.png"/>
        <!-- <script type="text/javascript" src="https://getfirebug.com/firebug-lite-debug.js"></script> -->
    </head>
    <body>
        <div class="container-fluid">
          <div class="row-fluid">
            <div class="span3">{link}</div>
            <h1  class="span6">{title}</h1>
            <div class="span3"><span id='status'></span></div>
          </div>
        </div>
        { body }
        <script type="text/javascript">
          window.WEB_SOCKET_SWF_LOCATION = '/assets/WebSocketMain.swf';
          window.GROUPBY = '{groupBy}'
          window.FILTER = '{filter}';
          window.DESIGN = '{design}'; 
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