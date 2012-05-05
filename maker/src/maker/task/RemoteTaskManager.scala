package maker.task

import java.net.InetSocketAddress
import java.util.concurrent.Executors
import maker.Maker
import maker.os.ScalaCommand
import org.jboss.netty.bootstrap.ServerBootstrap
import org.jboss.netty.channel.{Channel, ChannelEvent, ChannelHandlerContext, ChannelPipeline, ChannelPipelineFactory, Channels}
import org.jboss.netty.channel.group.DefaultChannelGroup
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory
import org.jboss.netty.handler.codec.serialization.{ClassResolvers, ObjectDecoder}
import java.lang.management.ManagementFactory
import org.jboss.netty.channel.SimpleChannelUpstreamHandler
import org.jboss.netty.channel.MessageEvent
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory
import org.jboss.netty.bootstrap.ClientBootstrap
import org.jboss.netty.handler.codec.serialization.ObjectEncoder
import org.jboss.netty.channel.SimpleChannelDownstreamHandler
import org.jboss.netty.channel.ExceptionEvent
import org.jboss.netty.channel.ChannelFuture
import java.util.concurrent.atomic.AtomicBoolean
import org.jboss.netty.channel.ChannelFutureListener
import org.jboss.netty.channel.ChannelStateEvent
import maker.utils.Log
import java.net.ConnectException

case object TellMeYourProcessID

class RemoteTaskManagerHandler extends SimpleChannelUpstreamHandler{
  override def messageReceived(ctx : ChannelHandlerContext, e : MessageEvent){
    println("Remote manager received " + e.getMessage)
    val channel = e.getChannel
    e.getMessage match {
      case TellMeYourProcessID ⇒
        channel.write(ProcessID())
      case _ ⇒
        println("got unexpected message " + e.getMessage)
    }
  }
}

object RemoteConfig{
  val address = new InetSocketAddress(8080)
}

object RemoteTaskManager extends App{

  import RemoteConfig._
  println("Running RemoteTaskManager")

  def objectDecoder = new ObjectDecoder(ClassResolvers.softCachingResolver(getClass.getClassLoader)){
    override def handleUpstream(ctx : ChannelHandlerContext, e : ChannelEvent){
      Log.info("REMOTE - decoding " + e)
      //setMessage(e, "Decoding")
      super.handleUpstream(ctx, e)
    }
  }
  val encoder = new ObjectEncoder(){
    override def handleDownstream(ctx : ChannelHandlerContext, e : ChannelEvent){
      Log.info("REMOTE - encoding " + e)
      //setMessage(e, "Encoding")
      super.handleDownstream(ctx, e)
    }
  }

    val factory = new NioServerSocketChannelFactory(
      Executors.newCachedThreadPool,
      Executors.newCachedThreadPool
    )
    val bootstrap = new ServerBootstrap(factory)
    bootstrap.setPipelineFactory(
      new ChannelPipelineFactory(){
        def getPipeline : ChannelPipeline = {
          //Channels.pipeline(new ServerHandler)
          Channels.pipeline(
            encoder, 
            objectDecoder,
            new RemoteTaskManagerHandler()
            //new ServerHandler
          )
        }
      }
    )
    val allChannels = new DefaultChannelGroup("Spike Server")
    val channel : Channel = bootstrap.bind(address)
    Log.info("RemoteTaskManager bound to address " + address)
    allChannels.add(channel)

    def close{
      allChannels.close.awaitUninterruptibly
      factory.releaseExternalResources
    }
}


