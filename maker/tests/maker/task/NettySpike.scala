package maker.task

import org.scalatest.FunSuite
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory
import java.util.concurrent.Executors
import org.jboss.netty.bootstrap.ServerBootstrap
import org.jboss.netty.channel.Channels
import org.jboss.netty.channel.ChannelPipeline
import org.jboss.netty.channel.ChannelPipelineFactory
import org.jboss.netty.channel.SimpleChannelUpstreamHandler
import org.jboss.netty.channel.MessageEvent
import org.jboss.netty.channel.ChannelHandlerContext
import java.net.InetSocketAddress
import org.jboss.netty.handler.codec.serialization.ObjectDecoder
import org.jboss.netty.handler.codec.serialization.ObjectEncoder
import org.jboss.netty.channel.ChannelStateEvent
import org.jboss.netty.bootstrap.ClientBootstrap
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory
import org.jboss.netty.handler.codec.serialization.ClassResolver
import org.jboss.netty.handler.codec.serialization.ClassResolvers
import org.jboss.netty.channel.ChannelFuture
import org.jboss.netty.channel.Channel
import org.jboss.netty.channel.group.ChannelGroup
import org.jboss.netty.channel.group.DefaultChannelGroup
import org.jboss.netty.channel.ChannelFutureListener
import org.jboss.netty.channel.ChannelEvent
import org.jboss.netty.channel.SimpleChannelDownstreamHandler
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.jboss.netty.channel.ExceptionEvent
import java.util.concurrent.atomic.AtomicBoolean
import maker.utils.Log

case class Foo(
  x : AtomicReference[Any]
)

object NettySpike extends App{

  val address = new InetSocketAddress(8080)

  case class NettyClientHandler(bootstrap : ClientBootstrap, size : Int ) extends SimpleChannelUpstreamHandler{
  
    override def channelConnected(ctx : ChannelHandlerContext, e : ChannelStateEvent){
      println("channelConnected " + e.getState + ", " + e.getValue)
      val msg = Foo(new AtomicReference("Started"))
      println("Sending message " + msg)
      val future = e.getChannel.write(msg)
      future.addListener(ChannelFutureListener.CLOSE)
    }

    override def exceptionCaught(ctx : ChannelHandlerContext, e : ExceptionEvent){
      println("NettyClientHandler received exception " + e)
      def printTraces(t : Throwable){
        Option(t).foreach{
          th ⇒ 
            println("\nCaused by")
            th.printStackTrace
            printTraces(th.getCause)
        }
      }
      //printTraces(e.getCause)
      ctx.sendUpstream(e)
      //super.exceptionCaught(ctx, e)
    }
  }

  def objectDecoder = new ObjectDecoder(ClassResolvers.softCachingResolver(getClass.getClassLoader)){
    override def handleUpstream(ctx : ChannelHandlerContext, e : ChannelEvent){
      //setMessage(e, "Decoding")
      super.handleUpstream(ctx, e)
    }
  }

  def setMessage(e : ChannelEvent, newState : String){
    Log.info("New state is " + newState)
    e match {
      case m : MessageEvent ⇒ {
        m.getMessage match {
          case Foo(x) ⇒ x.set(newState)
          case _ ⇒ println("msg was " + m)
        }
        println("Message event is " + m.getMessage)
      }
      case _ ⇒ println("Channel event " + e)
    }
  }

  class Client {
    val channelFactory  = new NioClientSocketChannelFactory(
      Executors.newCachedThreadPool(),
      Executors.newCachedThreadPool())
    val bootstrap = new ClientBootstrap(channelFactory)
    
    val encoder = new ObjectEncoder(){
      override def handleDownstream(ctx : ChannelHandlerContext, e : ChannelEvent){
        //setMessage(e, "Encoding")
        super.handleDownstream(ctx, e)
      }
    }
    val dummyDownstream = new SimpleChannelDownstreamHandler{
      override def handleDownstream(ctx : ChannelHandlerContext, e : ChannelEvent){
        //setMessage(e, "Dummy down")
        super.handleDownstream(ctx, e)
      }
    }
    val dummyUpstream = new SimpleChannelUpstreamHandler{
      override def handleUpstream(ctx : ChannelHandlerContext, e : ChannelEvent){
        //setMessage(e, "Dummy UP")
        super.handleUpstream(ctx, e)
      }
      override def exceptionCaught(ctx : ChannelHandlerContext, e : ExceptionEvent){
        println("Dummy received exception " + e)
        super.exceptionCaught(ctx, e)
        //ctx.sendUpstream(e)
      }
      override def toString = "Dummy upstream"
    }
    
    // Set up the pipeline factory.
    bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
        def getPipeline : ChannelPipeline = {
          Channels.pipeline(
            dummyDownstream,
            encoder,
            dummyUpstream,
            new NettyClientHandler(bootstrap, 10000)
          )
        }
    })

    def openConnection(numTries : Int = 0) : ChannelFuture = {
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
        println("Have connected")
        channelFuture
      } else{
        Thread.sleep(500)
        openConnection(numTries + 1)
      }
    }

    val channelFuture = openConnection()

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

  class ServerHandler extends SimpleChannelUpstreamHandler {
    override def messageReceived(ctx : ChannelHandlerContext, e : MessageEvent) {
      println("SUCCESS,  received " + e.getMessage)
      //setMessage(e, "Received in server")
      ctx.sendUpstream(e)
    }
  
  }

  class Server{
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
            //new ObjectEncoder, 
            objectDecoder,
            new ServerHandler
          )
        }
      }
    )
    val allChannels = new DefaultChannelGroup("Spike Server")
    val channel : Channel = bootstrap.bind(address)
    allChannels.add(channel)

    def close{
      allChannels.close.awaitUninterruptibly
      factory.releaseExternalResources
    }
  }
  lazy val client : Client = new Client
  new Thread(new Runnable{def run{client}}).start
  Thread.sleep(1100)
  val server = new Server
  Thread.sleep(1000)
  //new Thread(new Runnable{def run{client}}).start
  println("Closing client")
  client.close
  println("Closing server")
  server.close
}
