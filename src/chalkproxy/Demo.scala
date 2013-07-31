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
import org.json.JSONObject
import org.json.JSONArray

/**
 * Demonstrates many (6) clients registering and unregistering
 */
object Demo {
  def main(args:Array[String]) {
    val hostname = if (args.length > 0) args(0) else "localhost"
    val port = if (args.length > 1) args(1).toInt else  4000
    val mode = if (args.length > 2) args(2) else "start-stop"
    val demo = new Demo(hostname, port, "localhost")
    demo.go(mode)
  }
  def start(port:Int, mode:String) {
    val demo = new Demo("localhost", port, "localhost")
    new Thread(new Runnable() { def run() {
      demo.go(mode)
    }}, "Demo runner").start()
  }
  val modes = List("start-all", "start-one", "start-stop-all", "update", "start-stop")
}
class Demo(chalkHostname:String, chalkPort:Int, localHostname:String) {
  
  class SampleServer(port:Int, name:String, group:String, index:Int) {
    val longName = if (index==3) "gjfdkgjfkdgjrk " * 10 else "short"
    val chalkProxy = new ChalkProxyClient(chalkHostname, chalkPort, name, localHostname, port)
    chalkProxy.addIcon("a", "/go.jnlp", "Launch 2", "/assets/blowfish.png")
    chalkProxy.addIcon("b", "/go.jnlp", "ws", "/assets/blowfish.png")
    chalkProxy.addIcon("c", "/go.jnlp", "ws", "/assets/blowfish.png") 
    chalkProxy.addIcon("d", "/go.jnlp", "p")
    chalkProxy.addProperty("branch", if ((name.hashCode % 2) == 1) "master" else "release-1.0", "http://git/branches/master")
    chalkProxy.addProperty("commit", "fjfiwj5osj32")
    chalkProxy.addProperty("pwd", "/home/thomas/code/server/main") 
    chalkProxy.addProperty("&b=1", "url escaping test")
    chalkProxy.addProperty("started", "20Apr2012 14:54")
    chalkProxy.addProperty("Status", "starting")
    chalkProxy.addProperty("Long", longName)
    chalkProxy.addProperty("Name", name)
    chalkProxy.addProperty("headers", "here", "headers")
    chalkProxy.addProperty("pwd", "/home/thomas/code/server/main") 
    chalkProxy.addProperty("user", "t intentional spaces")
    chalkProxy.addProperty("started", "20Apr2012 14:54")
    chalkProxy.addProperty("pid", "2543")
    
    val webServer = new TestServer(name, port)
    def start() {
        webServer.start()
        chalkProxy.start()
    }
    def isStarted() = chalkProxy.isStarted
    def updateProperty(name:String, value:String) {
      chalkProxy.updateProperty(name, value)
    }
    def updateIcon(id:String, text:String, link:String, image:String) {
      chalkProxy.updateIcon(id, text, link, image)
    }
    def stop() {
      webServer.stop()
      chalkProxy.stop()
    }
  } 
  val foo = List("UAT" -> List("Red u@ thomas roo", "House u also quite long"), "Prod" -> List("James p", "Cloud p"), "Dev" -> List("D1", "D9"))
  val baseServerPort = 6000
  val samples = foo.flatMap { case (group, names) => { names.map { name => (name,group) } } }.zipWithIndex.map { case ((name,group), index) => {
    new SampleServer(baseServerPort+index, name, group, index)
  } }.toArray
  val colors = Array("Red", "Amber", "Green")
  val random = new java.util.Random(0)
  def go(mode:String) {
    mode match {
      case "start-all" => { samples.foreach(_.start) }
      case "start-one" => samples.head.start
      case "start-stop-all" => { samples.foreach(_.start); samples.foreach(_.stop) }
      case "start-stop-repeat" => {
        while (true) {
          samples.foreach(_.start);
          Thread.sleep(3000)
          samples.foreach(_.stop)
          Thread.sleep(4000)
        }
      }
      case "slow-start" => { samples.foreach{ s => s.start; Thread.sleep(5*60*1000) } }
      case "one-busy" => {
        samples.foreach(_.start);
	    val register = samples(4)
	    while (true) {
	      Thread.sleep(1500)
	      register.stop()
	      Thread.sleep(1500)
	      register.start()
	      //Thread.sleep(3000)
	    }
      }
      case "update-properties" => {
        samples.foreach(_.start);
	    while (true) {
	      val next = random.nextInt(samples.size)
	      val register = samples(next)
	      register.updateProperty("Status", colors(random.nextInt(colors.size)))
	      Thread.sleep(1000)
	    }
      }
      case "update-icons" => {
        samples.foreach(_.start);
        val server = samples(2)
	    var fish = true
	    while (true) {
	      val iconImage = if (fish) { 
	        "/assets/blowfish.png" 
	      } else {
	         "/assets/tick.png" 
	      }
	      fish = !fish
	      server.updateIcon("b", "x", "link-to", iconImage)
	      Thread.sleep(4000)
	    }
      }
      case "start-stop" => {
	    while (true) {
	      //val next = random.nextInt(samples.size)
	      //val register = samples(next)
	      samples.foreach { register => {
	        if (register.isStarted()) {
	          register.stop()
	        } else {
	          register.start()
	        }
	        Thread.sleep(1000)
	      } }
	    }
      }
    }
  }
}