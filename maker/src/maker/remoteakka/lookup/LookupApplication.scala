/**
 *  Copyright (C) 2009-2012 Typesafe Inc. <http://www.typesafe.com>
 */
package maker.remoteakka.lookup

/*
 * comments like //#<tag> are there for inclusion into docs, please don’t remove
 */

import akka.kernel.Bootable
import scala.util.Random
//#imports
import com.typesafe.config.ConfigFactory
import akka.actor.{ ActorRef, Props, Actor, ActorSystem }
import maker.remoteakka._
import maker.remoteakka.ProcessID
import maker.os.Command
//#imports


class LocalLookupApplication extends Bootable {
  //#setup
  val system = ActorSystem("LocalLookupApplication", ConfigFactory.load.getConfig("remotelookup"))
  val actor = system.actorOf(Props[LocalLookupActor], "lookupActor")
  val remoteActor = system.actorFor("akka://CalculatorApplication@127.0.0.1:2552/user/lookupCalculator")

  def doSomething(op: AnyRef) = {
    actor ! (remoteActor, op)
  }
  //#setup

  def startup() {
  }

  def shutdown() {
    system.shutdown()
  }
}

//#actor
class LocalLookupActor extends Actor {
  def receive = {
    case (actor: ActorRef, op: AnyRef) ⇒ actor ! op
    case ProcessID(id) ⇒ println("Received " + id + " from remote actor, this actor's id is " + ProcessID())
  }
}
//#actor

object LookupApp {
  def main(args: Array[String]) {
    val cmd = Command("/usr/local/scala/bin/scala", "maker.remoteakka.CalcApp")
    cmd.exec
    var sleepTime = 500
    if (args.size > 0)
      sleepTime = args(0).toInt
    Thread.sleep(sleepTime)
    val app = new LocalLookupApplication
    println("Started Lookup Application")
    while (true) {
      app.doSomething(ProcessID())

      Thread.sleep(200)
    }
  }
}
