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
import org.json.JSONTokener
import org.json.JSONArray

object JsonInstance {
  private def optionString(json:JSONObject, name:String) = {
    if (json.has(name)) Some(json.getString(name)) else None
  }
  def createInstance(json:JSONObject) = {
    
	  val name = json.getString("name")
	  val hostname = json.getString("hostname")
	  val port = json.getInt("port")
	  val icons = {
	    val iconsJson = if (json.has("icons")) json.getJSONArray("icons") else new JSONArray()
	    for (i <- 0 until iconsJson.length()) yield {
	      val iconJson = iconsJson.getJSONObject(i)
	      if (!iconJson.has("id")) iconJson.put("id", "i" + i) //for old servers before id was added
	      createIcon(iconJson)
	    }
	  }.toList
	  val props = {
	    val jsonProps = if (json.has("props")) json.getJSONArray("props") else new JSONArray()
	    for (i <- 0 until jsonProps.length()) yield {
	      val propJson = jsonProps.getJSONObject(i)
	      createProp(propJson)
	    }
	  }.toList
	  Instance(name, hostname, port, icons, props)
  }
  
  def createProp(propJson:JSONObject) = Prop(
      propJson.getString("name"),
      propJson.getString("value"),
      if (propJson.has("url")) Some(propJson.getString("url")) else None
  )
  
  def createIcon(iconJson:JSONObject) = Icon(
      iconJson.getString("id"),
      iconJson.getString("text"), 
      optionString(iconJson, "url"),
      optionString(iconJson, "image")
  )
}