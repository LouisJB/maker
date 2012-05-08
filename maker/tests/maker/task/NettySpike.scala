package maker.task

import java.net.{ConnectException, InetSocketAddress}
import java.util.concurrent.Executors
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger, AtomicReference}
import maker.utils.Log
import org.jboss.netty.bootstrap.{ClientBootstrap, ServerBootstrap}
import org.jboss.netty.channel.{Channel, ChannelEvent, ChannelFuture, ChannelFutureListener, ChannelHandlerContext, ChannelPipeline, ChannelPipelineFactory, ChannelStateEvent, Channels, ExceptionEvent, MessageEvent, SimpleChannelDownstreamHandler, SimpleChannelUpstreamHandler}
import org.jboss.netty.channel.group.{ChannelGroup, DefaultChannelGroup}
import org.jboss.netty.channel.socket.nio.{NioClientSocketChannelFactory, NioServerSocketChannelFactory}
import org.jboss.netty.handler.codec.serialization.{ClassResolver, ClassResolvers, ObjectDecoder, ObjectEncoder}
import org.scalatest.FunSuite


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
      e.getCause match {
        case _ : ConnectException ⇒ // retries will be done
        case _ ⇒ super.exceptionCaught(ctx, e)
      }
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
    
    // Set up the pipeline factory.
    bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
        def getPipeline : ChannelPipeline = {
          Channels.pipeline(
            new ObjectEncoder,
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
        println("Connection failed - will retry")
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
