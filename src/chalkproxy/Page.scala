package chalkproxy

/**
 * Holds all the html page generation code
 */
object Page {
  def listing(instances:List[InstanceSnapshot]) = {
    val groups = instances.groupBy(_.instance.group)
    <div id="groups">{groups.map { case (groupName, instances) => {
      groupHtml(groupName, instances)
    } }}</div>
  }
  
  def groupHtml(groupName:String, instances:List[InstanceSnapshot]) = {
    <h2>{groupName}</h2>
    <div class="instances" id={groupId(groupName)}>{
      instances.sortBy(_.instance.prefix).map { entry => { instanceHtml(entry.instance, !entry.isClosed) } }
    }</div>
  }
  
  def iconHtml(instance:Instance, icon:Icon) = {
    <a class="launch" href={instance.key + icon.url}>{
      if (icon.image == "") {
        icon.text
      } else {
        val src = if (icon.image.startsWith("/")) icon.image else "/" +instance.key + "/" + icon.image
        <img class="icon" src={src} alt={icon.text}/>
      }
    }</a>
  }
  
  def instanceHtml(instance:Instance, active:Boolean) = {
    val style = if (!active) "opacity: 0.3; zoom: 1; filter: alpha(opacity=30)" else ""
      <div class="row instance" id={instanceId(instance.key)} style={style}>
        <div class="span2 offset1 main-link"><a class="main-link" href={"/"+instance.prefix}>{instance.name}</a></div>
        <div class="span1 icons">{instance.icons.map { icon => iconHtml(instance, icon) } }</div>
        <div class="span8 props">{instance.props.map { case Prop(name, value, url) => { <span> <b>{name}:</b> {
          url match {
            case None => value
            case Some(u) => <a href={u}>{value}</a>
          }
        }</span> } } }</div>
      </div>
  }
  def groupId(group:String) = "group-"+group.replaceAll(" ", "_").toLowerCase()
  def instanceId(key:String) = "instance-" + key
}