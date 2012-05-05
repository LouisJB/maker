package maker.task

import org.jboss.netty.channel.SimpleChannelUpstreamHandler
import org.jboss.netty.channel.ChannelHandlerContext
import org.jboss.netty.channel.ExceptionEvent
import java.net.ConnectException
import maker.os.ScalaCommand
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory
import org.jboss.netty.bootstrap.ClientBootstrap
import org.jboss.netty.handler.codec.serialization.ObjectEncoder
import org.jboss.netty.channel.ChannelEvent
import org.jboss.netty.channel.ChannelPipelineFactory
import org.jboss.netty.channel.ChannelPipeline
import java.util.concurrent.Executors
import maker.utils.Log
import org.jboss.netty.channel.ChannelFuture
import maker.Maker
import org.jboss.netty.channel.Channels
import java.util.concurrent.atomic.AtomicBoolean
import org.jboss.netty.channel.ChannelFutureListener
import org.jboss.netty.channel.MessageEvent
import org.jboss.netty.handler.codec.serialization.ObjectDecoder
import org.jboss.netty.handler.codec.serialization.ClassResolvers

class LocalTaskManagerHandler extends SimpleChannelUpstreamHandler{
    override def exceptionCaught(ctx : ChannelHandlerContext, e : ExceptionEvent){
      e.getCause match {
        case _ : ConnectException ⇒ // retries will be done
        case _ ⇒ super.exceptionCaught(ctx, e)
      }
    }
  override def messageReceived(ctx : ChannelHandlerContext, e : MessageEvent){
    println("Local manager received " + e.getMessage)
  }
  //override def channelConnected(ctx : ChannelHandlerContext, e : ChannelStateEvent){
    //Log.info("channelConnected " + e.getState + ", " + e.getValue)
    //val msg = TellMeYourProcessID
    //Log.info("Sending message " + msg)
    //val future = e.getChannel.write(msg)
    //future.addListener(ChannelFutureListener.CLOSE)
    //}
}


class LocalTaskManager {

  import RemoteConfig._
  def launchRemote{
    val cmd = ScalaCommand(Maker.mkr.runClasspath, "maker.task.RemoteTaskManager")
    cmd.execAsync
  }
  launchRemote

  val channelFactory  = new NioClientSocketChannelFactory(
    Executors.newCachedThreadPool(),
    Executors.newCachedThreadPool())
  val bootstrap = new ClientBootstrap(channelFactory)
  
  val encoder = new ObjectEncoder(){
    override def handleDownstream(ctx : ChannelHandlerContext, e : ChannelEvent){
      Log.info("LOCAL - encoding " + e)
      //setMessage(e, "Encoding")
      super.handleDownstream(ctx, e)
    }
  }
  
  def objectDecoder = new ObjectDecoder(ClassResolvers.softCachingResolver(getClass.getClassLoader)){
    override def handleUpstream(ctx : ChannelHandlerContext, e : ChannelEvent){
      Log.info("LOCAL - decoding " + e)
      //setMessage(e, "Decoding")
      super.handleUpstream(ctx, e)
    }
  }
  // Set up the pipeline factory.
  bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
      def getPipeline : ChannelPipeline = {
        Channels.pipeline(
          encoder,
          objectDecoder,
          new LocalTaskManagerHandler
        )
      }
  })

  import RemoteTaskManager._

  def openConnection(numTries : Int = 0) : ChannelFuture = {
    Log.info("Trying to open connection")
    val maxretries = 5
    if (numTries > maxretries)
      throw new Exception("Can't connect to server")
    val channelFuture = bootstrap.connect(address)
    val haveConnected = new AtomicBoolean(false)
    channelFuture.addListener(
      new ChannelFutureListener(){
        def operationComplete(fut : ChannelFuture){
          haveConnected.set(fut.isSuccess)
        }
      }
    )
    channelFuture.awaitUninterruptibly
    if (haveConnected.get){
      Log.info("Have connected")
      channelFuture
    } else{
      Log.info("Connection failed - will retry")
      Thread.sleep(2000)
      openConnection(numTries + 1)
    }
  }

  val channelFuture = openConnection()
  channelFuture.awaitUninterruptibly
  println("Have created channel future")
  val msg = TellMeYourProcessID
  Log.info("Sending message " + msg)
  val future = channelFuture.getChannel.write(msg)
  future.awaitUninterruptibly
  def close{
    channelFuture.awaitUninterruptibly
    if (! channelFuture.isSuccess){
      channelFuture.getCause.printStackTrace
      System.exit(-1)
    }
    channelFuture.getChannel.getCloseFuture.awaitUninterruptibly
    channelFactory.releaseExternalResources
  }

}
