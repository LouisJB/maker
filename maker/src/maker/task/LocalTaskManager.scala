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
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicInteger
import maker.utils.os.ProcessID
import scalaz.Scalaz._
import maker.utils.os.OsUtils
import java.util.concurrent.ConcurrentHashMap


object TaskManagement{

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


case class Waiting(msg : Any){
  private val lock = new Object
  private var completed = false
  private var result: Any = null
  private var exception: Throwable = null

  def waitAndReturn = {
    lock.synchronized {
      if (!completed) {
        lock.wait()
      }
      if (exception != null) {
        throw exception
      }
      result
    }
  }

  def exception(e: Throwable) {
    lock.synchronized {
      this.completed = true
      exception = e
      lock.notify()
    }
  }

  def setResult(result: Object) {
    lock.synchronized {
      this.completed = true
      this.result = result
      lock.notify()
    }
  }
}

case object CannotConnectToRemoteServer extends Throwable

case class LocalTaskManager(retryTime : Int = 500, maxRetries : Int = 5) {

  val waitingFor = new ConcurrentHashMap[Int, Waiting]() 

  class LocalTaskManagerHandler extends SimpleChannelUpstreamHandler{

    override def exceptionCaught(ctx : ChannelHandlerContext, e : ExceptionEvent){
      e.getCause match {
        case _ : ConnectException ⇒ // retries will be done
        case _ ⇒ super.exceptionCaught(ctx, e)
      }
    }

    override def messageReceived(ctx : ChannelHandlerContext, e : MessageEvent){
      Log.debug("LOCAL manager received " + e.getMessage)
      e.getMessage match {
        case (id, result : AnyRef) ⇒ 
          waitingFor.get(id).setResult(result)
      }
    }

    override def channelConnected(ctx : ChannelHandlerContext, e : ChannelStateEvent){
      Log.debug("LOCAL channelConnected " + e.getState + ", " + e.getValue)
    }

  }
  private val port : Int = Maker.props.RemoteTaskPort()

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

  val channelFuture : AtomicReference[Option[ChannelFuture]] = new AtomicReference(None)
  val nextMessageNo = new AtomicInteger(0)
  val remoteProcess : AtomicReference[Option[Process]] = new AtomicReference(None)

  def remoteProcessID : Option[ProcessID] = remoteProcess.get.map(ProcessID(_))

  def isRemoteRunning = remoteProcessID.fold(_.isRunning, false)
  def isPortUsed = OsUtils.isPortUsed(port)


  def connectToRemote{
    channelFuture.set(Some(openConnection()))
  }

  def sendMessage(message : AnyRef) = {
    channelFuture.get match {
      case Some(cf) ⇒ {
        val id = nextMessageNo.getAndIncrement
        val waiting = Waiting(message)
        waitingFor.put(id, waiting)
        val result = cf.getChannel.write((id, message))
        result.addListener(new ChannelFutureListener {
          def operationComplete(future: ChannelFuture) {
            if (!future.isSuccess) {
              waiting.exception(future.getCause)
            }
          }
        })
        waiting.waitAndReturn
      }
      case None ⇒ 
        throw new Exception("Have no channel")
    }
  }

  def launchRemote {
    assert(! isPortUsed, "Port " + port + " already used ")
    val cmd = ScalaCommand(CommandOutputHandler(), Maker.mkr.props.Java().getAbsolutePath, Nil, Maker.mkr.runClasspath, "maker.task.RemoteTaskManager")
    val (proc, _) = cmd.execAsync
    remoteProcess.set(Some(proc))
  }

  def closeRemote{
    remoteProcess.get match {
      case Some(proc) ⇒ proc.destroy
      case None ⇒ throw new Exception("No known process runing")
    }
    remoteProcess.set(None)
  }

  private def openConnection(numTries : Int = 0) : ChannelFuture = {
    Log.info("LOCAL Trying to open connection")
    if (numTries > maxRetries)
      throw CannotConnectToRemoteServer
    val future = bootstrap.connect(new InetSocketAddress(port))
    val haveConnected = new AtomicBoolean(false)
    future.addListener(
      new ChannelFutureListener(){
        def operationComplete(fut : ChannelFuture){
          haveConnected.set(fut.isSuccess)
        }
      }
    )
    future.awaitUninterruptibly
    if (haveConnected.get){
      Log.info("LOCAL Have connected, future = " + future)
      future
    } else{
      Log.info("LOCAL Connection failed - will retry")
      Log.info("LOCAL Connection failed - sleeping for " + retryTime)
      Thread.sleep(retryTime)
      openConnection(numTries + 1)
    }
  }


  def close{
    channelFuture.get.foreach{
      cf ⇒ 
        cf.awaitUninterruptibly
      if (! cf.isSuccess){
        cf.getCause.printStackTrace
        System.exit(-1)
      }
      cf.getChannel.getCloseFuture.awaitUninterruptibly
    }
    channelFactory.releaseExternalResources
  }

}

object LocalTaskManager extends App{
  new LocalTaskManager
}
