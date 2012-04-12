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

class CreationApplication extends Bootable {
  //#setup
  val system = ActorSystem("RemoteCreation", ConfigFactory.load.getConfig("remotecreation"))
  val localActor = system.actorOf(Props[CreationActor], "creationActor")
  val remoteActor = system.actorOf(Props[AdvancedCalculatorActor], "advancedCalculator")

  def doSomething(op: AnyRef) = {
    localActor ! (remoteActor, op)
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
  def receive = {
    case (actor: ActorRef, op: AnyRef) ⇒ actor ! op
    case ProcessID(id) ⇒ println("Creation actor Received " + id + " from remote actor, this actor's id is " + ProcessID())
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
    Thread.sleep(sleepTime)
    val app = new CreationApplication
    println("Started Creation Application")
    while (true) {
      app.doSomething(ProcessID())
      Thread.sleep(200)
    }
  }
}
