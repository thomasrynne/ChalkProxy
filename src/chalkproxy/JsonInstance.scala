package chalkproxy
import org.json.JSONObject
import org.json.JSONTokener
import org.json.JSONArray

object JsonInstance {
  def createInstance(json:JSONObject) = {
	  val name = json.getString("name")
	  val group = json.getString("group")
	  val hostname = json.getString("hostname")
	  val port = json.getInt("port")
	  val icons = {
	    val iconsJson = if (json.has("icons")) json.getJSONArray("icons") else new JSONArray()
	    for (i <- 0 until iconsJson.length()) yield {
	      val iconJson = iconsJson.getJSONObject(i)
	      Icon(iconJson.getString("text"), iconJson.getString("image"), iconJson.getString("url"))
	    }
	  }.toList
	  val props = {
	    val jsonProps = if (json.has("props")) json.getJSONArray("props") else new JSONArray()
	    for (i <- 0 until jsonProps.length()) yield {
	      val propJson = jsonProps.getJSONObject(i)
	      createProp(propJson)
	    }
	  }.toList
	  Instance(name, group, hostname, port, icons, props)
  }
  
  def createProp(propJson:JSONObject) = Prop(propJson.getString("name"), propJson.getString("value"), if (propJson.has("url")) Some(propJson.getString("url")) else None)
}