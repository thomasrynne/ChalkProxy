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

import java.net.URI
import de.roderick.weberknecht.WebSocketConnection
import de.roderick.weberknecht.WebSocketEventHandler
import de.roderick.weberknecht.WebSocketMessage
import org.json.JSONObject
import org.json.JSONArray
import scala.collection.JavaConversions
import java.net.Socket
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicBoolean
import java.net.ConnectException
import java.util.concurrent.atomic.AtomicReference
import java.net.SocketException

/**
 * The server registration code to be used by servers
 * Creates a socket or websocket connection, sends the details as json, leaves the socket / websocket open
 */
case class ChalkIcon(text:String, image:String, url:String)
case class ChalkProperty(name:String, value:String, url:Option[String]=None)
class ChalkProxy(wpHost:String, wpPort:Int, serverHost:String, serverPort:Int, val name:String, val group:String,
    icons:List[ChalkIcon],
    properties:List[ChalkProperty]) {
  var latestProperties = properties
  def propertyToJson(attribute:ChalkProperty) = {
    val json = new JSONObject()
    json.put("name", attribute.name)
    json.put("value", attribute.value)
    attribute.url.foreach { url => {
  	  json.put("url", url)
    }}
    json
  }
  private def createInstanceJson() = {
	def iconToJson(icon:ChalkIcon) = {
      val json = new JSONObject()
      json.put("text", icon.text)
      json.put("image", icon.image)
      json.put("url", icon.url)
      json
    }
    val json = new JSONObject()
    json.put("name", name)
    json.put("group", group)
    json.put("hostname", serverHost)
    json.put("port", serverPort.toString)
    json.put("icons", {
      new JSONArray(JavaConversions.asJavaCollection(icons.map { icon => iconToJson(icon) }))
    })
    json.put("props", {
      new JSONArray(JavaConversions.asJavaCollection(latestProperties.map { property => propertyToJson(property) }))
    })
    json.toString
  }
  
  trait ChalkConnection {
    def update(prop:ChalkProperty)
    def stop()
  }
  class ChalkSocketConnection extends ChalkConnection with Runnable {
    
	val socket:AtomicReference[Socket] = new AtomicReference(null)
	val keepConnected = new AtomicBoolean(true)
    def run() {
	    def connectAndWait() {
          val json = createInstanceJson()
	      val s = new Socket(wpHost, wpPort)
	      socket.set(s)
	      s.getOutputStream().write((json + "\n").getBytes("utf8"))
	      val reader = new BufferedReader(new InputStreamReader(s.getInputStream()))
	      val response = reader.readLine
	      if (response != "OK") {
	        println("Registration failed: " + response)
	      }
	      while (reader.readLine() != null) {}
	    }
	    var retryInterval = 5000;
	    while (keepConnected.get) {
	      try {
	        connectAndWait()
	        retryInterval = 5000;
	      } catch {
	        case e:SocketException => {
	          retryInterval = math.min(retryInterval * 2, 5*60*1000)
	        }
	      }
	      if (keepConnected.get) {
	        Thread.sleep(retryInterval)
	      }
	    }
    }
	def update(prop:ChalkProperty) {
	  
      val s = socket.get
      if (s != null) {
        s.getOutputStream().write((propertyToJson(prop).toString() + "\n").getBytes("utf8"))
      } else {
        //throw new Exception("Not connected")
      }
    }
    def stop() {
      keepConnected.set(false)
      val s = socket.get
      if (s != null) s.close()
    }
  }
  val connection = new AtomicReference[ChalkConnection](null)
  
  def start() {
    val newConnection = new ChalkSocketConnection
    val notStarted = connection.compareAndSet(null, newConnection)
    if (notStarted) {
      new Thread(newConnection, "ChalkProxy").start()
    } else {
      throw new Exception("Already started")
    }
  }
  def isStarted() = {
    connection.get != null
  }
  def update(prop:ChalkProperty) {
    latestProperties = latestProperties.map { p => if (p.name == prop.name) prop else p }
    val c = connection.get
    if (c != null) {
      c.update(prop)
    } else {
      throw new Exception("Not started")
    }
  }
  def stop() {
    val c = connection.get
    connection.set(null)
    if (c != null) {
      c.stop()
    } else {
      throw new Exception("Not started")
    }
  }
}