package chalkproxy
import scala.xml.Text

/**
 * Holds all the html page generation code
 */
object Page {
  def listing(instances:List[InstanceSnapshot], groupFilter:Option[List[String]]) = {
    val allGroups = instances.groupBy(_.instance.group)
    val groupNames = groupFilter match {
      case Some(filter) => filter
      case None => allGroups.keySet.toList.sorted
    }
    <div id="groups" class="container-fluid">
    {groupNames.map { groupName => {
      val instances = allGroups.getOrElse(groupName, Nil)
      groupHtml(groupName, instances)
    } } }
    </div>
  }
  
  def groupHtml(groupName:String, instances:List[InstanceSnapshot]) = {
    <div class="row-fluid group"><h2>{groupName}</h2></div> ++
    {instances.sortBy(_.instance.prefix).map { entry => { instanceHtml(entry.instance, !entry.isClosed) } } }
  }
  
  private def addPrefix(instance:Instance, url:String) = {
    if (url.startsWith("/") || url.contains("://")) url else "/" + instance.prefix + "/" + url
  }
  
  def iconHtml(instance:Instance, icon:Icon) = {
    <a class="" href={instance.key + icon.url}>{
      if (icon.image == "") {
        icon.text
      } else {
        <img class="icon" src={addPrefix(instance, icon.image)} alt={icon.text}/>
      }
    }</a>
  }
  
  def instanceHtml(instance:Instance, active:Boolean) = {
    val disable= if (!active) " disable" else ""
      <div class="row-fluid instance" id={instanceId(instance.key)}>
        <div class={"span3 main-link " + disable}><a href={"/"+instance.prefix}>{instance.name}</a></div>
        <div class={"span1" + disable}>{instance.icons.map { icon => iconHtml(instance, icon) } }</div>
        <div class={"span8" + disable}>{instance.props.map { case Prop(name, value, url) => { <span class="prop" id={propId(instance.key, name)}> <b>{name}:</b> {
          url match {
            case None => value
            case Some(u) => <a href={addPrefix(instance, u)}>{value}</a>
          }
        }</span> ++ Text(" ")} } }</div>
      </div>
  }
  def propId(instanceKey:String, propName:String) = "prop-" + instanceKey + "-" + propName.replaceAll(" ", "_").toLowerCase()
  def groupId(group:String) = "group-"+group.replaceAll(" ", "_").toLowerCase()
  def instanceId(key:String) = "instance-" + key
}