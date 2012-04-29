package maker.task

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.PoisonPill
import akka.actor.{Props ⇒ AkkaProps}
import akka.dispatch.Await
import akka.kernel.Bootable
import akka.pattern.ask
import akka.util.Duration
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import maker.os.Command
import akka.routing.SmallestMailboxRouter
import java.lang.management.ManagementFactory

object ProcessID{
  def apply() : ProcessID = {
    val List(port, host) = ManagementFactory.getRuntimeMXBean().getName().split("@").toList 
    ProcessID(host, port.toInt)
  }
}

case class ProcessID(hostname : String, id : Int){
  def kill = Command("kill", "-9", id.toString).exec
}

object RemoteApplication extends App{
  new RemoteApplication()
}
  
class RemoteApplication extends Bootable {
  val system = ActorSystem("RemoteApplication", ConfigFactory.load.getConfig("calculator"))

  def startup() {
  }

  def shutdown() {
    system.shutdown()
  }
}


class MyTaskManager extends Actor {
  def nWorkers = (Runtime.getRuntime.availableProcessors / 2) max 1
  val router = context.system.actorOf(AkkaProps[Worker].withRouter(SmallestMailboxRouter(nWorkers)))
  def receive = {
    case RemoteTaskManager.GetRemoteProcessID ⇒ 
      sender ! ProcessID()
    case m : ExecTaskMessage ⇒
      router.forward(m)
  }
}

object RemoteTaskManager{
  case object Initialise
  case object Shutdown
  case object GetRemoteProcessID
}

class RTM_Actor(remoteActor : ActorRef) extends Actor {
  def receive = {
    case m ⇒ {
      remoteActor.forward(m)
    }
  }
}

class RemoteTaskManager(classpath : String) {
  import RemoteTaskManager._
  var system : ActorSystem = null
  var remoteActor : ActorRef = null
  var actor : ActorRef = null
  val args = List(
    "/usr/local/jdk/bin/java",
    "-Dscala.usejavacp=true",
    "-classpath",
   classpath,
    "scala.tools.nsc.MainGenericRunner",
    "maker.task.RemoteApplication")
    

  val cmd = Command(args: _*)
  println(cmd)
//val cmd = Command("/usr/local/scala/bin/scala", "maker.task.RemoteApplication")
  var remoteProcessID : ProcessID = null
  var proc : Process = null


  def askRemoteForProcessID : ProcessID = {
    implicit val timeout = Timeout(10000)
    val future = remoteActor ? GetRemoteProcessID
    val result = Await.result(future, Duration.Inf)
    result.asInstanceOf[ProcessID]
  }

  def shutdown{
    Option(system).foreach(_.shutdown)
    system = null
    remoteActor = null
    actor = null
    Thread.sleep(1000)
    Option(remoteProcessID).foreach(_.kill)
    remoteProcessID = null
  }

  def initialise{
    proc = cmd.execAsync
    Thread.sleep(3000)
    system = ActorSystem("RemoteCreation", ConfigFactory.load.getConfig("remotecreation"))
    remoteActor = system.actorOf(AkkaProps[MyTaskManager], "taskManager")
    actor = system.actorOf(AkkaProps(new RTM_Actor(remoteActor)), "localActor")
    remoteProcessID = askRemoteForProcessID
  }
}
