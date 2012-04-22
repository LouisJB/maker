/**
 *  Copyright (C) 2009-2012 Typesafe Inc. <http://www.typesafe.com>
 */
package maker.remoteakka.creation

/*
 * comments like //#<tag> are there for inclusion into docs, please don’t remove
 */

import akka.kernel.Bootable
import com.typesafe.config.ConfigFactory
import scala.util.Random
import akka.actor._
import maker.os.Command
import akka.remote.RemoteClientStarted
import akka.remote.RemoteClientError
import maker.remoteakka.ProcessID


class RemoteAkkaManager{
  var remoteActor : ActorRef = null
}

class ListeningActor extends Actor{
  def receive = {
    case a : AnyRef ⇒ println("Listener received " + a + ", " + a.getClass)
  }
}


class RemoteApplication extends Bootable {
  val system = ActorSystem("RemoteApplication", ConfigFactory.load.getConfig("calculator"))

  def startup() {
  }

  def shutdown() {
    system.shutdown()
  }
}

object RemoteApplication extends App{
  new RemoteApplication
  println("Started Remote Application - waiting for messages")
}

case object ShutdownRemoteSystem
import akka.pattern.ask
import akka.actor.Actor._
import akka.util.Timeout
import akka.dispatch.Await
import akka.util.Duration

class CreationApplication extends Bootable {
  var system : ActorSystem = null
  var localActor : ActorRef = null

  def launchSystem{
    system = ActorSystem("RemoteCreation", ConfigFactory.load.getConfig("remotecreation"))
    Thread.sleep(500)
    println("Creating local actor")
    localActor = system.actorOf(Props(new CreationActor(system)), "creationActor")
  }
  def shutdownSystem {
    implicit val timeout = Timeout(10000)
    val future = localActor ? ShutdownRemoteSystem
    val result = Await.result(future, Duration.Inf)
    system.shutdown
  }

  def startup() {}

  def shutdown() {
    Option(system).foreach(_.shutdown())
  }
}

class RemoteActor extends Actor {
  println("new remote created")
  def receive = {
    case id : ProcessID ⇒
      println("remote actor Received " + id)
      sender ! ProcessID()
  }
}
case object MakeNewRemoteActor
//#actor
class CreationActor(system : ActorSystem) extends Actor {
  val cmd = Command("/usr/local/scala/bin/scala", "maker.remoteakka.creation.RemoteApplication")
  var proc : Process = null
  var remoteActor : ActorRef = null
  var remoteProcessID : ProcessID = null
  private def createRemoteActor{
    Option(remoteActor).foreach(_ ! PoisonPill)
    Option(proc).foreach(_.destroy)
    Thread.sleep(1000)
    println("Launching new remote actor")
    proc = cmd.execAsync
    Thread.sleep(1000)
    remoteActor = system.actorOf(Props[RemoteActor], "remoteActor")
  }
  
  createRemoteActor
  def receive = {
    case id : ProcessID ⇒ {
      remoteProcessID = id
      println("Creation actor Received " + id + " from remote actor, this actor's id is " + ProcessID())
    }
    case  (msg : String, op: AnyRef) ⇒ 
      println("Local Received " + op)
      remoteActor ! op
    case ShutdownRemoteSystem ⇒ {
      println("Been told to shut down")
      Option(remoteActor).foreach(_ ! PoisonPill)
      println("Killing proc")
      Option(proc).foreach(_.destroy)
      Thread.sleep(1000)
      println("Killing remote processes")
      Option(remoteProcessID).foreach(_.kill)
      println("Finished shutting down")
      sender ! "Done"
    }
  }
}
//#actor

object CreationApplication {
  def main(args: Array[String]) {
    val app = new CreationApplication
    app.launchSystem
    println("Started Creation Application")
    while (true) {
      Thread.sleep(1500)
      app.localActor ! ("foo", ProcessID())
      Thread.sleep(1500)
      app.shutdownSystem
      Thread.sleep(1500)
      println("Launching system ahain")
      app.launchSystem
    }
  }
}
