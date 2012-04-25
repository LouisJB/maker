package maker.task
import akka.actor.ActorRef
import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import akka.actor.{Props ⇒ AkkaProps}
import maker.os.Command
import maker.remoteakka.creation.RemoteActor
import akka.kernel.Bootable
import akka.util.Timeout
import akka.pattern.ask
import maker.remoteakka.ProcessID
import akka.dispatch.Await
import akka.util.Duration


class RemoteApplication extends Bootable {
  val system = ActorSystem("RemoteApplication", ConfigFactory.load.getConfig("calculator"))

  def startup() {
  }

  def shutdown() {
    system.shutdown()
  }
}

class RemoteTaskRunner(){
  var system : ActorSystem = null
  var remoteActor : ActorRef = null
  val cmd = Command("/usr/local/scala/bin/scala", "maker.remoteakka.creation.RemoteApplication")
  var remoteProcessID : ProcessID = null

  def askRemoteForProcessID : ProcessID = {
    implicit val timeout = Timeout(10000)
    val future = remoteActor ? ProcessID()
    val result = Await.result(future, Duration.Inf)
    result.asInstanceOf[ProcessID]
  }

  def shutdown{
    Option(system).foreach(_.shutdown)
    Option(remoteProcessID).foreach(_.kill)
    Thread.sleep(1000)
  }

  def initialise{
    println("Launching application")
    cmd.execAsync
    Thread.sleep(1000)
    println("Creating local system")
    system = ActorSystem("RemoteCreation", ConfigFactory.load.getConfig("remotecreation"))
    Thread.sleep(1000)
    remoteActor = system.actorOf(AkkaProps[RemoteActor], "remoteActor")
    remoteProcessID = askRemoteForProcessID
  }
}
