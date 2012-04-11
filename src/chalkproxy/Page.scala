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
    <div id="groups" class="container">
    {groupNames.map { groupName => {
      val instances = allGroups.getOrElse(groupName, Nil)
      groupHtml(groupName, instances)
    } }}
    </div>
  }
  
  def groupHtml(groupName:String, instances:List[InstanceSnapshot]) = {
    <div class="row group"><h2>{groupName}</h2></div> ++
    {instances.sortBy(_.instance.prefix).map { entry => { instanceHtml(entry.instance, !entry.isClosed) } } }
  }
  
  def iconHtml(instance:Instance, icon:Icon) = {
    <a class="" href={instance.key + icon.url}>{
      if (icon.image == "") {
        icon.text
      } else {
        val src = if (icon.image.startsWith("/")) icon.image else "/" +instance.key + "/" + icon.image
        <img class="icon" src={src} alt={icon.text}/>
      }
    }</a>
  }
  
  def instanceHtml(instance:Instance, active:Boolean) = {
    val disable= if (!active) " disable" else ""
      <div class="row instance" id={instanceId(instance.key)}>
        <div class={"span3 main-link " + disable}><a href={"/"+instance.prefix}>{instance.name}</a></div>
        <div class={"span1" + disable}>{instance.icons.map { icon => iconHtml(instance, icon) } }</div>
        <div class={"span8" + disable}>{instance.props.map { case Prop(name, value, url) => { <span class="prop"> <b>{name}:</b> {
          url match {
            case None => value
            case Some(u) => <a href={u}>{value}</a>
          }
        }</span> ++ Text(" ")} } }</div>
      </div>
  }
  def groupId(group:String) = "group-"+group.replaceAll(" ", "_").toLowerCase()
  def instanceId(key:String) = "instance-" + key
}