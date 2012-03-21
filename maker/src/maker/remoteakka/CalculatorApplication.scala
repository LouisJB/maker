/**
 *  Copyright (C) 2009-2012 Typesafe Inc. <http://www.typesafe.com>
 */
package maker.remoteakka

/*
 * comments like //#<tag> are there for inclusion into docs, please don’t remove
 */

import akka.kernel.Bootable
import akka.actor.{ Props, Actor, ActorSystem }
import com.typesafe.config.ConfigFactory
import java.lang.management.ManagementFactory

object ProcessID{
  def apply() : ProcessID = {
    ProcessID(ManagementFactory.getRuntimeMXBean().getName())
  }
}

case class ProcessID(id : String)

//#actor
class LookupCalculatorActor extends Actor {
  def receive = {
    case ProcessID(id) ⇒
      println("Lookup calc actor Received " + id)
      sender ! ProcessID()
  }
}
//#actor

class CalculatorApplication extends Bootable {
  //#setup
  val system = ActorSystem("CalculatorApplication", ConfigFactory.load.getConfig("calculator"))
  //#setup

  def startup() {
  }

  def shutdown() {
    system.shutdown()
  }
}

object CalcApp {
  def main(args: Array[String]) {
    new CalculatorApplication
    println("Started Calculator Application - waiting for messages")
  }
}
