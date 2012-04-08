/**
 *  Copyright (C) 2009-2012 Typesafe Inc. <http://www.typesafe.com>
 */
package maker

/*
 * comments like //#<tag> are there for inclusion into docs, please don’t remove
 */

import akka.kernel.Bootable
import scala.util.Random
import com.typesafe.config.ConfigFactory
import akka.actor.{ActorRef, Props => AkkaProps, Actor, ActorSystem }
import maker.os.Command
import maker.utils.Log

trait MathOp

case class Add(nbr1: Int, nbr2: Int) extends MathOp

case class Subtract(nbr1: Int, nbr2: Int) extends MathOp

case class Multiply(nbr1: Int, nbr2: Int) extends MathOp

case class Divide(nbr1: Double, nbr2: Int) extends MathOp

trait MathResult

case class AddResult(nbr: Int, nbr2: Int, result: Int) extends MathResult

case class SubtractResult(nbr1: Int, nbr2: Int, result: Int) extends MathResult

case class MultiplicationResult(nbr1: Int, nbr2: Int, result: Int) extends MathResult

case class DivisionResult(nbr1: Double, nbr2: Int, result: Double) extends MathResult

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

class LookupApplication extends Bootable {
  val mathProc = Command("/usr/local/scala/bin/scala", "maker.CalcApp")
  mathProc.exec(async = true)
  //#setup
  val system = ActorSystem("LookupApplication", ConfigFactory.load.getConfig("remotelookup"))
  val remoteActor = system.actorOf(AkkaProps[SimpleCalculatorActor], "simpleCalculator")
  val actor = system.actorOf(AkkaProps(new LookupActor(remoteActor)), "lookupActor")
  def doSomething(op: MathOp) = {
    actor ! op
  }
  //#setup

  def startup() {
  }

  def shutdown() {
    system.shutdown()
  }
}

//#actor
class LookupActor(remoteActor : ActorRef) extends Actor {
  def receive = {
    case op: MathOp ⇒ {
      remoteActor ! op
    }

    case result: MathResult ⇒ result match {
      case AddResult(n1, n2, r)      ⇒ println("lookup received Add result: %d + %d = %d".format(n1, n2, r))
      case SubtractResult(n1, n2, r) ⇒ println("lookup received Sub result: %d - %d = %d".format(n1, n2, r))
    }
  }
}
//#actor

object LookupApplication {
  def main(args: Array[String]) {
    val app = new LookupApplication
    var i = 0
    while (true) {
      if (Random.nextInt(100) % 2 == 0) app.doSomething(Add(i, Random.nextInt(100)))
      else app.doSomething(Subtract(i, Random.nextInt(100)))

      i += 1
      Thread.sleep(200)
    }
  }
}
