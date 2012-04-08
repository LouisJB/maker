package maker

import org.scalatest.FunSuite
import akka.actor.ActorSystem
import akka.actor.{Props => AkkaProps}
import akka.actor.Actor

case class Reply(msg : String)
case class Message(msg : String)

class RemoteActorApplication extends Bootable {
  //#setup
  val system = ActorSystem("RemoteActorApplication", ConfigFactory.load.getConfig("remoteActor"))
  val actor = system.actorOf(Props[RemoteActor], "remoteActor")
  //#setup

  def startup() {
  }

  def shutdown() {
    system.shutdown()
  }
}
class LocalActor extends Actor{
  def receive = {
    case Reply(msg) => println("Received reply " + msg)
  }
}
class RemoteActor extends Actor{
  def receive = {
    case Message(msg) => println("Received message " + msg)
  }
}
class RemoteAkkeTests extends FunSuite{
  val system = ActorSystem("fred")
  val localActor = system.actorOf(AkkaProps[LocalActor], "localActor")
  val remoteActor = system.actorOf(AkkaProps[RemoteActor], "remoteActor")
}
