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

import java.util.concurrent.{ExecutorService, Executors}
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory
import org.jboss.netty.bootstrap.ServerBootstrap
import org.jboss.netty.channel.group.DefaultChannelGroup
import org.jboss.netty.handler.codec.frame.DelimiterBasedFrameDecoder
import org.jboss.netty.handler.codec.frame.Delimiters
import org.jboss.netty.channel._
import org.jboss.netty.handler.codec.string.StringDecoder
import org.jboss.netty.handler.codec.string.StringEncoder
import java.net.InetSocketAddress
import org.json.JSONObject
import org.json.JSONTokener
import org.jboss.netty.handler.timeout.ReadTimeoutException

/**
 * Uses a vanilla plain text socket to accept server registrations.
 * The protocol is the json instance on one line
 * The registration remains active until the socket is closed 
 */
class SocketRegistrationServer(registry:Registry, port:Int, executorService:ExecutorService, extras:ChannelHandler*) {
 
  val bootstrap = new ServerBootstrap(
                  new NioServerSocketChannelFactory(executorService, executorService))
  bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
    override def getPipeline() = {
      Channels.pipeline(extras.toList ::: List(
        new DelimiterBasedFrameDecoder(10000, Delimiters.lineDelimiter() :_*),
        new StringDecoder(),
        new StringEncoder(),
        new NettyRegistrationHandler) :_*)
    }
  })
  val channels = new DefaultChannelGroup()
  
  def start() {
    println("Accepting registrations on port " + port)
    bootstrap.bind(new InetSocketAddress(port))
  }

  def disconnectAll() {
    channels.disconnect()
  }
  
  class NettyRegistrationHandler extends SimpleChannelUpstreamHandler {
    var registered:Option[Instance] = None
    override def channelConnected(ctx:ChannelHandlerContext, e:ChannelStateEvent) {
      channels.add(e.getChannel())
    }
    override def messageReceived(ctx:ChannelHandlerContext, e:MessageEvent) {
      val message = e.getMessage().asInstanceOf[String]
      if (message == "OK") return
      try {
        val json = new JSONObject(new JSONTokener(message))
        registered match {
          case None => {
	          val instance = JsonInstance.createInstance(json)
	          registry.register(instance)
	          registered = Some(instance)
	          ctx.getChannel().write("OK\n")
          }
          case Some(i) => {
            if (json.has("id")) { //should express icon/prop better
	            val icon = JsonInstance.createIcon(json)
	            registry.update(i.key, icon)
              
            } else {
	            val prop = JsonInstance.createProp(json)
	            registry.update(i.key, prop)
            }
          }
        }
      } catch {
        case e:DuplicateRegistrationException => {
          ctx.getChannel().write(e.getMessage + "\n")
          ctx.getChannel().close()
        }
        case e:Exception => {
          ctx.getChannel.write(e.getMessage + "\n")
          ctx.getChannel().close()
          e.printStackTrace()
        }
      }
    }

    override def exceptionCaught(ctx: ChannelHandlerContext, e: ExceptionEvent) {
      if (e.getCause.isInstanceOf[ReadTimeoutException]) {
        ctx.getChannel().close()
      } else {
        e.getCause.printStackTrace()
      }
    }

    override def channelDisconnected(ctx:ChannelHandlerContext, e:ChannelStateEvent) {
      registered.foreach { instance => {
    	  registry.unregister(instance)
      }}
    }
  }
}