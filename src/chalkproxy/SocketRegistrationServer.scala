package chalkproxy
import java.util.concurrent.Executors
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory
import org.jboss.netty.bootstrap.ServerBootstrap
import org.jboss.netty.channel.group.DefaultChannelGroup
import org.jboss.netty.handler.codec.frame.DelimiterBasedFrameDecoder
import org.jboss.netty.handler.codec.frame.Delimiters
import org.jboss.netty.channel.ChannelPipelineFactory
import org.jboss.netty.channel.Channels
import org.jboss.netty.handler.codec.string.StringDecoder
import org.jboss.netty.handler.codec.string.StringEncoder
import java.net.InetSocketAddress
import org.jboss.netty.channel.SimpleChannelUpstreamHandler
import org.jboss.netty.channel.ChannelHandlerContext
import org.jboss.netty.channel.ChannelStateEvent
import org.jboss.netty.channel.MessageEvent

/**
 * Uses a vanilla plain text socket to accept server registrations.
 * The protocol is the json instance on one line
 * The registration remains active until the socket is closed 
 */
class SocketRegistrationServer(registry:Registry, port:Int) {
 
  val bootstrap = new ServerBootstrap(
                  new NioServerSocketChannelFactory(
                          Executors.newCachedThreadPool(),
                          Executors.newCachedThreadPool()));
  bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
    override def getPipeline() = {
      Channels.pipeline(new StringDecoder(), new StringEncoder(), new NettyRegistrationHandler)
    }
  })
  //bootstrap.setOption("child.keepAlive", true);
  bootstrap.bind(new InetSocketAddress(port))
  
  class NettyRegistrationHandler extends SimpleChannelUpstreamHandler {
    var registered:Option[Instance] = None
    override def messageReceived(ctx:ChannelHandlerContext, e:MessageEvent) {
      val message = e.getMessage().asInstanceOf[String]
      try {
	      val instance = JsonInstance.create(message)
	      registry.register(instance)
	      registered = Some(instance)
	      ctx.getChannel().write("OK\n")
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
    override def channelDisconnected(ctx:ChannelHandlerContext, e:ChannelStateEvent) {
      registered.foreach { instance => {
    	  registry.unregister(instance)
      }}
    }
  }
}