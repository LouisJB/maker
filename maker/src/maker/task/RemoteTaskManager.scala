package maker.task

import java.lang.management.ManagementFactory
import java.net.{ConnectException, InetSocketAddress}
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import maker.Maker
import maker.utils.Log
import maker.utils.os.{ProcessID, ScalaCommand}
import org.jboss.netty.bootstrap.{ClientBootstrap, ServerBootstrap}
import org.jboss.netty.channel._
import org.jboss.netty.channel.group.DefaultChannelGroup
import org.jboss.netty.channel.socket.nio.{NioClientSocketChannelFactory, NioServerSocketChannelFactory}
import org.jboss.netty.handler.codec.serialization.{ClassResolvers, ObjectDecoder, ObjectEncoder}


case object TellMeYourProcessID
case object Shutdown

class RemoteTaskManagerHandler extends SimpleChannelUpstreamHandler{
  override def messageReceived(ctx : ChannelHandlerContext, e : MessageEvent){
    Log.info("REMOTE received " + e.getMessage)
    val channel = e.getChannel
    e.getMessage match {
      case (id, TellMeYourProcessID) ⇒
        channel.write((id, ProcessID()))
      case Shutdown ⇒ 
        System.exit(0)
      case _ ⇒
        Log.info("REMOTE got unexpected message " + e.getMessage)
    }
  }
}

object RemoteTaskManager extends App{

  import TaskManagement._

  Log.info("REMOTE - starting task manager")

    val factory = makeServerChannelFactory
    val bootstrap = new ServerBootstrap(factory)
    bootstrap.setPipelineFactory(
      new ChannelPipelineFactory(){
        def getPipeline : ChannelPipeline = {
          Channels.pipeline(
            encoder("REMOTE"), 
            objectDecoder("REMOTE"),
            new RemoteTaskManagerHandler()
          )
        }
      }
    )
    val allChannels = new DefaultChannelGroup("Spike Server")
    val address = new InetSocketAddress(Maker.props.RemoteTaskPort())
    val channel : Channel = bootstrap.bind(address)
    Log.info("REMOTE bound to address " + address + " on channel " + channel)
    allChannels.add(channel)

    def close{
      allChannels.close.awaitUninterruptibly
      factory.releaseExternalResources
    }
}


