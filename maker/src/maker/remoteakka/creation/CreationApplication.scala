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
import maker.remoteakka._
import maker.os.Command
import akka.remote.RemoteClientStarted


class ListeningActor extends Actor{
  def receive = {
    case a : AnyRef ⇒ println("Listener received " + a + ", " + a.getClass)
  }
}

class CreationApplication extends Bootable {
  //#setup
  println("Local process id = " + ProcessID())
  val system = ActorSystem("RemoteCreation", ConfigFactory.load.getConfig("remotecreation"))
  println("Creating local actor")
  val localActor = system.actorOf(Props[CreationActor], "creationActor")
  system.eventStream.subscribe(localActor, classOf[AnyRef])
  println("\n\n\n")
  Thread.sleep(500)
  println("Creating remote actor")
  val remoteActor = system.actorOf(Props[AdvancedCalculatorActor], "advancedCalculator")
  println("\n\n\n")
Thread.sleep(500)
  localActor ! remoteActor

  def doSomething(op: AnyRef) = {
    localActor ! ("foo", op)
  }
  //#setup

  def startup() {
  }

  def shutdown() {
    system.shutdown()
  }
}

//#actor
class CreationActor extends Actor {
  var remoteActor : Option[ActorRef] = None
  var buffer = List[AnyRef]()
  var isRemoteActorReady = false
  def clearBuffer{
    (remoteActor, isRemoteActorReady) match{
      case (Some(a), true) ⇒ 
        buffer.foreach(remoteActor.get ! _)
        buffer = Nil
      case _ ⇒
    }
  }
  def receive = {
    case ProcessID(id) ⇒ println("Creation actor Received " + id + " from remote actor, this actor's id is " + ProcessID())
    case _ : RemoteClientStarted ⇒ 
      isRemoteActorReady = true
      clearBuffer
    case actor : ActorRef ⇒
      remoteActor = Some(actor)
      clearBuffer
    case  (msg : String, op: AnyRef) ⇒ 
      println("Received " + op + ", " + isRemoteActorReady)
      buffer = op :: buffer
      clearBuffer
    case a : AnyRef ⇒
      println("RECD " + a)
  }
}
//#actor

object CreationApp {
  def main(args: Array[String]) {
    val cmd = Command("/usr/local/scala/bin/scala", "maker.remoteakka.CalcApp")
    cmd.exec(async=true)
    var sleepTime = 500
    if (args.size > 0)
      sleepTime = args(0).toInt
    //Thread.sleep(sleepTime)
    val app = new CreationApplication
    println("Started Creation Application")
    while (true) {
      app.doSomething(ProcessID())
      Thread.sleep(1500)
    }
  }
}
