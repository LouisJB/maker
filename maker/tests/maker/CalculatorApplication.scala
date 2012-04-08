/**
 *  Copyright (C) 2009-2012 Typesafe Inc. <http://www.typesafe.com>
 */
package maker

/*
 * comments like //#<tag> are there for inclusion into docs, please don’t remove
 */

 import akka.actor.{ Props => AkkaProps, Actor, ActorSystem }
import com.typesafe.config.ConfigFactory
import akka.kernel.Bootable


//#actor
class SimpleCalculatorActor extends Actor {
  println("SimpleCalculatorActor CREATED")
  def receive = {
    case Add(n1, n2) ⇒
      println("Calculating %d + %d".format(n1, n2))
      sender ! AddResult(n1, n2, n1 + n2)
    case Subtract(n1, n2) ⇒
      println("Calculating %d - %d".format(n1, n2))
      sender ! SubtractResult(n1, n2, n1 - n2)
  }
}
//#actor

class CalculatorApplication extends Bootable {
  //#setup
  val system = ActorSystem("CalculatorApplication", ConfigFactory.load.getConfig("calculator"))
  //val actor = system.actorOf(AkkaProps[SimpleCalculatorActor], "simpleCalculator")
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
