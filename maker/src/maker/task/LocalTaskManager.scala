package maker.task

import java.net.{ConnectException, InetSocketAddress}
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import maker.utils.os.ScalaCommand
import maker.utils.Log
import org.jboss.netty.bootstrap.ClientBootstrap
import org.jboss.netty.channel.{ChannelEvent, ChannelFuture, ChannelFutureListener, ChannelHandlerContext, ChannelPipeline, ChannelPipelineFactory, ChannelStateEvent, Channels, ExceptionEvent, MessageEvent, SimpleChannelUpstreamHandler}
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory
import org.jboss.netty.handler.codec.serialization.{ClassResolvers, ObjectDecoder, ObjectEncoder}
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory
import maker.Maker
import maker.utils.os.CommandOutputHandler
import TaskManagement._
import RemoteTaskManager._


object TaskManagement{
  val address = new InetSocketAddress(8080)

  def encoder(localRemote : String) = new ObjectEncoder(){
    override def handleDownstream(ctx : ChannelHandlerContext, e : ChannelEvent){
      Log.debug(localRemote + " - encoding " + e)
      super.handleDownstream(ctx, e)
    }
  }

  def objectDecoder(localRemote : String) = new ObjectDecoder(ClassResolvers.softCachingResolver(getClass.getClassLoader)){
    override def handleUpstream(ctx : ChannelHandlerContext, e : ChannelEvent){
      Log.debug(localRemote + " - decoding " + e)
      super.handleUpstream(ctx, e)
    }
  }
  def makeClientChannelFactory() = new NioClientSocketChannelFactory(
    Executors.newCachedThreadPool(),
    Executors.newCachedThreadPool())
  def makeServerChannelFactory() = new NioServerSocketChannelFactory(
    Executors.newCachedThreadPool(),
    Executors.newCachedThreadPool())
}

class LocalTaskManagerHandler extends SimpleChannelUpstreamHandler{
    override def exceptionCaught(ctx : ChannelHandlerContext, e : ExceptionEvent){
      e.getCause match {
        case _ : ConnectException ⇒ // retries will be done
        case _ ⇒ super.exceptionCaught(ctx, e)
      }
    }
  override def messageReceived(ctx : ChannelHandlerContext, e : MessageEvent){
    Log.debug("LOCAL manager received " + e.getMessage)
  }
  override def channelConnected(ctx : ChannelHandlerContext, e : ChannelStateEvent){
    Log.debug("LOCAL channelConnected " + e.getState + ", " + e.getValue)
  }
}



class LocalTaskManager {

  def launchRemote{
    val cmd = ScalaCommand(CommandOutputHandler(), Maker.mkr.props.Java().getAbsolutePath, Maker.mkr.runClasspath, "maker.task.RemoteTaskManager")
    cmd.execAsync
  }
  launchRemote

  val channelFactory = makeClientChannelFactory
  val bootstrap = new ClientBootstrap(channelFactory)
  
  bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
      def getPipeline : ChannelPipeline = {
        Channels.pipeline(
          encoder("LOCAL"),
          objectDecoder("LOCAL"),
          new LocalTaskManagerHandler
        )
      }
  })


  def openConnection(numTries : Int = 0) : ChannelFuture = {
    Log.debug("LOCAL Trying to open connection")
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
      Log.debug("LOCAL Have connected")
      channelFuture
    } else{
      Log.debug("LOCAL Connection failed - will retry")
      Thread.sleep(500)
      openConnection(numTries + 1)
    }
  }

  val channelFuture = openConnection()

  channelFuture.awaitUninterruptibly
  Log.debug("LOCAL Have created channel future")
  val msg = (0, TellMeYourProcessID)
  Log.debug("LOCAL Sending message " + msg)
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

object LocalTaskManager extends App{
  new LocalTaskManager
}
