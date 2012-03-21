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

object RemoteApplication extends Application {
  new RemoteApplication
  println("Started Remote Application - waiting for messages")
}

class CreationApplication extends Bootable {
  val system = ActorSystem("RemoteCreation", ConfigFactory.load.getConfig("remotecreation"))
  Thread.sleep(500)
  println("Creating local actor")
  val localActor = system.actorOf(Props(new CreationActor(system)), "creationActor")

  def startup() {}

  def shutdown() {
    system.shutdown()
  }
}

class RemoteActor extends Actor {
  println("new Advanced calc created")
  def receive = {
    case ProcessID(id) ⇒
      println("new Advanced calc actor Received " + id)
      sender ! ProcessID()
  }
}
//#actor
class CreationActor(system : ActorSystem) extends Actor {
  val cmd = Command("/usr/local/scala/bin/scala", "maker.remoteakka.creation.RemoteApplication")
  var proc : Process = null
  var remoteActor : ActorRef = null
  private def createRemoteActor{
    Option(proc).foreach(_.destroy)
    proc = cmd.execAsync
    Thread.sleep(1000)
    remoteActor = system.actorOf(Props[RemoteActor], "remoteActor")
  }
  
  createRemoteActor
  def receive = {
    case ProcessID(id) ⇒ println("Creation actor Received " + id + " from remote actor, this actor's id is " + ProcessID())
    case  (msg : String, op: AnyRef) ⇒ 
      println("Local Received " + op)
      remoteActor ! op
  }
}
//#actor

object CreationApp {
  def main(args: Array[String]) {
    val app = new CreationApplication
    println("Started Creation Application")
    while (true) {
      app.localActor ! ("foo", ProcessID())
      //app.doSomething(ProcessID())
      Thread.sleep(1500)
    }
  }
}
