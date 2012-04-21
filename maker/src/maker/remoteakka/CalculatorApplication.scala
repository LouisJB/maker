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
import maker.os.Command

object ProcessID{
  def apply() : ProcessID = {
    val List(port, host) = ManagementFactory.getRuntimeMXBean().getName().split("@").toList 
    ProcessID(host, port.toInt)
  }
}

case class ProcessID(hostname : String, id : Int){
  def kill = Command("kill", "-9", id.toString).exec
}

//#actor
class LookupCalculatorActor extends Actor {
  def receive = {
    case id : ProcessID ⇒
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
